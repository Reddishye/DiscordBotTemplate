# Menu System Inventory

Reference document for the menu framework. Sections 1 and 2 describe what the
template provides and what JDA actually does; section 3 records what was ported
from the source menu tree and what was deliberately not; section 3.6 is the
declarative DSL. Every JDA statement was verified against the sources jar of the
exact JDA version this template depends on, and every framework claim against the
code as it stands. The reasoning behind the choices is in
`docs/design-decisions.md`, and what is still open is in `NOTES.md`.

## 1. Target template

### 1.1 Coordinates

| Item | Value |
| --- | --- |
| Repository | `/home/redactado/JavaProjects/DiscordBotTemplate` |
| Base package | `es.redactado` |
| Group / version | `es.redactado` / `1.0-SNAPSHOT` |
| Source roots | `src/main/java`, `src/main/resources` |
| Test roots | `src/test/java` |

The menu framework therefore lives under `es.redactado.menu`.

### 1.2 Language and build

| Item | Value |
| --- | --- |
| Build tool | Gradle 9.8.0 (Kotlin DSL, `build.gradle.kts`) |
| Java version | **27**, pinned by `java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }`. Nothing in the menu package uses a feature newer than 21; the baseline moved because the project needs 27 |
| Formatter | Spotless 7.2.1, google-java-format 1.26.0, AOSP style, `reflowLongStrings`, `skipJavadocFormatting`, `formatAnnotations`, `removeUnusedImports` |
| Packaging | Shadow 9.2.2 (`shadowJar`), Sentry 5.12.1, `application` plugin with main class `es.redactado.Main` |

A formatter is already configured, so Spotless stays as-is. Java 27 is the
baseline; the features the menu package actually uses, records, sealed
hierarchies, pattern-matching `switch` and virtual threads, all arrived by 21.

### 1.3 Dependencies

Present in `build.gradle.kts`:

- JDA `6.4.2` (`opus-java` excluded)
- discord-webhooks `0.8.4`
- Guice `7.0.0`
- dotenv-java `3.2.0`
- logback-classic `1.5.18`, slf4j-api `2.0.17`, jansi `2.4.2`
- tess4j `5.16.0`
- Hibernate ORM `7.1.5.Final` (core, hikaricp, jcache, community-dialects)
- jakarta.transaction-api
- HikariCP `7.0.2`, MariaDB `3.5.7`, SQLite `3.50.3.0`, H2 `2.3.232`
- jackson-databind `2.19.1`

Test-only: JUnit Jupiter `5.11.4`, Mockito `5.14.2`, AssertJ `3.26.3`,
`junit-platform-launcher`, and `net.bytebuddy:byte-buddy-agent:1.17.6` in a
dedicated `mockitoAgent` configuration used as a `-javaagent`.

Findings that affect the port:

- **Caffeine was undeclared in the working tree.** The working tree removed
  `com.github.ben-manes.caffeine:caffeine:v3.2.2` and `caffeine:jcache:v3.2.2`
  from `build.gradle.kts`, yet `src/main/java/es/redactado/command/handler/CommandRegister.java`
  still imports `com.github.benmanes.caffeine.cache.Cache` and
  `com.github.benmanes.caffeine.cache.Caffeine`. `./gradlew compileJava`
  therefore failed with 8 errors before any menu work began. Both lines were
  already in `HEAD`, so the file was restored to its committed content. The menu
  runtime also needs Caffeine for sessions, cooldown, and async loading, which
  section 2.3 permits.
- **Test infrastructure was added.** There was no `src/test` tree, no test
  dependency, and no `useJUnitPlatform()`. See section 1.8.
- **Serialization.** Jackson Databind `2.19.1` is resolved in `runtimeClasspath`
  through JDA's own dependency, but it is *not* on `compileClasspath`, so menu
  code cannot compile against it without a declaration. It is declared explicitly
  at the already-resolved version rather than introducing a new library. Preset
  files are JSON only; no YAML module is added.

### 1.4 Dependency injection

Guice 7.0.0, constructor injection with `@Inject`, `@Singleton` from Guice, and
a single `BotModule extends AbstractModule` created in `Main.run()`.

`BotModule.configure()` currently binds `Main`, `ShardManager`, an eager
`DatabaseManager` singleton, and every class listed in `Database.REPOSITORIES`.
It also exposes `@Provides @Singleton Dotenv`.

The menu framework follows the same pattern and adds two bindings:
`MenuService`, a Guice `@Singleton` owning the router, and `MenuListener`, which
`Listeners.LISTENERS` asks the injector for. `TaskManager` is a `@Singleton` as
well, because a second one would own a second set of pools.

### 1.4b Where the menu package gets its executors

The menu package **must not import `es.redactado.service`**. It receives
`java.util.concurrent.Executor` instances and nothing else, so it can be driven by
the template's own pools, by a test's deterministic executor, or by
`MenuExecutor.virtual()` with no wiring at all.

The connection is made in the wiring task (W), outside the package:

| Menu side | Host side | Ownership |
| --- | --- | --- |
| `MenuExecutor.shared(Executor io)` | `TaskManager.ioExecutor()` | borrowed; `close()` leaves it running |
| `MenuExecutor.virtual()` | nothing | owned; `close()` shuts it down |
| `new DataCache(config, loader, executor)` | `TaskManager.cpuExecutor()` | borrowed; the loader runs there |
| `SessionStore.cleanUp()` | `TaskManager.scheduleAtFixedRate(...)` | the store owns no thread; draining is scheduled |

`TaskManager` exposes `ioExecutor()` and `cpuExecutor()` and returns them as bare
`Executor`, not `ExecutorService`, so a caller cannot shut down a pool it does not
own. Both throw `IllegalStateException` when the manager is not running, which is
the same contract as the manager's other methods: an accessor has no future to
fail, so it fails at the call.

Verified in the Caffeine sources: `Caffeine.executor(null)` throws
`NullPointerException`, so an absent executor has to leave the builder
unconfigured rather than be passed through. Unconfigured means
`ForkJoinPool.commonPool()`, which is the behaviour this class had before the
parameter existed. Measured, not assumed: an unconfigured `DataCache` really does
load on a `ForkJoinPool.commonPool-worker-N` thread.

