# NOTES

Open questions and deviations from the task plan. Every question below carries
the conservative default that will be used if no answer arrives. Nothing here
blocks work.

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

**Resolved.** Both lines were already in `HEAD`; the breakage was an
*uncommitted* local edit. Restoring `build.gradle.kts` to its committed content
was enough, so no `restore caffeine dependency` commit was needed and no empty
commit was created. The menu runtime also needs Caffeine for sessions, cooldown,
and async loading, which section 2.3 permits.

### Test infrastructure added

There was no `src/test` tree, no test dependency, and no `useJUnitPlatform()`.
Added in commit `set up test infrastructure`:

| Dependency | Version | Scope |
| --- | --- | --- |
| `org.junit:junit-bom` | 5.11.4 | `testImplementation` platform |
| `org.junit.jupiter:junit-jupiter` | 5.11.4 | `testImplementation` |
| `org.mockito:mockito-core` | 5.14.2 | `testImplementation` |
| `org.mockito:mockito-junit-jupiter` | 5.14.2 | `testImplementation` |
| `org.assertj:assertj-core` | 3.26.3 | `testImplementation` |
| `org.junit.platform:junit-platform-launcher` | from BOM | `testRuntimeOnly` |
| `net.bytebuddy:byte-buddy-agent` | 1.17.6 | `mockitoAgent` configuration |

Two JVM settings were needed, both verified on the pinned Java 21 toolchain:

- **`testRuntimeOnly("org.junit.platform:junit-platform-launcher")`** — required
  by Gradle 9. Its test executor loads the launcher reflectively, and without it
  the task fails with `Failed to load JUnit Platform`. Not a JDK issue.
- **`jvmArgs("-javaagent:${mockitoAgent.asPath}")`** — Mockito's inline mock
  maker installs Byte Buddy's agent through the JDK self-attach mechanism, which
  prints `Mockito is currently self-attaching to enable the inline-mock-maker.
  This will no longer work in future releases of the JDK` on every test JVM
  start when run on Java 21. Declaring `byte-buddy-agent` in its own
  `mockitoAgent` configuration and passing it as `-javaagent` removes the
  warning. `asPath` gives the resolved jar, so the flag stays correct across
  version changes. The configuration is separate from the test classpath so the
  agent never ships in the runtime classpath. The version is pinned to the one
  Mockito already resolves, which is `1.17.6`.

`-XX:+EnableDynamicAgentLoading` was tried first and is *not* used: it suppresses
the failure but not the warning, so the `-javaagent` route is strictly better.

Verified after the change: 4 tests, 0 failures, 0 errors, and empty
`<system-err/>`, with the test JVM running `/usr/lib/jvm/zulu-21/bin/java`.

## JDA version

### Upgraded from 6.0.0-rc.3 to 6.4.2

Commit `bump jda to 6.4.2`. `./gradlew clean build` succeeded with **zero**
source changes, so the bump is kept. JDA is the same version the source menu
system was written against.

`docs/menus-inventory.md` section 2 was re-verified in full against
`JDA-6.4.2-sources.jar`. Changes from the rc.3 pass:

| Item | 6.0.0-rc.3 | 6.4.2 |
| --- | --- | --- |
| `components.label.Label` | absent | present, `Label.of(String, LabelChildComponent)` |
| `TextInput.create` | `(id, label, style)` | `(id, style)` |
| `TextInput.MAX_LABEL_LENGTH` | 45 | removed |
| `Label.LABEL_MAX_LENGTH` | n/a | 45 |
| `Modal.Builder#addActionRow` | present, `@Deprecated @ForRemoval` | **removed** |
| `Modal.Builder` mutators | `setId`, `setTitle`, 3×`addComponents`, `addActionRow` | `setId`, `setTitle`, 3×`addComponents` |

Everything else checked out unchanged: `Container.of`, `Section.of`,
`TextDisplay.of`, `Separator.createDivider/createInvisible/create`,
`MediaGallery.of` with `MAX_ITEMS = 10`, `Thumbnail.fromUrl`,
`StringSelectMenu.create`, `Button` factories, `Message.MAX_CONTENT_LENGTH_COMPONENT_V2
= 4000`, `Section.MAX_COMPONENTS = 3`, `Modal.MAX_COMPONENTS = 5`,
`SelectMenu.OPTIONS_MAX_AMOUNT = 25`, `MessageRequest#useComponentsV2()`, and
`MessageEditRequest extends MessageRequest` (so hook actions inherit
`useComponentsV2()`).

