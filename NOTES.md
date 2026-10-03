# NOTES

Open questions and deviations from the task plan. Newest entries are appended at
the bottom of the relevant section.

## Blockers and build fixes

### The template did not compile before any menu work

`build.gradle.kts` in the working tree removed

```kotlin
implementation("com.github.ben-manes.caffeine:caffeine:v3.2.2")
implementation("com.github.ben-manes.caffeine:jcache:v3.2.2")
```

while `src/main/java/es/redactado/command/handler/CommandRegister.java` still
imports `com.github.benmanes.caffeine.cache.Cache` and
`com.github.benmanes.caffeine.cache.Caffeine`. `./gradlew compileJava` failed
with 8 errors.

**Decision.** Restore both lines at the versions already present in `HEAD`. The
menu system needs Caffeine anyway (sessions, cooldown, async loading), so section
2.3 permits it, and this is the state the repository was committed in.

**Outcome.** No commit was required. Both Caffeine lines are already in `HEAD`;
the breakage was an *uncommitted* local edit to `build.gradle.kts`. Restoring the
file to its committed content was sufficient, so `build.gradle.kts` is now
byte-identical to `HEAD` and there is no `restore caffeine dependency` commit to
make. An empty commit was deliberately not created.

### No test framework exists

There is no `src/test` tree, no JUnit/Mockito/AssertJ dependency, and no
`test { useJUnitPlatform() }` block. T2 onward require tests, so JUnit 5
(Jupiter), Mockito, and AssertJ are added as `testImplementation`. This is
allowed by section 2.3.

### No Java toolchain is pinned

`build.gradle.kts` has no `java { toolchain }` block, so the Java version follows
the Gradle JVM, currently JDK 24.0.2. Records, sealed hierarchies,
pattern-matching `switch`, and virtual threads are all available, so
`Executors.newVirtualThreadPerTaskExecutor()` is the executor backing from T4 on,
as section 3 requires for Java 21 or newer.

**Open question.** Should a toolchain be pinned so the build is reproducible?
Left unpinned because changing the Java version is outside the scope of this
plan. Recommend `java { toolchain { languageVersion = JavaLanguageVersion.of(24) } }`
as a separate change.

## JDA version differences

### `Label` does not exist in JDA 6.0.0-rc.3

The target uses JDA `6.0.0-rc.3`; the source project uses `6.4.2`. In the source,
`ProfileMenu` builds modals with `Label.of(String, TextInput)`. There is no
`net.dv8tion.jda.api.components.label.Label` in the target's JDA version.

**Decision.** `ModalForm` uses `Modal.Builder#addActionRow(TextInput)` with the
label passed to `TextInput.create(id, label, style)`, honoring
`TextInput.MAX_LABEL_LENGTH = 45`. Modals stay limited to 5 components, which is
`Modal.MAX_COMPONENTS` on both versions.

### `Components` has no V2 convenience factories

In 6.0.0-rc.3 there is no `Components.container(...)`. The view layer uses
`Container.of(...)`, `Section.of(...)`, `TextDisplay.of(...)`,
`Thumbnail.fromUrl(...)`, `Separator.create(...)`, and
`MediaGallery.of(...)` directly.

### `useComponentsV2()` on hook actions

Verified that `MessageEditRequest extends MessageRequest` and
`MessageCreateRequest extends MessageRequest`, so `WebhookMessageEditAction`
inherits both `setComponents(Collection)` and `useComponentsV2()`. The source
pattern `hook.editOriginalComponents(container).useComponentsV2().queue()` is
valid on this JDA version and is mirrored unchanged.

### Per-user locale

`User#getLocale()` does not exist in 6.0.0-rc.3, but
`Interaction#getUserLocale()` returning `DiscordLocale` does. Section 5.6 is
therefore satisfied from the interaction rather than from the `User` entity, with
`Interaction#getGuildLocale()` as the fallback and English as the last resort.

## Serialization decision

Section 2.3 says to reuse the serialization library already present in TARGET,
and to add exactly one only if none exists.

Jackson Databind `2.19.1` is already resolved in TARGET's `runtimeClasspath`
through JDA's own dependency, but it is **not** on `compileClasspath`, so menu
code cannot compile against it without declaring it.