**A session store deliberately has no executor parameter.** Caffeine only delegates
to one for removal notifications, `AsyncCache` computations, `refresh` and periodic
maintenance, and a session store configures none of them, so the parameter could
never be used. It was added by mistake and removed in commit `drop the unused
session maintenance executor`. What the store needs is an occasional `cleanUp()`,
which the host schedules on a timer; `SessionStoreTest.respectsMaximumSize` calls it
before asserting, because `estimatedSize()` is approximate and eviction is lazy.

### 1.5 Event listeners

Listeners are discovered from the static list `es.redactado.config.Listeners.LISTENERS`.
`Main.instantiateListeners()` asks the injector for each entry and registers the
result with `ShardManager.addEventListener`. Slash commands that also extend
`ListenerAdapter` are appended from `CommandRegister.getListeners()`.

The command entry is `CommandListener`, which shows the template's
interaction convention: take the event on the JDA thread, hand the work to
`Executors.newVirtualThreadPerTaskExecutor()`, acknowledge before doing work, and
route failures to a single ephemeral error reply plus `Sentry.captureException`.

The menu listener is registered the same way and already is:
`MenuListener extends ListenerAdapter` in this same package, listed in
`Listeners.LISTENERS`, delegating to the router the `MenuService` owns. See the
Wiring section.

### 1.6 Lifecycle and shutdown

`Main.run()` registers `Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown))`.
`Main.shutdown()` calls `serviceManager.stopAll()` and then
`shardManager.shutdown()`.

Service lifecycle is handled by `IService` (`init()` / `shutdown()` /
`dependsOn()`), orchestrated by `ServiceManager.startAll(List<Class<? extends IService>>)`
and `ServiceManager.stopAll()`. Services are declared in
`es.redactado.config.Services` as two lists:

- `INFRASTRUCTURE_SERVICES`, started before JDA connects
- `BUSINESS_SERVICES`, started after the first `ReadyEvent`

The menu runtime needs to close its executor, its session store and its preset
watcher, and it needs to be reachable from a command. It is therefore
`es.redactado.service.MenuService`, an `IService` registered in
`INFRASTRUCTURE_SERVICES` after `TaskManager`: it starts before JDA connects,
builds the `MenuRouter` with the executors `TaskManager` owns, schedules the
session store's drain, and closes in `shutdown()`. `Main.shutdown()` already
delegates to `ServiceManager.stopAll()`, so the existing shutdown hook closes it in
reverse initialisation order without touching `Main`. It touches neither
`ShardManager` nor the Discord API, which is what an infrastructure service is
required not to do.

### 1.6b Package dependency direction

The menu packages depend on each other in one direction only, which the prep
commit established:

```
preset ->  (no other menu package; JDA and the JDK only)
api    ->  (no other menu package)
core   ->  api
view   ->  api, core
```

`preset` is a leaf: it holds data about how a menu looks and deliberately imports
nothing from the rest of the framework, so a preset can be read, tested, and
serialised on its own. `api`, `core`, and `view` all import it, and nothing goes the
other way.

The chain at run time is: `PresetResolver` picks a preset per interaction,
`MenuRouter` puts it on the `MenuContext` as `ctx.preset()`, and every component reads
that one value. `Looks` is the only class that converts a preset into a JDA value, so a
mapping is changed in one place rather than in every component.

## Wiring

How the framework is attached to this template. The framework itself is unchanged by
any of it: it takes an `Executor` and knows nothing about the bot.

### Services

| Service | Declared in | Depends on | What it owns |
| --- | --- | --- | --- |
| `TaskManager` | `Services.INFRASTRUCTURE_SERVICES` | nothing | the pools |
| `MenuService` | `Services.INFRASTRUCTURE_SERVICES`, after `TaskManager` | `TaskManager` | router, sessions, presets, preferences, the preset watcher, the drain schedule |

Both are started before JDA connects, so neither may touch `ShardManager`, and neither
does.

**Both are `@Singleton`, and that is load-bearing.** `ServiceManager` resolves each
service class with `injector.getInstance` and inits whatever it gets back. Without a
scope, a class injecting `TaskManager` would receive a *second*, never-started copy
whose executors do not exist, and `MenuService.init` would throw on the first call.
`MenuServiceTest.menuServiceSeesTheStartedTaskManager` proves it by building the
injector the way the template does and reaching for a pool through the service.

### Listener

`MenuListener` lives in `es.redactado.command.handler`, beside `CommandListener`,
because that is where the template keeps its `ListenerAdapter` implementations and
where `Listeners.LISTENERS` expects them from. It is registered in
`Listeners.LISTENERS` and injects `MenuService`.

It is three overrides and nothing else: `onButtonInteraction`, `onModalInteraction`
and `onStringSelectInteraction`, each delegating to the matching
`MenuRouter.dispatch*`. It does not block and does not catch; an interaction the menu
system does not own is left untouched for another listener.

### Placement, and why

| Code | Package | Reason |
| --- | --- | --- |
| framework | `es.redactado.menu.*` | no dependency on the template at all |
| `MenuService`, `MenuSettings` | `es.redactado.service` | beside `IService` and `TaskManager`, the two things they are |
| `MenuListener` | `es.redactado.command.handler` | beside `CommandListener`, which the listener list and the injector both already cover |

`MenuDependencyTest` enforces the boundary: nothing under `menu` imports
`es.redactado.*` at all, and outside it the only importers of `es.redactado.service`
are `MenuListener` plus the two template files that already did so.

### Configuration

Read through `Dotenv`, the mechanism the template already uses, so there is one way
to configure a bot rather than two.

| Setting | Default | Meaning |
| --- | --- | --- |
| `MENU_PRESETS_DIR` | `presets` | directory read for `*.json` preset files; a missing directory is not an error |
| `MENU_SESSION_MAX_SIZE` | `50000` | how many menu messages are remembered at once |
| `MENU_SESSION_IDLE_TTL` | `30m` | how long an untouched message keeps its history; accepts `ms`, `s`, `m`, `h`, or a bare number of minutes |
| `MENU_USER_PRESETS_ENABLED` | `false` | whether a user's own choice may override their guild's |
| `MENU_DEFAULT_PRESET` | `default` | which preset is used when nothing else says otherwise |