**Answer to the question about rc.3 deprecation:** yes, in 6.0.0-rc.3
`Modal.Builder#addActionRow` is already annotated `@Deprecated`, `@ForRemoval`,
and `@ReplaceWith("addComponents(ActionRow.of(components))")`. The fallback path
described in the task plan would therefore have used a deprecated method even
had the bump been reverted. No further action needed now that 6.4.2 is in place.

### `Components` has no V2 convenience factories

There is no `Components.container(...)` on either version. The view layer uses
`Container.of`, `Section.of`, `TextDisplay.of`, `Thumbnail.fromUrl`,
`Separator.create`, and `MediaGallery.of` directly.

## Java version

Pinned to 21 in commit `pin java toolchain to 21`:

```kotlin
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
```

Confirmed by `javap`: `major version: 65`. Records, sealed hierarchies,
pattern-matching `switch`, and virtual threads are all available, so
`Executors.newVirtualThreadPerTaskExecutor()` backs `MenuExecutor` from T4 on, as
section 3 requires for Java 21 or newer.

Note that Gradle itself still runs on the host JDK (24.0.2); the toolchain only
governs compilation and test execution.

## Serialization decision

Jackson Databind `2.19.1` is resolved in `runtimeClasspath` through JDA's own
dependency, but is **not** on `compileClasspath`, so menu code cannot compile
against it without a declaration.

**Decision.** Preset files are **JSON only**, parsed with Jackson Databind,
declared as `implementation("com.fasterxml.jackson.core:jackson-databind:2.19.1")`
at the version already resolved in the project. Every mention of YAML in the task
plan is ignored: no YAML module is added and no `.yml` preset files are read.

## Architecture decisions

### Menu runtime lifecycle

Section 3 requires the executor, caches, and preset watcher to be closed from the
template's existing shutdown hook. `Main.shutdown()` already delegates to
`ServiceManager.stopAll()`, and `Main` itself must not change.

**Decision.** One `MenuRuntime implements IService` in `es.redactado.menu.core`,
registered in `Services.INFRASTRUCTURE_SERVICES`. Its `shutdown()` closes the
executor, both caches, and the watcher. Infrastructure rather than business,
because it must not depend on `ShardManager`.

### The menu listener

Registered by adding `MenuListener` to `Listeners.LISTENERS`, the template's
existing discovery mechanism, resolved through the Guice injector like
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

## Source items ported but expected to be replaced later

An earlier revision of this file listed these as "not ported". The maintainer
overruled that for T1: **every SOURCE class except `ProfileMenu` is ported first**,
and removals happen in later tasks where the surrounding design is being rewritten.
All 28 files are in the tree as of commit `rename types that clash with jda`.

| Class | Why ported anyway | Due |
| --- | --- | --- |
| `api/Renderable` | redundant next to `MenuComponent#render`, but harmless and unused | T14 cleanup |
| `api/NavigationAware` | never invoked by the source router, so it is a dead interface | T5, must become the `onEnter`/`onLeave` hook or be removed |
| `api/StateNotFoundException` | used by `MenuContext#require` | T5, replaced by `UserFacingException` |
| `view/SectionList` | keeps its page in per-event state and emits an unhandled `"noop"` custom id | T10, replaced by `Pager<T>` |
| `view/JdaSeparator` | static factory returning a raw JDA component, not a `MenuComponent` | T10, folded into `Divider` |
| `view/ThumbnailComponent` | guaranteed `ClassCastException`, see above | T10 |
| `core/AbstractMenu` | carries the `onButton` string comparison and the modal helper | T4, replaced by the dispatcher |

`ProfileMenu.java` is still excluded from production code, per section 1. It is
ported only later as `examples/ProfileExampleMenu` on an in-memory fake service.

## Rename verification (T1 commit 2)

Renamed with IDE refactoring, which updated declarations, all references, and
Javadoc: `Component` to `MenuComponent`, `Context` to `MenuContext`, `ActionRow`
to `Row`.

