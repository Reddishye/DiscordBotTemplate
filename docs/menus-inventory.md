# Menu System Inventory

Reference document for the menu framework port. It records what the target
template provides, what the source menu tree contains, and where the two do not
line up. Every JDA statement below was verified against the sources jar of the
exact JDA version this template depends on.

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
| Build tool | Gradle 9.0.0 (Kotlin DSL, `build.gradle.kts`) |
| Java version | pinned to 21 by `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }` |
| Formatter | Spotless 7.2.1, google-java-format 1.26.0, AOSP style, `reflowLongStrings`, `skipJavadocFormatting`, `formatAnnotations`, `removeUnusedImports` |
| Packaging | Shadow 9.2.2 (`shadowJar`), Sentry 5.12.1, `application` plugin with main class `es.redactado.Main` |

A formatter is already configured, so Spotless stays as-is. Java 21 supplies
records, sealed hierarchies, pattern-matching `switch`, and virtual threads.

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

Bindings for the menu framework follow the same pattern: bind in `BotModule`,
resolve instances through the `Injector`.

### 1.5 Event listeners

Listeners are discovered from the static list `es.redactado.config.Listeners.LISTENERS`.
`Main.instantiateListeners()` asks the injector for each entry and registers the
result with `ShardManager.addEventListener`. Slash commands that also extend
`ListenerAdapter` are appended from `CommandRegister.getListeners()`.

The only current entry is `CommandListener`, which shows the template's
interaction convention: take the event on the JDA thread, hand the work to
`Executors.newVirtualThreadPerTaskExecutor()`, acknowledge before doing work, and
route failures to a single ephemeral error reply plus `Sentry.captureException`.

The menu listener is registered the same way: one `MenuListener extends
ListenerAdapter` added to `Listeners.LISTENERS`, delegating to `MenuRouter`.

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

The menu runtime needs to close its executor, its session cache, its data
cache, and the preset file watcher. Because `Main.shutdown()` already delegates
to `ServiceManager.stopAll()`, the conservative wiring is a single
`MenuRuntime implements IService` registered in `INFRASTRUCTURE_SERVICES`, so the
existing shutdown hook closes everything in reverse initialisation order without
touching `Main`.

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
serialised on its own. `api`, `core`, and `view` are free to import it; none of them
does yet, because components start reading presets in T10.

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

### 1.7 Target file tree

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
src/test/java/es/redactado/menu/view/
  MenuBuilderTest.java
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
                                   PresetRegistry, IconKey
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

`tasks.test` uses `useJUnitPlatform()`. Two JVM settings are required, both
explained in `NOTES.md`:

- `testRuntimeOnly("org.junit.platform:junit-platform-launcher")`, because
  Gradle's test executor loads the launcher reflectively and its absence fails
  the task with `Failed to load JUnit Platform`.
- `-javaagent:${mockitoAgent.asPath}` pointing at `byte-buddy-agent`, because
  Mockito's inline mock maker otherwise self-attaches and prints
  `Mockito is currently self-attaching` on every test JVM start on Java 21.

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
| `api/Menu.java` | `api/Menu.java` | `Menu` | **T3**: `onButton`/`onModal` replaced by `actions(ActionTable.Builder)` |
| `api/NavigationAware.java` | `api/NavigationAware.java` | `NavigationAware` | becomes the `onEnter`/`onLeave` hook in T5, or is removed |
| `api/Renderable.java` | `api/Renderable.java` | `Renderable` | unused, removed in T14 |
| `base/AbstractMenu.java` | `core/AbstractMenu.java` | `AbstractMenu` | **T3** registers the built-in `nav` action; **T5** the nav action delegates to `Navigator` |
| `base/BaseContext.java` | `core/BaseContext.java` | `BaseContext` | becomes the `MenuContext` implementation in T5 |
| `builder/MenuBuilder.java` | `view/MenuBuilder.java` | `MenuBuilder` | made preset-aware in T10 |
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
  and never instantiates these three classes. **Fixed in T1b** by splitting the
  render contract into `MenuComponent`, `RowItem`, and `Accessory`. Detailed in
  `NOTES.md`.
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

## 4. Open questions

Recorded in `NOTES.md` rather than resolved unilaterally.