A value that is present but unusable fails at startup with a message naming the
setting, because a mistyped number in a `.env` file is otherwise invisible until a
menu misbehaves hours later.

### Shutdown order

`ServiceManager.stopAll` stops in reverse init order, so:

1. `MenuService.shutdown` cancels the drain schedule, closes the `PresetStore`, closes
   the router, closes the `SessionStore`;
2. `TaskManager.shutdown` then stops the pools the router was borrowing.

The executor is never closed by the menu side: `MenuExecutor.shared` borrows
`TaskManager.ioExecutor()`, and the service manager stops its owner in its own time.
`shutdown` is idempotent, and it tolerates a task manager that has already stopped.

### Opening a menu

`MenuRouter.open(IReplyCallback, menuId, ephemeral)` answers any interaction that can
be acknowledged: a slash command, a context menu, a component or modal of another
system. An unknown id fails before anything is acknowledged, so the caller can still
answer with a plain reply. Nothing else is provided and **no commands ship**: where a
menu is opened from is a decision for the bot.

## Custom preset files

One preset per file, `<dir>/<name>.json`, not recursive. The file name is part of the
contract: `name` must equal it without the extension, so a file's location and its
contents cannot disagree.

`docs/presets/ocean.json` is the worked example and is loaded by
`PresetLoaderTest`, so this section and the format cannot drift apart.

```json
{
  "name": "ocean",
  "extends": "midnight",
  "description": "Cool blue theme for support menus.",
  "palette": { "accent": "#1E90FF", "success": "#2ECC71" },
  "icons": { "ok": "\uD83D\uDE80", "delete": "" },
  "density": "comfortable",
  "divider": { "visible": true, "gap": "large" },
  "header": { "level": 2, "subtitle": true },
  "buttons": { "primary": "secondary" },
  "footer": "{menu}"
}
```

Only `name` is required. Anything omitted is inherited from `extends`, or from the
`default` preset when there is no `extends`. Objects merge field by field, so a
partial `palette` changes only the colours it lists and a partial `header` changes
only the keys it has.

| Key | Values |
| --- | --- |
| `extends` | a built-in or custom preset name |
| `description` | up to 120 characters |
| `palette` | `accent`, `success`, `warning`, `danger`, `info`, `neutral`, each `#RRGGBB` |
| `icons` | lowercase `IconKey` names to a Unicode emoji or `<:name:id>`, `""` to remove |
| `density` | `compact`, `normal`, `comfortable` |
| `divider` | `visible`, `gap` of `small` or `large` |
| `header` | `level` from 1 to 3, `subtitle` |
| `buttons` | role to style, all four of `primary`, `secondary`, `success`, `danger` |
| `footer` | up to 200 characters, `{user}` and `{menu}` only |

Words are matched case-insensitively. Unknown properties are errors at every level,
because a misspelled key that is silently ignored is a change that appears to have
been made and was not. Inheritance is resolved once, at load time: a loaded `Preset`
has no parent and nothing to walk.

A file over 64 KiB is rejected, and at most 200 files are read per pass, so one
directory cannot make a reload unbounded. Neither limit is a real constraint for a
hand-written preset, and both bound the cost of a mistake.

Errors name the file and the field path rather than throwing, so one bad file costs
its own preset and nothing else:

```
ocean.json: palette.accent: expected #RRGGBB, got 'blue'
```

`PresetStore` keeps the last version that loaded, so editing a file into an invalid
state costs that preset's new values rather than the preset itself. Only a file that
stops existing is dropped. Reloading can be manual, or automatic: `startWatching()`
watches the directory on one daemon thread and reloads once writes have been quiet
for 300 ms, so a burst of saves is one reload rather than twenty.

## Adding a locale or a key

User-facing text lives in `src/main/resources/menu/messages.properties` (English, the
default) and `messages_<tag>.properties` for every other locale. Code never spells a
sentence: it names a constant from `core/MessageKeys` and the text is resolved against
the interaction's locale.

To change an existing message, edit the value in each bundle.

To add a new message:

1. Add the key to `messages.properties`, with `{0}` style positional placeholders.
2. Add the same key to every other bundle. `MessageKeysTest.parity` fails the build
   otherwise, and so does a mismatch in the `{n}` placeholders for one key.
3. Add a `public static final String` to `MessageKeys`, grouped under the right prefix.
   The same test fails if a constant has no value, if a bundle key has no constant, and
   if a constant is never referenced by any other main source.
4. Use it: `ctx.t(MessageKeys.X)` in a component, or
   `Replies.ephemeral(event, messages, locale, MessageKeys.X)` from the router.

To add a language:

1. Create `src/main/resources/menu/messages_<tag>.properties`, copying every key from
   the English bundle. Do not translate it yet if you do not have a translator; an
   English copy passes parity and is honest about needing review.
2. Nothing else. `Messages` resolves the exact tag, then the bare language, then
   English, and caches each language once.

`Locales` picks the user's Discord locale when they have one and the guild's otherwise.
`DiscordLocale.UNKNOWN`, which Discord reports for most new accounts, falls through to
English.

`EmojiText` and `DefaultLook` are package-private inside `preset`. `EmojiText`
exists because JDA does not validate emoji, and `DefaultLook` exists to break a
class-initialisation cycle between `Preset` and `BuiltinPresets`.

`Session`, `NavEntry`, and `NavigationMode` sit in `api` rather than `core`
because `MenuContext` exposes all three. Moving them keeps the invariant above
intact instead of making `api` depend on the dispatcher.

`core` also holds `MenuExecutor`, which wraps an `ExecutorService`, and
`InteractionGuard`, a `ConcurrentHashMap.newKeySet()` of message ids currently
being handled. Both were added in T4.

Two cycles had to be broken. `Limits` moved from `view` to `api`, because
`core.ComponentId` needs `MAX_CUSTOM_ID_LENGTH` and `view` already needs
`core.ComponentId`. `api.MenuComponent` stopped importing `view.Row` for a
Javadoc link. Removing the dead `AbstractMenu.renderValidated` is what actually
cleared `core` -> `view`, since it was the only user of `view.Validator` there.

### 1.7 File tree