Clash check: extracted the simple name of every type declared in
`src/main/java/es/redactado/menu/{api,core,view}` and intersected it with all
1121 simple names of `*.java` under `net/dv8tion` in `JDA-6.4.2-sources.jar`.

**Result: zero collisions.**

```
AbstractMenu ActionButton BaseContext ComponentId ComponentLimitException Field
Gallery JdaSeparator Limits LinkButton Menu MenuBuilder MenuComponent MenuContext
MenuException MenuNotFoundException MenuRouter NavigationAction NavigationAware
NavigationMode Renderable Row SectionList StateNotFoundException Text
ThumbnailComponent ValidationResult Validator
```

Honest detail on why each rename was needed:

| Old name | Exists in JDA 6.4.2 | Reason |
| --- | --- | --- |
| `Component` | yes, `net.dv8tion.jda.api.components.Component` | real collision, avoided |
| `ActionRow` | yes, `net.dv8tion.jda.api.components.actionrow.ActionRow` | real collision, avoided |
| `Context` | no | precautionary; no JDA type of that simple name exists |

`Context` was renamed because it was specified, not because JDA forces it. The
name is clearer next to its siblings, so the rename was kept, but it was not
required.

One stale reference was fixed as part of the rename: the `MenuBuilder` Javadoc
sample still called `MenuBuilder.create("profile", ctx)` and `ActionRow.of(...)`,
neither of which existed. It now matches the real signature and uses `Row`.

## Back behaviour

Confirmed by the maintainer and implemented in **T5**, not T1:

- On a **root** menu, `Back` renders the declared root view **silently**. No
  message, no error.
- When a **session has expired** and the menu is **not** a root, the router shows
  the localized `menu.error.expired` notice and then renders the root view.

The T1 port still contains the old behaviour: `AbstractMenu#handleBack` replies
`"No previous menu."` ephemerally when the per-event back stack is empty. That is
the defect T5 removes.

## Defects found during the port

### `ActionButton`, `LinkButton`, and `ThumbnailComponent` always throw

Discovered by the T1 smoke test, not present in the known-defects list.

`MenuComponent#render` is declared as returning
`List<ContainerChildComponent>`, but JDA's component hierarchies are disjoint:

```
Button     extends ActionComponent, ActionRowChildComponent, SectionAccessoryComponent
Thumbnail  extends SectionAccessoryComponent
ContainerChildComponent extends Component
ActionRowChildComponent  extends Component
SectionAccessoryComponent extends Component
```

`Button` and `Thumbnail` are **not** `ContainerChildComponent`. All three
classes therefore end their render with a cast that can never succeed:

- `ActionButton#render` returns `List.of((ContainerChildComponent) btn)`
- `LinkButton#render` returns `List.of((ContainerChildComponent) btn)`
- `ThumbnailComponent#render` returns `List.of((ContainerChildComponent) Thumbnail.fromUrl(url))`

Verified at runtime on JDA 6.4.2:

```
java.lang.ClassCastException: class net.dv8tion.jda.internal.components.buttons.ButtonImpl
  cannot be cast to class net.dv8tion.jda.api.components.container.ContainerChildComponent
  at es.redactado.menu.view.ActionButton.render(ActionButton.java:71)

java.lang.ClassCastException: class net.dv8tion.jda.internal.components.thumbnail.ThumbnailImpl
  cannot be cast to class net.dv8tion.jda.api.components.container.ContainerChildComponent
  at es.redactado.menu.view.ThumbnailComponent.render(ThumbnailComponent.java:23)
```

**Why SOURCE never noticed.** `ProfileMenu` builds JDA buttons, rows, and
sections directly and only ever imports `Text`, `Field`, `Gallery`, and
`JdaSeparator`. `ActionButton`, `LinkButton`, and `ThumbnailComponent` are
unreachable dead code in the source bot.

**Decision.** Ported verbatim and left broken, because fixing it requires
changing the `MenuComponent#render` signature, which T1 explicitly forbids
("do not change behaviour, signatures, or logic"). The three classes are marked
as broken in `MenuBuilderTest`'s Javadoc so nobody discovers it at runtime.

