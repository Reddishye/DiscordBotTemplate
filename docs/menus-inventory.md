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
| Test roots | none configured yet |

The menu framework therefore lives under `es.redactado.menu`.

### 1.2 Language and build

| Item | Value |
| --- | --- |
| Build tool | Gradle 9.0.0 (Kotlin DSL, `build.gradle.kts`) |
| Java version | not pinned by a `java { toolchain }` block, so it follows the Gradle JVM: JDK 24.0.2 |
| Formatter | Spotless 7.2.1, google-java-format 1.26.0, AOSP style, `reflowLongStrings`, `skipJavadocFormatting`, `formatAnnotations`, `removeUnusedImports` |
| Packaging | Shadow 9.2.2 (`shadowJar`), Sentry 5.12.1, `application` plugin with main class `es.redactado.Main` |

A formatter is already configured, so Spotless stays as-is. Because the Java
version is 24, records, sealed hierarchies, pattern-matching `switch`, and
virtual threads are all available.

### 1.3 Dependencies

Present in `build.gradle.kts`:

- JDA `6.0.0-rc.3` (`opus-java` excluded)
- discord-webhooks `0.8.4`
- Guice `7.0.0`
- dotenv-java `3.2.0`
- logback-classic `1.5.18`, slf4j-api `2.0.17`, jansi `2.4.2`
- tess4j `5.16.0`
- Hibernate ORM `7.1.5.Final` (core, hikaricp, jcache, community-dialects)
- jakarta.transaction-api
- HikariCP `7.0.2`, MariaDB `3.5.7`, SQLite `3.50.3.0`, H2 `2.3.232`

Findings that affect the port:

- **Caffeine is required but undeclared.** The working tree removed
  `com.github.benmanes.caffeine:caffeine:v3.2.2` and `caffeine:jcache:v3.2.2`
  from `build.gradle.kts`, yet `src/main/java/es/redactado/command/handler/CommandRegister.java`
  still imports `com.github.benmanes.caffeine.cache.Cache` and
  `com.github.benmanes.caffeine.cache.Caffeine`. `./gradlew compileJava`
  therefore fails with 8 errors before any menu work begins. Both dependency
  lines exist in `HEAD`, so restoring them is the conservative fix and it also
  satisfies section 2.3 of the plan.
- **No test framework.** There is no `src/test` tree, no JUnit/Mockito/AssertJ
  dependency, and no `test { useJUnitPlatform() }` block. The task plan adds
  JUnit 5, Mockito, and AssertJ.
- **Serialization.** Jackson Databind `2.19.1` is already resolved in
  `runtimeClasspath` through JDA's own dependency, but it is *not* on
  `compileClasspath`, so menu code cannot compile against it as-is. The plan
  declares it explicitly at the already-resolved version rather than introducing
  a new library.

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
```

## 2. JDA 6.0.0-rc.3 API surface

Checked in `JDA-6.0.0-rc.3-sources.jar`.

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
| `Modal` | `net.dv8tion.jda.api.modals.Modal` |

`MessageRequest#useComponentsV2()` and `MessageRequest#setComponents(Collection)`
exist. `MessageEditRequest extends MessageRequest`, so
`WebhookMessageEditAction` inherits both, and the source pattern
`hook.editOriginalComponents(container).useComponentsV2().queue()` is valid on
this version. `MessageCreateRequest` likewise extends `MessageRequest`.

There are no `Components.container(...)` convenience factories in this version;
the entry point is `Container.of(...)`.

### 2.2 Missing

`net.dv8tion.jda.api.components.label.Label` does not exist in 6.0.0-rc.3. The
source `ProfileMenu` builds modals with `Label.of(String, TextInput)`. On this
JDA version a modal is built with `Modal.Builder#addActionRow(TextInput)` and the
label is supplied through `TextInput.create(id, label, style)`, where
`TextInput.MAX_LABEL_LENGTH` is 45. The `ModalForm` component must use
`addActionRow`.

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
| `TextInput.MAX_LABEL_LENGTH` | 45 |
| `TextInput.MAX_VALUE_LENGTH` | 4000 |
| `TextInput.MAX_PLACEHOLDER_LENGTH` | 100 |
| `Modal.MAX_COMPONENTS` | 5 |
| `Modal.MAX_ID_LENGTH` | 100 |
| `Modal.MAX_TITLE_LENGTH` | 45 |
| `ButtonStyle` | `PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `LINK`, `PREMIUM`, `UNKNOWN` |

### 2.4 Interaction APIs used by the dispatcher

| Need | Available signature |
| --- | --- |
| Fast acknowledgement | `GenericComponentInteractionCreateEvent#deferEdit()` returning `MessageEditCallbackAction` |
| Modal response | `GenericComponentInteractionCreateEvent#replyModal(Modal)` returning `ModalCallbackAction` |
| Hook access | `GenericComponentInteractionCreateEvent#getHook()` |
| Edit the original message | `InteractionHook#editOriginalComponents(Collection<? extends MessageTopLevelComponent>)` returning `WebhookMessageEditAction<T>` |
| Send an ephemeral follow-up | `InteractionHook#sendMessage(String)` |
| Button payload | `GenericComponentInteractionCreateEvent#getComponentId()`, `getMessageIdLong()`, `getMessage()`, `getChannel()`, `getUser()`, `getGuild()`, `getMember()` |
| Select payload | `StringSelectInteraction#getValues()`, `getSelectedOptions()` |
| Locale | `Interaction#getUserLocale()` returning `DiscordLocale`, `Interaction#getGuildLocale()` |