```
src/main/java/es/redactado/
  BotModule.java                    Guice module
  LogbackOutputStream.java
  Main.java                         entry point, phases, shutdown hook
  command/                          PingCommand, handlers, base types
  config/                           Bot, Commands, Database, Listeners, Services
  database/                         DatabaseManager, model, repository
  exception/service/                DependencyResolutionException
  service/                          IService, ServiceManager, TaskManager
src/main/resources/
  .env.example
  logback.xml
src/test/java/es/redactado/
  TestStackTest.java
  menu/                           the scan tests, the end-to-end tests, the
                                  concurrency stress class
src/main/java/es/redactado/menu/
  api/                            Menu, MenuContext, MenuComponent, Ack,
                                   ActionTable, ButtonAction, ModalAction,
                                   ButtonHandler, ModalHandler, Done, Limits,
                                   UserFacingException, NavEntry, Session,
                                   NavigationMode, Validator,
                                   ValidationResult, exceptions
  core/                           MenuRouter, MenuExecutor, InteractionGuard,
                                   SessionStore, SessionConfig, Navigator,
                                   ComponentId, Replies, ErrorReply,
                                   NavigationAction, AbstractMenu, BaseContext
  preset/                         Preset, Icons, Palette, Density, Gap,
                                   DividerStyle, HeaderStyle, ButtonRole,
                                   ButtonStyles, BuiltinPresets,
                                   PresetRegistry, IconKey,
                                   PresetLoader, PresetStore, LoadResult,
                                   LoadProblem, PresetPreferences,
                                   InMemoryPresetPreferences
  view/                           MenuBuilder, components, Validator
  RowItem.java                   Action-row children: buttons and selects
  Accessory.java                 Section accessories: buttons and thumbnails
```

### 1.7b How the template's own services are called today

Relevant to the wiring task: menu data will come from these, and the choice
between `MenuExecutor.supply` and a native async API depends on the answer.

**Everything is blocking. There is no async or reactive API in the template
outside the menu package.** Specifically:

| Component | Shape | Consequence for menus |
| --- | --- | --- |
| `AbstractRepository` | synchronous JPA: `session.createQuery(...).list()`, `session.beginTransaction()` | must be called through `MenuExecutor.supply`, not directly from a handler |
| `DatabaseManager` | `SessionFactory`, built eagerly by Guice | startup only; not on the interaction path |
| `TaskManager` | `CompletableFuture` over a scheduled pool | usable directly, but it is a general scheduler, not a data source |
| `CommandListener` | `CompletableFuture.runAsync` on a virtual-thread executor | the pattern the menu system generalises |
| JDA REST | `RestAction.submit()` | already asynchronous; compose rather than wrap |

Hibernate offers an `unwrap(Session.class, CompletionStage.class)` style handle for
asynchronous completion, but nothing in this template uses it and no repository
exposes it. So the wiring task should assume **blocking repositories** and reach
them through `MenuExecutor.supply`, which keeps the JDA thread free and bounds the
real concurrency at the HikariCP pool.

The two `CompletableFuture` users above are `TaskManager` (scheduling, not data)
and `CommandListener` (dispatch), neither of which provides an async repository.

### 1.8 Test infrastructure

`tasks.test` uses `useJUnitPlatform()`. Two JVM settings are required, both for
the toolchain rather than for the tests:

- `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`, because
  Gradle's test executor loads the launcher reflectively and its absence fails
  the task with `Failed to load JUnit Platform`.
- `-javaagent:${mockitoAgent.asPath}` pointing at `byte-buddy-agent`, because
  Mockito's inline mock maker otherwise self-attaches and prints
  `Mockito is currently self-attaching` on every test JVM start on Java 27.

A third setting is the `stress` tag, which is excluded unless `-PrunStress` is
passed: the concurrency stress class measures wall-clock percentiles and should not
fail a build for being slow.

`src/test/java/es/redactado/TestStackTest.java` proves the stack: four tests
covering interface mocking, interaction verification, virtual threads, and the
pinned toolchain version.

## 2. JDA 6.4.2 API surface

Checked in `JDA-6.4.2-sources.jar`.

### 2.1 Available

Components V2 is fully present:

| Type | Location |
| --- | --- |
| `Container` | `net.dv8tion.jda.api.components.container.Container` |
| `Section` | `net.dv8tion.jda.api.components.section.Section` |
| `TextDisplay` | `net.dv8tion.jda.api.components.textdisplay.TextDisplay` |
| `Thumbnail` | `net.dv8tion.jda.api.components.thumbnail.Thumbnail` |
| `Separator` | `net.dv8tion.jda.api.components.separator.Separator` |
| `MediaGallery` | `net.dv8tion.jda.api.components.mediagallery.MediaGallery` |
| `FileDisplay` | `net.dv8tion.jda.api.components.filedisplay.FileDisplay` |
| `ActionRow` | `net.dv8tion.jda.api.components.actionrow.ActionRow` |
| `StringSelectMenu` | `net.dv8tion.jda.api.components.selections.StringSelectMenu` |
| `TextInput` | `net.dv8tion.jda.api.components.textinput.TextInput` |
| `Label` | `net.dv8tion.jda.api.components.label.Label` |
| `Modal` | `net.dv8tion.jda.api.modals.Modal` |

`MessageRequest#useComponentsV2()` and `MessageRequest#setComponents(Collection)`
exist. `MessageEditRequest extends MessageRequest`, so
`WebhookMessageEditAction` inherits both, and the source pattern
`hook.editOriginalComponents(container).useComponentsV2().queue()` is valid on
this version. `MessageCreateRequest` likewise extends `MessageRequest`.

There are no `Components.container(...)` convenience factories in this version;
the entry point is `Container.of(...)`.

### 2.2 Modal building

`Label` exists, so modals are built exactly as the source does, with
`Modal.Builder#addComponents(Label.of(String label, TextInput input))`.

Two facts that matter for `ModalForm`:

- `TextInput.create` takes `(String id, TextInputStyle style)`. There is **no**
  label parameter and **no** `TextInput.MAX_LABEL_LENGTH`. The 45-character limit
  moved to `Label.LABEL_MAX_LENGTH`.
- `Modal.Builder` has **no** `addActionRow`. Its only mutators are `setId`,
  `setTitle`, and three `addComponents` overloads. Every modal input must be
  wrapped in a `Label`.