**Decision.** Preset files are JSON, parsed with Jackson Databind, declared
explicitly as `implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")`
at the version already resolved in the project. No YAML module is added. This
reuses the library that is already on the runtime classpath instead of
introducing a second one.

## Architecture decisions

### Menu runtime lifecycle

Section 3 requires the executor, caches, and preset watcher to be closed from
the template's existing shutdown hook. `Main.shutdown()` already delegates to
`ServiceManager.stopAll()`, and `Main` itself must not change.

**Decision.** One `MenuRuntime implements IService` in `es.redactado.menu.core`,
registered in `Services.INFRASTRUCTURE_SERVICES`. Its `shutdown()` closes the
executor, both caches, and the watcher. `MenuRuntime` is an infrastructure
service because it must not depend on `ShardManager`.

### The menu listener

Registered by adding `MenuListener` to `Listeners.LISTENERS`, the template's
existing discovery mechanism, and resolved through the Guice injector like
`CommandListener`.

### Own scope of `Listeners.java`

`Listeners.java` is already modified in the working tree relative to `HEAD`
(`CommandListener` was added). Menu commits add one line to that list and stage
only that file.

## Deviation from the prescribed package layout

Section 4 lists `api`, `core`, `view`, `preset`, `simple`, `i18n`, `examples`.
The source tree uses different sub-package names (`api`, `base`, `builder`,
`component`, `dispatch`, `exception`, `modal`, `navigation`, `util`,
`validation`), and those are not kept, because section 4 gives an explicit target
layout.

Mapping chosen:

| Source package | Target package |
| --- | --- |
| `api` | `api` |
| `base`, `dispatch`, `navigation` | `core` |
| `builder`, `component`, `validation` | `view` |
| `exception` | split: `UserFacingException` and `MenuException` in `api`, `ComponentLimitException` in `view` |

`api` must hold public contracts per section 4, and section 5.5 puts
`UserFacingException` there explicitly.

## Source items deliberately not ported

- `api/Renderable.java`. `Component#render` already returns a list, so
  `Renderable` is a redundant single-method interface. Section 2.2 forbids
  over-abstracted single-implementation interfaces.
- `component/SectionList.java`. Section 5.3 specifies `Pager<T>` with the page
  held in the session. `SectionList` keeps its page in per-event state and emits
  an unhandled `noop` custom id, so porting it would reintroduce a known bug.
- `component/JdaSeparator.java` and `component/ThumbnailComponent.java`. Both are
  folded into `Divider` and the `Section` accessory required by section 5.3.
- `api/NavigationAware.java` in its current shape. Section 5.4 asks for
  `onEnter`/`onLeave` hooks that the router actually invokes; the source interface
  is never called anywhere in the source bot, so porting it verbatim would create
  a dead interface.
- `exception/StateNotFoundException.java`. Section 5.5 replaces it with
  `UserFacingException` carrying a localized message.
- `ProfileMenu.java` as production code. Ported only as
  `examples/ProfileExampleMenu` on an in-memory fake service, per section 1.

## Open questions

### Preset preference storage

Section 5.1 says the guild and user lookups go through `PresetPreferences` with
an in-memory default implementation, so the template does not force a database.
The template does have Hibernate and a repository layer, but no migration
infrastructure for a new table.

**Decision.** Ship only the in-memory implementation, since that is what section
5.1 specifies. Question for the maintainer: should a Hibernate-backed
`PresetPreferences` be added later?

### Hot reload default

Section 5.1 calls preset hot reload *optional*. Assumption: enabled by default
when the preset directory exists, disabled when it does not, controlled by a
boolean in a `MenuConfig` value object.

### Simple-menu naming

Section 5.2 shows the entry point as `Menus.simple("help")`. `Menus` collides
with nothing in JDA 6, so the name is kept as specified.

### `Ack` naming

Section 3 gives the enum values as `DEFER_EDIT`, `MODAL`, `NONE`. Kept verbatim.

### Cooldown granularity

Section 5.5 says cooldown is per user *per action*. Assumed key is
`userId + ":" + menuId + ":" + action`, so two menus cannot throttle each other.