`DiscordLocale#getLanguageTag()` and `getLocale()` are available, so locale
selection can follow interaction locale, then guild locale, then English.

## 3. Source menu tree

Root: `/home/redactado/Workspace/scpsl-helperbot/src/main/java/es/redactado/menu`
(29 files, 1737 lines of Java, including `ProfileMenu`).
The source project itself uses JDA 6.4.2, which is why `Label` appears there and
not in the target's JDA version.

`repomix-output.xml` in the source menu directory is a generated dump, not code,
and is not ported.

### 3.1 Classes to port

| Source | Lines | Target package | Notes |
| --- | --- | --- | --- |
| `api/Menu.java` | 24 | `es.redactado.menu.api` | becomes `Menu`, gains an action table in T3 |
| `api/Component.java` | 17 | `es.redactado.menu.api` | renamed `MenuComponent` |
| `api/Context.java` | 85 | `es.redactado.menu.api` | renamed `MenuContext`; state and back-stack removed in T5 |
| `api/Renderable.java` | 11 | not ported | redundant with `Component`, which already returns a list |
| `api/NavigationAware.java` | 15 | not ported as-is | becomes the `onEnter`/`onLeave` hook required by section 5.4 |
| `base/AbstractMenu.java` | 80 | `es.redactado.menu.core` | replaced by the T4 dispatcher; only the modal-opening helper survives |
| `base/BaseContext.java` | 197 | `es.redactado.menu.core` | becomes the `MenuContext` record |
| `builder/MenuBuilder.java` | 93 | `es.redactado.menu.view` | moved to the view layer and made preset-aware in T10 |
| `component/Text.java` | 36 | `es.redactado.menu.view` | as-is |
| `component/Field.java` | 65 | `es.redactado.menu.view` | hardcoded Spanish fallback and custom emoji ids removed |
| `component/ActionRow.java` | 41 | `es.redactado.menu.view` | renamed `Row` |
| `component/ActionButton.java` | 74 | `es.redactado.menu.view` | emoji now comes from the preset |
| `component/LinkButton.java` | 37 | `es.redactado.menu.view` | as-is |
| `component/Gallery.java` | 32 | `es.redactado.menu.view` | as-is |
| `component/SectionList.java` | 93 | `es.redactado.menu.view` | superseded by `Pager<T>` from section 5.3 |
| `component/JdaSeparator.java` | 26 | `es.redactado.menu.view` | absorbed into `Divider` |
| `component/ThumbnailComponent.java` | 25 | `es.redactado.menu.view` | absorbed into `Section` accessory |
| `dispatch/ComponentId.java` | 71 | `es.redactado.menu.core` | rewritten in T2 without `split` |
| `dispatch/MenuRouter.java` | 102 | `es.redactado.menu.core` | rewritten in T4 as the O(1) dispatcher |
| `exception/MenuException.java` | 11 | `es.redactado.menu.api` | renamed `MenuException`, kept as the base type |
| `exception/ComponentLimitException.java` | 20 | `es.redactado.menu.view` | kept, raised by `Limits` |
| `exception/MenuNotFoundException.java` | 14 | `es.redactado.menu.api` | kept |
| `exception/StateNotFoundException.java` | 14 | not ported | replaced by `UserFacingException` from 5.5 |
| `navigation/NavigationAction.java` | 24 | `es.redactado.menu.core` | absorbed into the navigation API of T5 |
| `navigation/NavigationMode.java` | 13 | `es.redactado.menu.core` | reduced to the push/pop/replace/root modes of section 5.4 |
| `validation/Limits.java` | 24 | `es.redactado.menu.view` | extended with the JDA 6.0.0-rc.3 limits |
| `validation/ValidationResult.java` | 72 | `es.redactado.menu.view` | kept, rewritten as an immutable record |
| `validation/Validator.java` | 54 | `es.redactado.menu.view` | kept, extended for tests-versus-production behaviour |

### 3.2 Not ported

- `ProfileMenu.java` (367 lines). Ported only as `examples/ProfileExampleMenu`
  backed by an in-memory fake service, per section 1.
- `repomix-output.xml`. Generated dump.

### 3.3 Source behaviour to correct during the port

Carried over from the source, and each one contradicts a rule of the plan:

- `ComponentId.decode` uses `String.split`, a regex, and a per-call
  `ArrayList`. Section 3 requires `indexOf`/`substring` and a prebuilt map.
- `ComponentId.decode` returns `null` for a bad prefix, so callers cannot tell a
  foreign component from a malformed one. Section 5.3 requires an explicit
  result.
- `MenuRouter` holds menus in a `ConcurrentHashMap` and resolves them by
  exception on miss. Section 3 requires immutable snapshots and O(1) lookups
  without exceptions on the hot path.
- `MenuRouter.dispatchButton` catches `Exception` and replies with
  `"❌ Error: " + e.getMessage()`, exposing internal messages to users.
  Section 2.2 and 5.5 require a single `ErrorReply` path with a reference code.
- `MenuRouter` and `AbstractMenu.refresh` run `queue()` on the JDA thread after
  doing the work inline. Section 3 requires acknowledging first and working on a
  dedicated executor.
- `BaseContext` keeps a `ConcurrentHashMap` state map and an `ArrayDeque`
  back-stack on the per-event object, so state dies with the event.
  Section 3 requires session-scoped state.
- `ProfileMenu.onButton` uses a `switch` on the action string with a
  `default ->` branch that opens a modal. Section 5.4 and T3 forbid implicit
  "default opens a modal".
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