For reference, in JDA 6.0.0-rc.3 `Modal.Builder#addActionRow` already carried
`@Deprecated` and `@ForRemoval`, so the fallback path described in the task plan
would have used a deprecated method even on that version.

### 2.3 Limits read from the JDA version

| Constant | Value |
| --- | --- |
| `Message.MAX_CONTENT_LENGTH_COMPONENT_V2` | 4000 |
| `Message.MAX_COMPONENT_COUNT_IN_COMPONENT_TREE` | 40 |
| `Section.MAX_COMPONENTS` | 3 |
| `MediaGallery.MAX_ITEMS` | 10 |
| `Thumbnail.MAX_DESCRIPTION_LENGTH` | 1024 |
| `SelectMenu.ID_MAX_LENGTH` | 100 |
| `SelectMenu.PLACEHOLDER_MAX_LENGTH` | 100 |
| `SelectMenu.OPTIONS_MAX_AMOUNT` | 25 |
| `SelectOption.LABEL_MAX_LENGTH` | 100 |
| `SelectOption.VALUE_MAX_LENGTH` | 100 |
| `SelectOption.DESCRIPTION_MAX_LENGTH` | 100 |
| `TextInput.MAX_ID_LENGTH` | 100 |
| `TextInput.MAX_VALUE_LENGTH` | 4000 |
| `TextInput.MAX_PLACEHOLDER_LENGTH` | 100 |
| `Label.LABEL_MAX_LENGTH` | 45 |
| `Label.DESCRIPTION_MAX_LENGTH` | 100 |
| `Modal.MAX_COMPONENTS` | 5 |
| `Modal.MAX_ID_LENGTH` | 100 |
| `Modal.MAX_TITLE_LENGTH` | 45 |
| `ButtonStyle` | `PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `LINK`, `PREMIUM`, `UNKNOWN` |

### 2.4 Interaction APIs used by the dispatcher

| Need | Verified signature |
| --- | --- |
| Fast acknowledgement | `GenericComponentInteractionCreateEvent#deferEdit()` returning `MessageEditCallbackAction` |
| Modal response | `GenericComponentInteractionCreateEvent#replyModal(Modal)` returning `ModalCallbackAction` |
| Hook access | `GenericComponentInteractionCreateEvent#getHook()` |
| Edit the original message | `InteractionHook#editOriginalComponents(Collection<? extends MessageTopLevelComponent>)` returning `WebhookMessageEditAction<Message>` |
| Send an ephemeral follow-up | `InteractionHook#sendMessage(String)` |
| Button payload | `getComponentId()`, `getMessageIdLong()`, `getMessage()`, `getChannel()`, `getUser()`, `getGuild()`, `getMember()` |
| Select payload | `StringSelectInteraction#getValues()`, `getSelectedOptions()` |
| Modal payload | `ModalInteractionEvent#getModalId()`, `getValues()`, `ModalMapping#getCustomId()`, `getAsString()` |
| Locale | `Interaction#getUserLocale()` returning `DiscordLocale`, `Interaction#getGuildLocale()` |
| Emoji | `Emoji#fromUnicode(String)`, `Emoji#fromCustom(String, long, boolean)` |
| Buttons | `Button#primary/secondary/success/danger/link/of`, `#asDisabled()`, `#withEmoji(Emoji)` |

`DiscordLocale#getLanguageTag()` and `getLocale()` are available, so locale
selection can follow interaction locale, then guild locale, then English.

## 3. Source menu tree

Root: `/home/redactado/Workspace/scpsl-helperbot/src/main/java/es/redactado/menu`
(29 files, 1737 lines of Java, including `ProfileMenu`).

The source project uses JDA 6.4.2, which is now also the target's version, so
every Components V2 pattern the source relies on ports over unchanged. The only
API edits the port needs are the ones made for the menu framework's own rules,
not for version compatibility.

`repomix-output.xml` in the source menu directory is a generated dump, not code,
and is not ported.

### 3.1 Classes ported

All 28 SOURCE classes except `ProfileMenu` were ported in T1. The "Target" column
is the final path; the "Name" column is the final type name where it differs.

| SOURCE | Target | Name | Later work |
| --- | --- | --- | --- |
| `api/Component.java` | `api/MenuComponent.java` | `MenuComponent` | render contract fixed in T10 |
| `api/Context.java` | `api/MenuContext.java` | `MenuContext` | state and back-stack moved to sessions in T5 |
| `api/Menu.java` | `api/Menu.java` | `Menu` | **T3**: `onButton`/`onModal` replaced by `actions(ActionTable.Builder)`; **T8**: gains `presetName()` so a menu can force a look |
| `api/NavigationAware.java` | `api/NavigationAware.java` | `NavigationAware` | becomes the `onEnter`/`onLeave` hook in T5, or is removed |
| `api/Renderable.java` | `api/Renderable.java` | `Renderable` | unused, removed in T14 |
| `base/AbstractMenu.java` | `core/AbstractMenu.java` | `AbstractMenu` | **T3** registers the built-in `nav` action; **T5** the nav action delegates to `Navigator` |
| `base/BaseContext.java` | `core/BaseContext.java` | `BaseContext` | becomes the `MenuContext` implementation in T5 |
| `builder/MenuBuilder.java` | `view/MenuBuilder.java` | `MenuBuilder` | preset-aware: tone, footer |
| `component/ActionButton.java` | `view/ActionButton.java` | `ActionButton` | broken cast, fixed in T10 |
| `component/ActionRow.java` | `view/Row.java` | `Row` | renamed to avoid the JDA clash |
| `component/Field.java` | `view/Field.java` | `Field` | emoji from preset in T10 |
| `component/Gallery.java` | `view/Gallery.java` | `Gallery` | as-is |
| `component/JdaSeparator.java` | `view/JdaSeparator.java` | `JdaSeparator` | folded into `Divider` in T10 |
| `component/LinkButton.java` | `view/LinkButton.java` | `LinkButton` | broken cast, fixed in T10 |
| `component/SectionList.java` | `view/SectionList.java` | `SectionList` | superseded by `Pager<T>` in T10 |
| `component/Text.java` | `view/Text.java` | `Text` | as-is |
| `component/ThumbnailComponent.java` | `view/ThumbnailComponent.java` | `ThumbnailComponent` | broken cast, folded into `Section` in T10 |
| `dispatch/ComponentId.java` | `core/ComponentId.java` | `ComponentId` | **rewritten in T2**: `indexOf`/`substring` only, `decode` returns `Optional` |
| `dispatch/MenuRouter.java` | `core/MenuRouter.java` | `MenuRouter` | **T3** action tables and ack; **T4** owner check, re-entrancy guard, and virtual-thread executor |
| `exception/ComponentLimitException.java` | `api/ComponentLimitException.java` | `ComponentLimitException` | kept |
| `exception/MenuException.java` | `api/MenuException.java` | `MenuException` | kept as the base type |
| `exception/MenuNotFoundException.java` | `api/MenuNotFoundException.java` | `MenuNotFoundException` | kept |
| `exception/StateNotFoundException.java` | `api/StateNotFoundException.java` | `StateNotFoundException` | replaced by `UserFacingException` in T5 |
| `navigation/NavigationAction.java` | `core/NavigationAction.java` | `NavigationAction` | **T5** parses mode and target into `ctx.navigate` |
| `navigation/NavigationMode.java` | `api/NavigationMode.java` | `NavigationMode` | **T5** moved to `api`, reduced to `PUSH`, `REPLACE`, `BACK`, `ROOT` |
| `validation/Limits.java` | `api/Limits.java` | `Limits` | moved out of `view` in prep; extended with the 6.4.2 limits in T10 |
| `validation/ValidationResult.java` | `api/ValidationResult.java` | `ValidationResult` | **PREP** moved to `api`; rewritten as a record in T10 |
| `validation/Validator.java` | `api/Validator.java` | `Validator` | **PREP** moved to `api`; every outgoing view is checked by `ViewEditor` |