**Mandatory fix, due in T10** when the view layer is rewritten to be
preset-aware. The contract has to change from
`List<ContainerChildComponent>` to something that can carry both container
children and row or accessory children, for example
`List<? extends Component>`, with `MenuBuilder` narrowing to
`ContainerChildComponent` and `Row` narrowing to `ActionRowChildComponent`.
`Row#render` already casts its children to `ActionRowChildComponent`, so it is
already written for that contract.

**Question.** Should the three broken classes be deleted in T10 instead of
fixed, given that `Row` with plain JDA buttons covers the same ground?

**Default.** Fix them, because section 5.3 lists `ActionButton`, `LinkButton`,
and `Section` as required components.

### Hardcoded emoji that must move into presets

Left in place by T1 because removing them changes rendered output:

- `SectionList` renders pagination buttons with `Emoji.fromUnicode("◀")` and
  `Emoji.fromUnicode("▶")`.
- `Field` uses the custom emoji `lucide_check` (`1521846140775960626L`) as its
  default accessory and `lucide_eraser` (`1521822427246759936L`) for the danger
  variant, hardcoded in the class.

Both violate section 5.1, which requires every emoji to come from the active
preset. Section 5.1 already names `back`, `next`, and `previous` as preset slots,
so T10 must replace these literals.

`Field`'s custom emoji ids are additionally hardcoded to one specific Discord
server's emoji, which is wrong for a template. T10 should use generic unicode
emoji from the preset instead.

## Open questions

Each entry states the question, the conservative default that will be used if no
answer arrives, and whether an answer would change the design.

### Preset preference storage

**Question.** Section 5.1 specifies `PresetPreferences` with an in-memory default
implementation. Should a Hibernate-backed implementation be added? The template
has Hibernate and a repository layer but no migration infrastructure for a new
table.

**Default.** In-memory only, exactly as section 5.1 specifies. Guild and user
preferences reset on restart.

**Would change the design if answered differently.** Yes: a persistent
implementation would replace `InMemoryPreferences` behind the same interface, and
would need a migration and a repository binding.

### Hot reload default

**Question.** Should preset hot reload be on by default?

**Default.** On when the preset directory exists, off when it does not. Gated by
a boolean in a `MenuConfig` value object so an operator can turn it off without
deleting files.

**Would change the design if answered differently.** No, it is a flag.

### Simple-menu naming

**Question.** Section 5.2 names the entry point `Menus.simple("help")`. Keep?

**Default.** Keep `Menus`. It collides with nothing in JDA 6.

**Would change the design if answered differently.** Only cosmetically.

### `Ack` naming

**Question.** Section 3 specifies `DEFER_EDIT`, `MODAL`, `NONE`. Keep?

**Default.** Keep verbatim.

**Would change the design if answered differently.** No.

### Cooldown granularity

**Question.** Section 5.5 says cooldown is per user *per action*. Does the key
include the menu id?

**Default.** Key is `userId + ":" + menuId + ":" + action`, so two menus cannot
throttle each other. Memory cost is bounded by the cache's `maximumSize`.

**Would change the design if answered differently.** Only the cache key.

### Additional questions raised while porting

**Question.** The source's `Field` component renders a button accessory whose
custom id is the literal `"noop"` when no action is supplied, and no action named
`noop` exists, so the button raises an unknown-interaction error on click. How
should a non-interactive field accessory render?

**Default.** A field with no action renders as a `TextDisplay` rather than a
`Section`, so nothing clickable is produced. No dead custom ids anywhere in the
view layer.

**Question.** Section 5.3 wants over-limit builds to "fail fast in tests and
degrade gracefully in production". How is the mode selected?

**Default.** A static `Limits.Strict` boolean, true under tests and false
otherwise, defaulting to lenient. Tests set it explicitly rather than relying on
detection, so behaviour does not depend on the environment.

**Question.** `AbstractMenu#handleBack` in the source replies in Spanish and pops
a per-event back stack that is always empty, because the stack dies with the
event. What should `Back` do on the first press of a root menu?

**Default.** Render the menu's declared root view in place, with no message. This
is what section 5.4 requires for an expired session, and reusing it avoids a
special case.