### 3.2 Not ported

`ProfileMenu.java` (367 lines) is the only SOURCE menu class that was not ported,
and it never becomes production code. Per section 1 of the plan it is ported only
in T12 as `examples/ProfileExampleMenu`, backed by an in-memory fake service, to
demonstrate the framework. Its bot-specific logic is not copied: neither the
`ApplyService` dependency nor the `es.redactado.database.type.LinkType` reference.

`repomix-output.xml` is a generated dump and is not code.

### 3.3 Source behaviour to correct during the port

Carried over from the source, and each one contradicts a rule of the plan:

- The back stack and state map lived on the per-interaction context, so both were
  discarded when the click that created them returned. Going back therefore always
  found an empty stack, and pagination always reset to page one.
  **Fixed in T5:** both moved to a per-message `Session`.
- `ComponentId.decode` used `String.split`, a regex, and a per-call `ArrayList`.
  Section 3 requires `indexOf`/`substring` and a prebuilt map. **Fixed in T2.**
- `ComponentId.decode` returned `null` for a bad prefix, so callers could not tell
  a foreign component from a malformed one. Section 5.3 requires an explicit
  result. **Fixed in T2:** it now returns `Optional<ComponentId>`.
- `MenuRouter` held menus in a `ConcurrentHashMap` and resolved them by exception
  on miss, and dispatched with a `switch` on the action string. Section 3 requires
  immutable snapshots and O(1) lookups without exceptions on the hot path.
  **Fixed in T3:** each menu builds one immutable `ActionTable` at registration,
  and dispatch is two map lookups with no string matching. The `ConcurrentHashMap`
  itself is retained for now, because registration happens at startup and is not
  the hot path; T4 may switch it to an immutable snapshot.
- `ProfileMenu.onButton` had a `switch` with a `default ->` branch that opened a
  modal for any unrecognised action. Section 5.4 and T3 forbid that. **Fixed in
  T3:** every action must be declared with an explicit `Ack`, and an unknown action
  now produces a localized warning reply instead of opening a modal.
- A handler that threw synchronously escaped the router entirely, because the
  failure handler was only attached to the returned future. **Fixed in T3.**
- `MenuRouter.dispatchButton` catches `Exception` and replies with
  `"❌ Error: " + e.getMessage()`, exposing internal messages to users.
  Section 2.2 and 5.5 require a single `ErrorReply` path with a reference code.
- `MenuRouter` and `AbstractMenu.refresh` run `queue()` on the JDA thread after
  doing the work inline. Section 3 requires acknowledging first and working on a
  dedicated executor.
- `BaseContext` keeps a `ConcurrentHashMap` state map and an `ArrayDeque`
  back-stack on the per-event object, so state dies with the event.
  Section 3 requires session-scoped state.
- `ProfileMenu.buildSummary` calls `ctx.guild().retrieveMemberById(userId).complete()`,
  a blocking REST call on the event thread, explicitly forbidden by section 3.
- `ProfileMenu` replies with Spanish literals (`"Anadir enlace"`,
  `"Volver"`, `"No hay elementos."`). Section 5.6 requires resource bundles.
- `Field` hardcodes custom emoji ids (`lucide_check`, `lucide_eraser`) and the
  Spanish fallback `"*Pendiente*"`. Section 5.1 requires preset-driven emoji and
  5.6 requires bundles.
- `Field` and `ActionButton` fall back to `Emoji.fromUnicode("⬜")` when no emoji
  is set, which is a hardcoded emoji outside a preset.
- `SectionList` stores its page in per-event context state, so pagination resets
  on every click, and it renders a `"noop"` custom id that no action handles.
- `Validator` throws `ComponentLimitException` in production, while section 5.3
  requires failing fast in tests and degrading with a truncation plus a warning
  in production.
- `ProfileMenu` reaches into `es.redactado.database.type.LinkType`, a
  bot-specific type that must not be copied into the template.
- `ActionButton#render`, `LinkButton#render`, and `ThumbnailComponent#render`
  cast a JDA `Button` or `Thumbnail` to `ContainerChildComponent`. Those JDA
  types extend `ActionRowChildComponent` and `SectionAccessoryComponent`, and
  the hierarchies are disjoint, so every call threw `ClassCastException`. The
  source bot never hit this because `ProfileMenu` builds JDA components directly
  and never instantiates these three classes. **Fixed** by splitting the render
  contract into `MenuComponent`, `RowItem`, and `Accessory`.
- `ActionButton#render` called `Button.of(style, id, emoji)` when the label was
  empty, but that overload requires a non-null emoji. Removing the `"⬜"`
  placeholder turned a cosmetic default into a `NullPointerException`. **Fixed
  in T1b** by using the four-argument `Button.of`, which accepts a nullable
  label and emoji and lets JDA enforce the "label or emoji" rule.

### 3.4 Source constants reused verbatim

These are already correct and stay unchanged:

- `menu:` prefix in `ComponentId`
- `MAX_CONTAINER_CHILDREN = 25`
- `WARN_CONTAINER_CHILDREN = 20`
- `MAX_ACTION_ROW_CHILDREN = 5`
- `MAX_CUSTOM_ID_LENGTH = 100`
- `MAX_MEDIA_GALLERY_ITEMS = 10`

### 3.5 Components added during the port

These arrived after the source tree was read, each in `es.redactado.menu.view`,
each routed through the same action table as a class-based menu. Limits are
enforced at construction except where a limit cannot be known until render, which
is said so on the method.

### `Nav`

Navigation buttons. All of them use the built-in `nav` action, so no menu
declares or wires them, and the id carries the mode, the target menu and the
target view rather than a handler. Rendered in the preset's `SECONDARY` style;
only `back()` takes its label from the bundles.

| Factory | Goes to | Mode |
| --- | --- | --- |
| `Nav.back()` | the previous view | back |
| `Nav.view(action, label, params...)` | a view of this menu | push |
| `Nav.swap(action, label, params...)` | a view of this menu | replace |
| `Nav.push(menuId, label)` | another menu's home | push |
| `Nav.replace(menuId, label)` | another menu's home | replace |
| `Nav.root(menuId, label)` | another menu's home, clearing history | root |
| `Nav.to(menuId, action, label, params...)` | a view of another menu | push |

The id is `menu:<current>:nav:<mode>[:<targetMenu>[:<viewAction>[:<param>...]]]`,
with the view and its params omitted when the button names only a menu, so an id
written before views existed still resolves.

```java
Row.of(Nav.back(), Nav.view("details", "Details", id))
```

### `Pager<T>`

One page of a list, with previous and next controls. The page lives in the session
under `pager:<id>`, and the stored page is clamped on read rather than trusted.
Limits: 1 to 20 items per page; the id must match `[a-z0-9_]{1,20}` so it survives
custom-id encoding.

```java
Pager.of("users", users, 5, user -> Field.of(user.name(), user.tag()))
```

### `Confirm`

A prompt with a yes and a no, rendered as a view rather than a flag on the view
that triggered it, so cancel is a plain `Nav.back()` and has no state to unwind.
`.danger()` asks for the destructive role.

```java
Confirm.of(Text.of(labels.deletePrompt()), "reallyDelete", entityId).danger()
```

### `SelectMenu`

A string select. Value first, label second, which is the reverse of JDA's own
`addOption` and deliberate: the value is what a handler receives. Limits: 25
options, 100 characters for the value, label, description and placeholder, and the
encoded id stays within 100. A duplicate value, or a default that matches no
option, is refused, because Discord drops both silently. A select must be the only
item in its row.

```java
Row.of(SelectMenu.of("assign", "Pick a role").option("owner", "Owner").selected("owner"))
```

### `ModalForm`

A modal built from labelled inputs, the one mutable type here: configured by the
calls that follow `shortField` or `paragraph`, built once, discarded. Limits: 5
fields, 45 characters for the title and for each label, 100 for a field id and for
a placeholder, 4000 for a value, and a form with no fields is refused. A modal must
be the first and only response, so it belongs behind `Ack.MODAL`.

```java
ModalForm form = ModalForm.create(ctx, "apply", "Apply");
form.shortField("name", "Name").required(true);
showModal(ctx, form.build());
```

## 3.6 Simple menus

`es.redactado.menu.simple` declares a menu without writing a class. What
`SimpleMenuBuilder.build()` returns is an ordinary `es.redactado.menu.api.Menu`,
extending the same `AbstractMenu` a hand-written one extends, so routing, the
owner check, the duplicate-click guard, sessions, presets, translations and the
loader timeout are the framework's and not the DSL's. There is no second dispatch
path and no way for a simple menu to be treated differently.

```java
Menu help = Menus.simple("help")
    .tone(Tone.INFO)
    .home(v -> v
        .header(Msg.key("help.title"), Msg.key("help.subtitle"))
        .text("Pick a topic.")
        .row(r -> r.primary("faq", "FAQ", click -> click.go("faq"))
                   .link("Docs", "https://example.com")))
    .view("faq", v -> v.text("...").row(r -> r.back()))
    .build();
```

The builders are mutable, one pass, and discarded; everything built is immutable
and safe to share, which is what `SimpleMenuThreadSafetyTest.concurrentRendersAreIndependent`
proves by rendering one menu from eight threads at once.

### `Msg`

The only user-facing text type in the DSL, and a functional interface with no
behaviour beyond resolving: `Msg.literal(String)` for content, and
`Msg.key(String, Object... args)` for anything a user reads in more than one
language, resolved through `ctx.t`. An unknown key renders as the key, loudly.

### Reference

`SimpleMenuBuilder<M>`, all of it fluent, all returning the builder:

| Method | Purpose | Limits and rules |
| --- | --- | --- |
| `tone(Tone)` | what the menu is for, and so which colour it takes from the preset | never null; default `Tone.ACCENT` |
| `preset(String)` | forces a look, whatever the guild and user asked for | not blank |
| `shared()` | usable by everyone who can see it, not only its owner | off by default |
| `loadTimeout(Duration)` | how long a render waits for the loader | null means the 10-second default |
| `home(Consumer<ViewBuilder<M>>)` | the view a menu opens on | **required**, and only once |
| `view(String, Consumer<ViewBuilder<M>>)` | a view reachable by navigation | the name is also the action that opens it |
| `onClick(String, ClickHandler)` | handles an action whose button a `custom(...)` component drew | name is menu-wide |
| `onSubmit(String, SubmitHandler)` | handles a submitted modal form | name is menu-wide |
| `build()` | returns the menu | runs the whole-menu checks |

`ViewBuilder<M>`, likewise fluent:

| Method | Purpose | Limits and rules |
| --- | --- | --- |
| `header(Msg)` / `header(Msg, Msg)` / `header(String)` | the title, with an optional line under it | a preset without subtitles drops the subtitle |
| `text(String)` / `message(Msg)` | a line of fixed text | the `String` form is a **literal**; `header(String)` is a **key** |
| `text(Function<Scope<M>, String>)` | a line built from the loaded model | called once per render |
| `field(Msg, Function<Scope<M>, String>)` | a labelled value, laid out beside other fields | label resolved per reader, value per render |
| `divider()` / `space()` | a rule, or the blank line a preset asks for instead | a preset may draw neither |
| `section(Msg, String)` | text with an image beside it | remote image URL |
| `row(Consumer<RowBuilder<M>>)` | up to five buttons, links or navigation | **at most 5 items**; refused at declaration |
| `select(String, Msg, Consumer<SelectSpec>, PickHandler)` | a select menu, in a row of its own | at most 25 options; a selected value no option declares is refused |
| `list(String, Function<Scope<M>, List<T>>, int, Function<T, String>)` | a paged list of text items | id `[a-z0-9_]{1,20}`, **unique per menu**; page 1 to 20 |
| `custom(MenuComponent)` / `custom(Function<Scope<M>, MenuComponent>)` | anything the DSL has no word for | built per render; a lambda needs a typed local because `MenuComponent` is itself a functional interface |

`RowBuilder<M>`, whose button methods return a `ButtonSpec` and whose `and()`
comes back to the row:

| Method | Purpose | Limits and rules |
| --- | --- | --- |
| `primary` / `secondary` / `success` / `danger` | a button that runs a handler | `Msg` or literal label; a handler is required |
| `link(Msg, String)` / `link(String, String)` | a Discord link button | a URL is required; it leaves the menu system entirely |
| `back()` | the previous view | **legal on `home`**, where it renders home again |
| `view(String, String)` | opens a view of this menu, keeping this one in history | the view must be declared |
| `item(RowItem)` | a row item the DSL has no word for | the same 5-item limit |
| `ButtonSpec.icon(IconKey)` / `params(String...)` / `disabled(boolean)` / `opensModal()` | the details only some buttons have | `params` are counted against the 100-character id limit at declaration |
| `ButtonSpec.and()` | back to the row | only needed after a button method |

`SelectSpec`: `option(String value, String label)`, `option(value, label, description)`,
`selected(String...)`, `range(int min, int max)`. The value is what a handler
receives; the label is what a user reads.

### Handlers and triggers

Every handler returns a `CompletableFuture<Void>` and takes exactly one argument,
which is a trigger:

| Type | Handler | Carries |
| --- | --- | --- |
| `Click` | `ClickHandler.handle(Click)` | `event()`, and `modal(Modal)` for a button declared `opensModal()` |
| `Pick` | `PickHandler.handle(Pick)` | `values()`, the chosen values |
| `Submit` | `SubmitHandler.handle(Submit)` | `values()`, the answers keyed by field id and trimmed |

A `Trigger` has `ctx()`, `refresh()`, `go(String viewName)`,
`go(String menuId, String viewName)`, `back()`, `reply(Msg)`,
`reply(String key, Object... args)` and `done()`. `refresh()` re-renders the view
that owns the action that was pressed, so one handler is correct on any view.

`AbstractMenu.refresh(ctx)` does the same for a hand-written menu: it renders
`ctx.at(currentView(ctx))`, never `ctx` as it stands. A handler is reached
through an action, and for most of them that action is not a view, so rendering
the context as it stands asks the menu for a view named after a button or a
submission and the user is told the view is not available straight after their
change was saved. `RefreshCurrentViewTest` is the regression test for that.

### What is refused, and when

Most rules fire **where the element is declared**, because that is where the
author is looking at the line: a duplicate action name, a reserved name
(`nav`, `page`, `home`), an action name with a colon, an id that cannot fit in 100
characters once its params are counted, an invalid or duplicate list id, a row
over five items, an empty row, a select with no or too many options, a selected
value no option declares, and a view name that is already taken.

`build()` then runs the checks that need every view in front of it: a menu with no
home view, and a view declaring more elements than a container holds.

The exception is always `IllegalStateException` or `IllegalArgumentException`, and
the message names the menu, the view and the element.

### Examples

All three are compiled and tested but **never registered by default**, and live in
the same exempt package as the showcase, because an example is allowed to write
literal text where the framework is not.

| Example | Shows | Proved by |
| --- | --- | --- |
| `HelpMenu` | two static views, a link, Back | `SimpleExamplesRenderTest.everyExampleViewRendersWithinLimits`, `SimpleExamplesEndToEndTest.helpNavigatesAndComesBack`, `SimpleExamplesEndToEndTest.helpLinkIsNotAnAction` |
| `CounterMenu` | session state, `refresh()`, a reset behind a `Confirm` dropped in with `custom(...)` | `SimpleExamplesEndToEndTest.counterCountsInTheSession`, `SimpleExamplesEndToEndTest.counterResetIsConfirmed`, `SimpleExamplesEndToEndTest.counterResetRuns` |
| `ServerInfoMenu` | a slow loader, a paged list, a select that switches the section shown | `SimpleExamplesEndToEndTest.serverInfoLoadsAndPages`, `SimpleExamplesEndToEndTest.serverInfoSelectSwitchesSection`, `SimpleExamplesEndToEndTest.serverInfoLoaderIsAsynchronous` |

### Two names that differ from the obvious spelling

- **`message(Msg)` rather than `text(Msg)`.** `Msg` is a functional interface, so
  `text(Msg)` beside `text(Function<Scope<M>, String>)` makes every
  `text(s -> ...)` ambiguous and the example above would not compile. `text` keeps
  the scope form, which is what the lambdas need.
- **`onClick` exists at all.** An action is declared by the element that draws it,
  which leaves nothing to declare an action whose button comes from a
  `custom(...)` component. `CounterMenu` needs it for the confirming button of a
  `Confirm`.

## 4. Open questions

Recorded in `NOTES.md` rather than resolved unilaterally.