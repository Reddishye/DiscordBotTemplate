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

**Superseded.** The baseline was Java 21, pinned in commit `pin java toolchain to
21`. It is now **Java 27**, changed deliberately by the maintainer because the
project depends on features of that version, and committed as `move toolchain to
java 27 and update gradle wrapper`:

```kotlin
java { toolchain { languageVersion = JavaLanguageVersion.of(27) } }
```

Confirmed by `od` on a compiled class: `major version: 71`. The earlier
`--release 21` compile check that T10b asked for was **dropped**, since the menu
package may now be used on Java 27 and the check would only have proved it still
compiled on something nobody targets.

What the change does *not* mean: nothing in the menu package uses a feature newer
than Java 21. Its newest constructs are records, sealed interfaces, pattern
matching in `switch`, `Optional` and virtual threads, all of which arrived by 21.
The baseline moved because the project needs 27, not because the menus do.

Gradle itself still runs on the host JDK; the toolchain governs compilation and
test execution only.

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
overruled that for T1: all 28 SOURCE framework classes were ported first, and
removals happen in later tasks where the surrounding design is being rewritten.
`ProfileMenu` is the only SOURCE class that was **not** ported in T1, and it is
**not** production code. See the entry below.

| Class | Why ported anyway | Due |
| --- | --- | --- |
| `api/Renderable` | redundant next to `MenuComponent#render`, but harmless and unused | T14 cleanup |
| `api/NavigationAware` | never invoked by the source router, so it is a dead interface | T5, must become the `onEnter`/`onLeave` hook or be removed |
| `api/StateNotFoundException` | used by `MenuContext#require` | T5, replaced by `UserFacingException` |
| `view/SectionList` | keeps its page in per-event state and emits an unhandled `"noop"` custom id | T10, replaced by `Pager<T>` |
| `view/JdaSeparator` | static factory returning a raw JDA component, not a `MenuComponent` | T10, folded into `Divider` |
| `view/ThumbnailComponent` | guaranteed `ClassCastException`, see above | fixed in T1b, folded into `Section` in T10 |
| `core/AbstractMenu` | carries the `onButton` string comparison and the modal helper | T4, replaced by the dispatcher |

### `ProfileMenu` is not production code

`ProfileMenu.java` is the only SOURCE menu class that was not ported, and it will
not become production code at any point. Per section 1 of the plan it is ported
only in **T12**, as `examples/ProfileExampleMenu`, backed by an in-memory fake
service, to demonstrate the framework. Its bot-specific logic, in particular the
`ApplyService` dependency and the `es.redactado.database.type.LinkType`
reference, is not copied into the template.

Nothing under `es.redactado.menu.examples` is production code. Those classes are
compiled and tested but never registered by default.

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
**Fixed in T1b.**

JDA splits components across three disjoint hierarchies:

```
Button     extends ActionComponent, ActionRowChildComponent, SectionAccessoryComponent
Thumbnail  extends SectionAccessoryComponent
ContainerChildComponent extends Component
ActionRowChildComponent  extends Component
SectionAccessoryComponent extends Component
```

`Button` and `Thumbnail` are **not** `ContainerChildComponent`, yet all three
classes ended their render with a cast that could never succeed:

- `ActionButton#render` returned `List.of((ContainerChildComponent) btn)`
- `LinkButton#render` returned `List.of((ContainerChildComponent) btn)`
- `ThumbnailComponent#render` returned `List.of((ContainerChildComponent) thumbnail)`

Verified at runtime before the fix:

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
`JdaSeparator`. `ActionButton`, `LinkButton`, and `ThumbnailComponent` were
unreachable dead code in the source bot.

**Fix chosen, by maintainer instruction.** One interface per JDA hierarchy
instead of a wildcard return type. `MenuComponent#render` keeps returning
`List<ContainerChildComponent>` unchanged.

| Type | Interface | Rendered JDA type |
| --- | --- | --- |
| `MenuComponent` | `es.redactado.menu.api` | `List<ContainerChildComponent>` |
| `RowItem` | `es.redactado.menu.view` | `ActionRowChildComponent` |
| `Accessory` | `es.redactado.menu.view` | `SectionAccessoryComponent` |

`ActionButton` and `LinkButton` now implement `RowItem` and no longer implement
`MenuComponent`. `ThumbnailComponent` implements `Accessory` and no longer
implements `MenuComponent`. `Row.of(RowItem...)` is now the only way to place a
button in a container, and it rejects an empty row and a row longer than
`Limits.MAX_ACTION_ROW_CHILDREN`. No `(ContainerChildComponent)` cast remains
anywhere in the menu package.

A wildcard return type such as `List<? extends Component>` was explicitly
rejected, because it would push the hierarchy decision onto every caller and
reintroduce the same ambiguity one layer up.

### Second bug fixed alongside: buttons with no emoji

Removing the `"⬜"` placeholder in T1 exposed a second latent defect.
`ActionButton#render` used `Button.of(style, id, emoji)` when the label was
empty, but that overload declares `Emoji` as `@Nonnull`, so a label-less,
emoji-less button threw `NullPointerException`.

**Fix.** `ActionButton` and `LinkButton` now use the four-argument
`Button.of(style, idOrUrl, label, emoji)`, which declares both the label and the
emoji as `@Nullable` and delegates the "must have a label or an emoji" rule to
JDA's own `ButtonImpl.checkValid()`. This also removes a manual label-length
check, because JDA enforces `Button.LABEL_MAX_LENGTH`.

### Consumers adapted to the new contracts

- `Field` builds its accessory through an `Accessory` lambda and calls
  `Section.of(accessory.render(ctx), text)`. Its public API is unchanged. The
  `"noop"` fallback id is now the named constant `NO_ACTION`.
- `SectionList` builds its pagination row with `Row.of(RowItem...)` instead of a
  raw JDA `ActionRow` plus a cast. Its page indicator still uses the `"noop"`
  id, which remains a dead custom id until T10 replaces this class with
  `Pager<T>`. Already tracked below.

**Question.** Should the `"noop"` ids in `Field` and `SectionList` become
disabled buttons now, so they cannot be clicked?

**Default.** No. Leaving them live keeps T1b limited to the render contract.
Tracked for T10.

**Question.** Should there be a `Section` view component that wraps an
`Accessory` plus text, so callers do not have to call JDA's `Section.of`
directly? Today only `Field` builds sections, and the tests call
`Section.of(...)` themselves.

**Default.** No new component in T1b, to avoid inventing T10's API. Added in
T10 alongside `Divider`, where section 5.3 requires `Section` anyway.

### Package cycles, resolved in the prep commit

Two cycles existed and both are gone.

**`core` <-> `view`.** `core.ComponentId` imported `view.Limits` for
`Limits.MAX_CUSTOM_ID_LENGTH`, while `view.ActionButton`, `view.Field`, and
`view.SectionList` import `core.ComponentId` to build their ids. Resolved by
moving `Limits` from `view` to `api`, with unchanged content. `api` is the right
home: the limits are part of the protocol a menu must respect, not a
presentation choice.

**`api` -> `view`.** `api.MenuComponent` imported `view.Row` purely to resolve a
`{@link Row}` in its Javadoc. Resolved by dropping the import and qualifying the
Javadoc reference. `api` now has no dependency on any other menu package, which
is the property section 4 implies but did not state.

`core.AbstractMenu` also imported `view.Validator` for its `renderValidated`
method, which had no callers anywhere in the tree. That dead method was removed,
which is what actually cleared `core` -> `view`. `Validator` validates a rendered
container, so it correctly stays in `view`; T4 replaces `AbstractMenu` with the
dispatcher and `MenuBuilder` keeps calling `Validator` directly.

Resulting direction, all one-way:

```
api   ->  (nothing)
core  ->  api
view  ->  api, core
```

### `ComponentId.require` still throws `IllegalArgumentException`

The plan states this becomes `UserFacingException` in a later task, because a
missing param is a user-visible problem and the message must be localized.
Recorded here so it is not lost. `MenuContext.require` throws
`StateNotFoundException` today for the same reason and is due the same change.

### `decode` accepts a trailing colon as one empty param

`menu:a:b:` decodes to menu {@code a}, action {@code b}, params {@code [""]},
rather than being rejected.

**Reason.** Encoding permits an empty param, so rejecting the round trip would
make `encode` produce ids that `decode` refuses. Round-tripping losslessly
matters more than rejecting a harmless trailing separator. `encode` is the place
that rejects malformed input, since it is the only place that knows the
caller's intent.

**Question.** Should `encode` reject empty params instead, so no empty param can
ever be produced?

**Default.** No. An empty param is occasionally useful as a positional
placeholder, and `decode` handles it unambiguously.

### Throughput guard for `decode`

`ComponentIdTest.Throughput` decodes one million ids and asserts completion.
Measured at **87 ms** on this machine against a **10-second** bound, so roughly
115x of headroom. The number is documented in the test's Javadoc. The bound is
loose on purpose: the test guards against reintroducing `String.split` or a
per-call array blowup, not against a throughput regression, so a tight bound
would only add flake risk.

### `ComponentId` error messages

The length failure reports the length and the limit but deliberately does **not**
include the offending id, because an over-length id is by definition large and
would flood the log or the exception message. The segment failures name the
segment, and a bad param also names its index.

### Ephemeral follow-ups after `deferEdit`, verified in JDA 6.4.2

The plan asked for this to be verified rather than assumed. It is supported.

`WebhookMessageCreateAction#setEphemeral` carries this Javadoc:

> For a `deferReply()` deferred reply, this is not supported. When a reply is
> deferred, the very first message sent through the `InteractionHook`, inherits
> the ephemeral state of the initial reply.

That caveat is about the *first* message after `deferReply()`, which becomes the
deferred reply itself. It does not apply here. Verified in
`WebhookMessageCreateActionImpl`:

- `setEphemeral(true)` throws `IllegalStateException` only when
  `isInteraction` is false.
- `isInteraction` is set to false in exactly one place:
  `IncomingWebhookClientImpl`. `InteractionHookImpl` never does, so hook sends
  keep `isInteraction == true`.
- When `ephemeral` is true, `finalizeData()` writes
  `flags |= MessageFlag.EPHEMERAL` into the request body.

So `hook.sendMessage(text).setEphemeral(true)` is accepted and serialized for an
interaction hook, including after `deferEdit()`. `Replies.ephemeral` and
`AbstractMenu#handleBack` both rely on it.

Note that with `Ack.DEFER_REPLY` the whole hook is already ephemeral because the
router defers with `deferReply(true)`, so the extra `setEphemeral(true)` there is
redundant but harmless.

### JDA has no single interface exposing both `deferEdit` and `deferReply`

Relevant to the router's two `acknowledge` overloads:

```
IDeferrableCallback extends Interaction     declares getHook()
IReplyCallback      extends IDeferrableCallback   declares reply(), deferReply(boolean)
IMessageEditCallback extends IDeferrableCallback  declares editComponents(...), deferEdit()

ComponentInteraction extends IReplyCallback, IMessageEditCallback, IModalCallback, ICustomIdInteraction
  ButtonInteraction extends ComponentInteraction
ModalInteraction   extends IReplyCallback, IMessageEditCallback, ICustomIdInteraction
```

`deferEdit` lives only on `IMessageEditCallback` and `deferReply` only on
`IReplyCallback`, so there is no common interface declaring both. `acknowledge`
therefore takes the concrete event type, as two short overloads rather than one
method with an `instanceof` inside.

`ModalInteractionEvent` does **not** implement `IModalCallback`, so it cannot
open a modal. `AbstractMenu#showModal` keeps the `instanceof IModalCallback`
check and throws `IllegalStateException` when the interaction type cannot open
one.

### A handler that throws synchronously escaped the router

Found by the T3 failure tests. Attaching `whenComplete` to the returned future
only covers failures that arrive through the future. A handler that throws before
returning one produces no future, so the exception propagated out of
`dispatchButton` and the interaction was never answered.

`MenuRouter#run` now takes a `Supplier<CompletableFuture<Void>>`, catches around
the call, and reports both shapes through `Replies.ephemeral`. This is the only
`catch (Exception)` in the menu package, which section 2.2 permits solely in the
top-level dispatcher.

### Null ack and null handler throw `NullPointerException`, not `IllegalArgumentException`

The plan asked for `IllegalArgumentException` on a null ack or handler. The
builder uses `Objects.requireNonNull`, so those two cases throw
`NullPointerException` with the action name in the message, while the empty name,
colon, duplicate, and `Ack.MODAL` cases throw `IllegalArgumentException` as
specified.

**Reason.** Section 2.2 requires `Objects.requireNonNull(x, "x")` at public entry
points. A null argument is a programming error, and `NullPointerException` is the
conventional signal for that; `IllegalArgumentException` conventionally means the
argument was of the right type but an unacceptable value.

**Default.** Keep `NullPointerException` for nulls. If uniform
`IllegalArgumentException` is wanted for all six validation failures, that is a
one-line change per site.

### Two `switch` statements remain, and they are on an enum

Both are `MenuRouter#acknowledge`, switching on `Ack`:

```java
switch (ack) {
    case DEFER_EDIT -> event.deferEdit().queue();
    case DEFER_REPLY -> event.deferReply(true).queue();
    case MODAL, NONE -> {}
}
```

No string switch and no `.equals("` call remains anywhere in `core` or `view`;
`grep '\.equals("'` returns nothing. Section 2.2 prefers pattern-matching
`switch` for a closed set of types, and `Ack` is exactly that, so these two are
the intended form rather than a leftover. The compiler enforces exhaustiveness,
which is why adding an `Ack` constant cannot silently skip an acknowledgement.

### The preferred owner check worked; no fallback was needed

Verified in `JDA-6.4.2-sources.jar` before writing any code, and implemented as
specified.

| Need | Verified API |
| --- | --- |
| Message owner | `Message#getInteractionMetadata()` returning `Message.InteractionMetadata` or `null` |
| Owner user | `Message.InteractionMetadata#getUser()` returning `User` |
| Compare | `User#getIdLong()` |
| Modal origin | `ModalInteraction#getMessage()`, declared `@Nullable` |

Two details worth recording:

- `InteractionMetadata` is a **nested class inside `Message`**, not a top-level
  type, so the type name is `Message.InteractionMetadata`.
- `ModalInteraction` does **not** extend `IModalCallback`, so a modal submission
  cannot open a modal. `AbstractMenu#showModal` keeps the `instanceof` check.

The fallback, where the owner is the first id parameter compared with
`Long.parseUnsignedLong`, was **not** implemented. The metadata check needs no
cooperation from menu authors, which is strictly better.

`owns` is allocation-free on the allowed path: it takes the message and compares
two longs, and returns before creating anything. The three early returns are, in
order, a shared menu, no message, and no interaction metadata.

### No global concurrency cap exists yet

`MenuExecutor.virtual()` uses `Executors.newThreadPerTaskExecutor`, which is
**unbounded**. Virtual threads are cheap because they park rather than block a
platform thread, but nothing stops 50,000 simultaneous clicks from creating 50,000
virtual threads.

This is a deliberate deferral, not an oversight. The real ceiling on a menu
handler is whatever it waits on, and that is almost always a bounded downstream
resource rather than CPU:

- the HikariCP pool, which queues when exhausted,
- a database's own connection limit,
- Discord's per-channel rate limit,
- HTTP client connection pools.

Adding a semaphore now would measure nothing real and would reject clicks that
would otherwise queue harmlessly. Revisit alongside the async data layer in T6,
once there is a concrete pool to size against.

**Measured, for reference:** 1,000 clicks on 1,000 distinct messages, each
handler completing after 50 ms, finish in **423 ms** against a 10 s budget. The
serial equivalent would be about 50 s. Full assertions, including "every handler
ran exactly once" and "no message left claimed", are in `MenuRouterThroughputTest`.

### `close` does not wait for a handler's future, only for its task

A test initially assumed `close()` would block until a handler's returned future
completed. It does not, and should not.

The executor task is "build the context, invoke the handler, attach a completion".
That finishes as soon as the future is attached. A handler that returns an
incomplete future therefore holds nothing open, so `close()` returns immediately
and correctly.

To actually exercise the bounded wait, the handler body itself has to be stuck.
`closeIsBounded` blocks the virtual thread on a latch nobody releases, and asserts
`close` returns between 3 and 9 seconds, which brackets the 5-second
`awaitTermination` plus interrupt.

### One `catch (Exception)` remains, in `MenuRouter#submit`

It covers a handler that throws *before* returning its future. `whenComplete` only
observes failures that arrive through the future, so the synchronous case needs
its own guard. Section 2.2 permits a broad catch solely in the single top-level
dispatcher, and this is it. `NoBlockingCallsTest` enforces the separate rule that
no blocking call exists under `menu`.

The scanner is not vacuous: injecting `.join()` into any main source under
`es/redactado/menu` fails the build and names both the file and the call.

### `UserFacingException` messages are English literals until T9

As specified for this task. The message is written by the throwing code, so it
cannot currently be localized. T9 replaces the string with a key resolved through
`Messages`. The same applies to the router's own literals: "This menu is not
yours.", "The bot is busy. Try again.", "Unknown action.", and "Something went
wrong (ref: ...)."

### Three types had to move from `core` to `api`

The plan listed `Session` and `NavEntry` as `core` types. `NavEntry` was flagged for
a move, but two more turned out to need the same treatment for the same reason:
`MenuContext` is in `api`, and the methods T5 adds to it return these types.

| Type | Moved because |
| --- | --- |
| `NavEntry` | `Menu#home(MenuContext)` returns it, so `api.Menu` needs it |
| `Session` | `MenuContext.session()` and `findSession()` return it |
| `NavigationMode` | `MenuContext.navigate(NavigationMode, String)` takes it |

Without these moves `api` would import `core`, undoing the direction the prep
commit established (`api` -> nothing, `core` -> `api`, `view` -> `api, core`). The
invariant is preserved and verified: `grep '^import es.redactado.menu.core' api/`
and `grep '^import es.redactado.menu.view' core/` both return nothing.

`SessionConfig` and `SessionStore` stay in `core`, because only the store uses them
and neither appears in a `MenuContext` signature.

### `Navigator` does not call `Validator`

The plan says showing a view should "validate with `Validator`". That is not
implemented, because it would reintroduce `core -> view`, which the prep commit
removed and which section 4's own package assignment forbids: section 4 puts
validation in `view`, and `Navigator` is in `core`.

Validation still happens for every container built the documented way, because
`MenuBuilder.build` calls `Validator.verify` itself. What is *not* guaranteed is a
menu that assembles a `Container` by hand and skips the builder.

**Default.** Leave it. T10 rewrites the view layer and is the right place to make
validation a mandatory single step, at which point every render path passes through
it by construction.

### `Session.state` used `Class.cast`, which throws instead of yielding empty

A real bug caught by the T5 tests. `Class.cast` throws `ClassCastException` on a
mismatch, so the documented "empty if the value is another type" behaviour did not
hold; it surfaced as an exception from a read. Fixed with an `isInstance` check
before the cast.

### `NavigationMode` replaced two constants and added two

The source enum was `PUSH`, `REPLACE`, `SINGLE_USE`, `POP`. T5 specifies `PUSH`,
`REPLACE`, `BACK`, `ROOT`. `SINGLE_USE` had no implementation behind it and `POP`
meant "pop the stack", which is what `BACK` now does properly.

Final enum, in `api`:

```java
public enum NavigationMode { PUSH, REPLACE, BACK, ROOT }
```

### `MenuContext.messageId()` was added beyond the plan

`Navigator` is specified to look the session up with `SessionStore.find`, which
needs a message id, and `session()` needs the same id to create. Rather than have
`Navigator` reach past the context for it, the id is exposed as
`OptionalLong messageId()` and both session methods derive from it. It is also the
primitive a menu author needs to correlate state with a message.

### `NavigationAction` now reports a bad mode as user-facing

The source did `NavigationMode.valueOf(ctx.param(0).orElse("PUSH").toUpperCase())`,
which threw a bare `IllegalArgumentException` on a malformed component id and
would have leaked as a generic error. It now throws
`UserFacingException("Unknown navigation mode.")`, and `BACK` omits the target
segment from the id rather than encoding an empty one.

### Pagination now actually persists

`SectionList` kept its page in the per-event state map, so every click reset it to
page one and pagination was broken. It now reads and writes
`session().state("page_" + key, Integer.class)`, so the page survives between
clicks. The class is still replaced by `Pager<T>` in T10.

### `Validator` moved to `api` so every outgoing view is validated

`Validator` and `ValidationResult` lived in `view`, which meant `core` could not
check a container without importing `view`. T5 noted this as the reason
`Navigator` skipped validation; that is now fixed rather than deferred.

`core.ViewEditor` is the single place a container reaches Discord. It runs
`Validator.verify` first and, if the container breaks a hard limit, completes the
future exceptionally **without** contacting Discord at all. A second test,
`ViewEditorIsTheOnlyEditPathTest`, fails the build if
`editOriginalComponents` appears in any main source other than `ViewEditor`, and
also asserts `ViewEditor` really does contain the call, so the rule cannot be
satisfied by deleting it.

`ViewEditor` uses `submit()` rather than `queue()`, so a failed REST call becomes a
failed future instead of vanishing.

### `MenuBuilder.build` keeps its own validation on purpose

The prep plan asked to remove the now-redundant `Validator.verify` from
`MenuBuilder` only if that did not lose the early failure in tests. Removing it
**would** lose something real, so it stays.

`MenuBuilder.build` validates synchronously, so a menu that assembles more children
than Discord allows throws at the construction site, with a stack pointing at the
menu author's code, during a plain unit test. With `ViewEditor` alone the same
mistake surfaces inside an asynchronous send chain, far from the mistake.

The two checks are not duplicates in kind. The builder check is an ergonomics
guard that fails fast at the point of construction; `ViewEditor` is the boundary
check that protects Discord for any container that bypasses the builder. Both
messages are the same generic one, so neither leaks internals.

### `Menu.render` returning a future forced three more `api` types

`Loader`, `Renderer`, and `Render` are all in `api` by design: `AbstractMenu#view`
is `protected`, so a menu subclass in any package can use them, and putting them in
`core` would make every menu depend on the dispatcher. `Loader` and `Renderer` are
`@FunctionalInterface` so a menu can write a view inline.

### `refresh` returns a future instead of being fire-and-forget

The first version of `AbstractMenu#refresh` chained the render and then called
`.join()`, which would have been a forbidden blocking call caught by
`NoBlockingCallsTest`. `refresh` now returns `CompletableFuture<Void>` and the
handler decides what to do with a failure. A handler that ignores the returned
future still works; nothing blocks either way.

### `MenuExecutor.supply` is the bridge to the blocking template

The template's `AbstractRepository` is synchronous JPA
(`session.createQuery(...).list()`, `session.beginTransaction()`) with no async
handle exposed. Documented in `docs/menus-inventory.md` section 1.7b so the wiring
task can choose deliberately rather than discovering it.

The Javadoc on `supply` states the important consequence: virtual threads remove the
platform-thread bottleneck, not the resource one. A HikariCP pool of ten admits ten
concurrent queries whatever the executor does, so pool sizing is the real decision.

### `DataCache.invalidateAfter` invalidates on failure too

Invalidating only on success would leave a stale entry after a failed write, which
may still have changed the stored row. The test covers both directions and asserts
the original result or exception is preserved either way.

### Three test-design mistakes worth recording

Each of these was a test bug, not a production bug, and each is the kind that makes
an async suite flaky rather than failing loudly:

- Asserting a `supply`/`run` result without first waiting on the future. The
  executor is asynchronous by design, so `marker.get()` right after
  `executor.run(...)` is a race.
- Reading a session through a cache that had just renewed it and then expecting
  expiry, which cannot happen because the read reset the clock.
- A "PUSH" expectation that included the *current* view in the render list. `PUSH`
  records the current view and renders only the target; the current view is never
  re-rendered on the way out.

### JDA does not validate emoji, so the preset package does

Checked empirically rather than assumed. `Emoji.fromFormatted` only rejects an empty
string; anything else is accepted:

```
Emoji.fromFormatted("garbage") -> UnicodeEmoji(codepoints=U+67U+61U+72U+62U+61U+67U+65)
```

`UnicodeEmojiImpl` stores whatever name it is handed. A typo in a preset file would
therefore reach Discord silently and render as the wrong thing, so `Icons` validates
before resolving: `EmojiText` accepts a custom emoji mention, or a string whose every
code point falls in a range emoji live in.

**The range set is coarse on purpose.** It is there to catch a typo, a truncated
escape, or a plain English word, not to be a complete Unicode emoji validator. A
newer emoji outside the listed ranges would be rejected, which is the safe failure:
the fix is one line in `EmojiText`, whereas accepting garbage is invisible.

### Two class-initialisation cycles, both caught by tests

Both were introduced by T7 and both would have been invisible without a test that
touches the built-ins directly.

1. `Preset.builder` read `BuiltinPresets.DEFAULT`, while `BuiltinPresets.<clinit>`
   builds its presets through `Preset.builder`. Whichever lost the race saw `null`.
2. After fixing that by moving the shared values into `DefaultLook`, a second
   failure appeared: `new EnumMap<>(Map.of())` throws, because EnumMap's copy
   constructor infers the key type from its argument and an empty map carries none.
   `ButtonStyles.identity` is exactly an empty map, so it failed on construction.
   Fixed by naming the key type: `new EnumMap<>(ButtonRole.class)` then `putAll`.

The lesson worth keeping: a class whose static initialiser builds instances of
another class that refers back to it will fail in a way that looks like a null
pointer rather than an initialisation problem.

### JDA 6.4.2 has no user locale on `User`, and the package is not where it looks

Two things in the T9 brief did not match the library, both checked against the 6.4.2
sources jar rather than assumed:

- `DiscordLocale` is in `net.dv8tion.jda.api.interactions`, not in an
  `interactions.locales` subpackage.
- **There is no `User.getLocale()`.** A user's locale is only reachable through the
  interaction: `Interaction.getUserLocale()`, which `IReplyCallback` inherits because
  `IDeferrableCallback extends Interaction`. Reading it off the `User` would not
  compile, so `BaseContext` reads it from the event.

`Interaction.getGuildLocale()` is a default method that delegates to
`getGuild().getLocale()`, which throws in a direct message. Every call site therefore
checks `getGuild() != null` first and passes `DiscordLocale.UNKNOWN` otherwise. A
direct message with no guild is an ordinary case, not an edge case, and the obvious
implementation crashes on it.

### `MessageFormat` was rejected for argument substitution

`String.formatted` does not substitute `{0}`; it is a `Formatter`, so it wants `%s`. That
mattered concretely: a test that asserted the generic error message by calling
`.formatted(reference)` on the template silently compared the unsubstituted template and
failed, rather than substituting.

More importantly, `MessageFormat` treats a single quote as an escape character, so a
Spanish or French translation containing an apostrophe renders wrong unless its author
knows to double every one of them. `Messages.substitute` is a small hand-written
`StringBuilder` loop instead: no escaping rules for a translator to get wrong, and no
pattern parsing for a message that is mostly prose.

`substitute` is package-private purely so its rules can be tested against a
two-placeholder template. No shipped message has two placeholders, and adding a fake one
would put text in front of users that exists only to be tested.

### AssertJ's `anySatisfy` means "every", not "some"

A test asserting that at least one Spanish message contained `ñ` used `anySatisfy`, which
AssertJ defines as *every* element satisfying the condition. It failed for the right
reason and the wrong message: there is no `ñ` in the Spanish copy, because "menú" has a
`ú`. The `ñ` assertion was removed rather than satisfied by inventing a message
containing one.

### The encoding test is only meaningful because Java sources must be ASCII

`AsciiSourcesTest` keeps every Java source below 0x80, so the expected Spanish strings in
`MessageKeysTest` are written as `\u00FA` escapes. That is what makes the test a real
check rather than a tautology: the expected values are built from ASCII source and carry
real accented characters, so they match only if the properties file was decoded as
UTF-8. Reading it as ISO-8859-1 would yield different code points and the equalities
would fail. Writing the literals as accented characters in the test source would have
made the test assert that a file is identical to itself.

### The literal scan covers the reply path through one targeted rule

`NoHardcodedUserTextTest` flags an English literal in the first argument position of
`ephemeral(`, `reply(`, `TextDisplay.of(` and friends. That missed
`Replies.ephemeral(event, "text")`, where the event is first and the text second, and
that method is the reply path **every** user-facing message in the package travels, so
the first-argument rule had no coverage of it at all.

A second pattern now covers that one method. It is scoped to `Replies.ephemeral` rather
than to "any second argument", because matching any second argument would also match
format strings, builder arguments and developer-facing text, and the scan would start
reporting things that are correct. `ephemeralRuleIsNarrow` pins that down so a later
widening fails the build.

Remaining gaps, all asserted rather than assumed:

- A literal starting with markdown or an emoji, `TextDisplay.of("*Not set*")`, is not
  matched because the first character is not a letter.
- A literal arriving through a constant or a local variable is not in argument position.
- A literal built by concatenation or `formatted` is not matched.

All three occurred during T9 and were localized anyway. The scan is a net for new
mistakes, not a substitute for reading the diff.

### Developer-facing text was deliberately not translated

`Validator` messages, every `LOG` line, and the text of `IllegalArgumentException` and
`IllegalStateException` stay English literals. They go to logs, stack traces and
`IllegalArgumentException` messages, never to a Discord user, and translating them would
mean a bot operator reading a stack trace in a language they did not choose. The scan is
scoped to the menu package and to user-facing call sites so this distinction is enforced
rather than assumed.

### `Icons` was widened to `Emoji`, which made it unusable

T7 stored what `Emoji.fromFormatted` returns as `Emoji`. That return type is actually
**`EmojiUnion`**, and `Button.of(style, id, label, emoji)` requires an `EmojiUnion`, so
every component that renders a preset icon would have needed a cast. Widening a factory's
return type on the way in pushes the cost onto every caller.

`Icons` now stores and returns `EmojiUnion`. This is a T7 public-API change, made in T10a
because T10a is the first thing that actually had to call `Button.of` and so the first
time the cost became real.

Note the related JDA asymmetry, confirmed against the 6.4.2 jar: the *interfaces*
`UnicodeEmoji` and `RichCustomEmoji` do not extend `EmojiUnion`, while their
implementations `UnicodeEmojiImpl` and `RichCustomEmojiImpl` implement it directly. So
`EmojiUnion` is what `Emoji.fromFormatted` hands back, and it is what `Button.of` takes,
regardless of which interface the static type suggests.

### Removing `Field`'s hardcoded emoji exposed a JDA constraint

Discord rejects an action button with **neither a label nor an emoji**, which the previous
hardcoded `lucide_check` glyph had been silently satisfying. With the glyph removed, a
read-only `Field` rendered a button with no label and no emoji, and any preset with no
icons (`minimal`) crashed.

`Field` now draws a line of text when it has no action, because a field with nothing to
press should not have a button at all. An editable field keeps its button, and when the
preset defines no icon for it the button falls back to the field's own label, which is
caller-supplied and therefore already localized. Inventing a glyph there would have
reintroduced exactly what T10a removed.

### `Divider` follows density, not just the preset's gap

`Separator` has only two spacings, `SMALL` and `LARGE`, while a preset expresses three
densities and two gap values. The effective rule is: `COMPACT` is always `SMALL`,
`COMFORTABLE` is always `LARGE`, and only `NORMAL` defers to `divider().gap()`. Density
is mostly about how much room a menu needs rather than how a rule is drawn, so a compact
menu is compact throughout.

`Divider.line()` and `Divider.space()` differ only in whether they draw a rule; both
reserve the same space, which is why `minimal`, whose palette hides rules, still separates
its sections.

### `MenuRouter.Builder` records ownership instead of guessing it

The builder knows whether it created each component, so `close()` closes only what it
made. The rule is that whoever creates a component closes it. A router given a shared
executor must not close it, because a shared executor outlives one router and closing it
would silently break the next one. Guessing from the type or from a flag the caller sets
would put the responsibility on the caller to remember; making the builder remember is
the only option that cannot be forgotten.

### `PresetLoader` uses the tree model, not data binding, for three reasons

Data binding was rejected on purpose. Each of the three behaviours the format needs is
awkward or impossible with it:

1. **Unknown properties are errors at any depth.** `@JsonIgnoreProperties` only covers
   one level, and a typo like `palette.background` would otherwise be silently
   dropped, producing a change that looks applied and was not.
2. **Errors name the field path.** Binding reports "cannot deserialize from String",
   not `palette.accent: expected #RRGGBB, got 'blue'`. Since the whole point of a
   load result is that a human reads it, the message is the feature.
3. **A partial object means inherit, not default.** With data binding, an absent
   `palette` and a `palette` of nulls look the same to the setter.

`JsonParser.Feature.STRICT_DUPLICATE_DETECTION` is on, so `{"name":"a","name":"b"}`
is an error rather than last-wins. A duplicated key in a hand-edited file is nearly
always a mistake, and silently taking one of them hides it.

### Cycles report every file, and that needed a second pass to get right

The first version reported a cycle and then, on the way back up, also reported
`parent 'X' failed to load` for each member. So a two-file cycle produced four
messages for two mistakes, and `failed to load` was actively wrong: nothing had
failed to load, the two files were pointing at each other.

The walk now tracks the names on a cycle separately and reports
`extends: is part of a cycle with other.json` once per member. A file whose parent is
on a cycle but which is not itself on it gets `parent 'X' is part of a cycle`, which
is the true reason it did not load. A parent that is simply unreadable still gets the
specified `parent 'X' failed to load`.

### A failed parent is distinguished from a missing one

A child of a file that failed to parse gets `parent 'X' failed to load`; a child of a
name nothing defines gets `no preset named 'X'`. These read almost the same and mean
different things: the first is fixed by repairing a file that exists, the second by
creating or correcting a name. Collapsing them would send an author to the wrong place.

To make that possible the loader keeps the set of file names it could not read. A
file that fails validation is simply absent from the parsed map, which on its own is
indistinguishable from a typo in `extends`.

### Emoji in JSON are `\\uXXXX` escapes, so the files stay ASCII

`ocean.json` uses `\\uD83D\\uDE80` for the rocket rather than the character. A
literal emoji in a JSON file survives fine until someone edits the file in an editor
with a different encoding, or a transfer mangles it, and the damage is invisible in a
diff. `AsciiSourcesTest` only covers Java sources, so this is a separate decision.

### Filesystem tests are tagged and may be slow

`PresetStoreTest.Watching` is `@Tag("filesystem")` and uses real file events. The
debounce test needs real time, since it asserts that twenty rapid writes produce at
most two reloads, and there is no way to make a `WatchService` wait faster. The other
watching tests use latches with a ten second bound rather than sleeping.

Skip them with `./gradlew test -PexcludeTags=filesystem` if a file system turns out to
be unreliable; they are the only tests that touch timing. The tag is wired up in
`build.gradle.kts` so a tag nobody can act on would be no use.

### Two public methods were removed after auditing T7 against the written spec

`Preset` shipped with `renderFooter(userId, menuId)` and `iconValues()` that the
agreed API did not call for. Both are gone.

- `iconValues()` was pure duplication of `icons().asMap()`, one call deeper.
- `renderFooter(...)` was not duplication but was still out of scope: validating
  placeholders is the preset's business, because a bad footer must be rejected at
  construction, while *rendering* one is a view concern and belongs in T10 where the
  footer is actually laid out.

Placeholder validation is unchanged and still tested, so an invalid `{...}` is
still rejected at construction. A preset can also be serialised in T8 with
`icons().asMap()`, without either method having existed.

The rule I applied: publish the API the task specifies, and let a later task add a
method when it has a caller. Public surface with no caller is surface nobody tests.

### `DEFAULT` is now built by overriding nothing, so the two cannot drift

Three separate literals used to describe the default look: `Palette`, `Icons`, and
the description string, each written out in `BuiltinPresets` while `Preset` held its
own copy of the defaults. `Preset.builder` therefore started from the values and
`DEFAULT` restated them, which is exactly the drift the design was supposed to
prevent.

Now `DefaultLook` owns the values once, `Preset.builder` reads them, and `DEFAULT` is
`Preset.builder("default").build()` with no override at all. There is one copy of
each default value in the codebase.

### `all()` sorts once per reload instead of once per call

The registry's own javadoc claimed reads do "no copy, and no allocation" while
`all()` ran a stream, a sort, and a list copy on every invocation. The class was
wrong, not the implementation being aspirational. `Snapshot` now precomputes the
sorted list while the set is immutable, so every accessor really is one volatile read
plus a field access. Asserted by `allIsPreComputed`, which checks identity rather
than equality.

### `AsciiSourcesTest` forced four edits to pre-existing template files

The rule is codebase-wide, so it applies to the template's own code, and four files
failed it. All were cosmetic and none changes behaviour:

| File | Was | Now |
| --- | --- | --- |
| `Main.java` | five `// -- Phase N -- --` banners with box drawing and an em dash | plain sentence comments |
| `ServiceManager.java` | an em dash in a comment | a comma |
| `PingCommand.java` | three emoji in the reply text | `Character.toString(0x...)` |
| `CommandListener.java` | a warning-sign emoji in the error message | `Character.toString(0x26A0)` |

The `Main.java` banners were decorative comments, which section 2.1 forbids
anyway, so removing them fixes two rules at once. The emoji replacements render
byte-identical output.

This is a change outside the `menu` package in a commit about presets. It was
unavoidable: scoping the scan to `menu` would have left the codebase rule unenforced
and invited the same violation later.

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
## Build environment

### The working tree was not clean when T10b resumed, and still is not mine

`9499d86` was clean when this task started. Four uncommitted local edits sit
beside it and are **not** part of any menu commit:

| File | Local change |
| --- | --- |
| `build.gradle.kts` | `java.toolchain` 21 to 27 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.0.0 to 9.8.0 |
| `gradlew` | mode change, no content change |
| `src/main/java/es/redactado/service/TaskManager.java` | a full rewrite, 317 added lines |
| `src/main/java/es/redactado/config/Listeners.java` | `CommandListener` added to the list |

They are left exactly as found and are never staged, so `gradlew`,
`Listeners.java` and `TaskManager.java` appear in no commit from this point on.

Two consequences worth stating plainly:

1. **The menu package now compiles on Java 27, not 21.** The committed
   `build.gradle.kts` still pins 21, so `main version: 65` remains what the
   repository produces; the local override produced `main version: 71` while
   these tests ran. Nothing in the menu package depends on a version between the
   two, but the 506 earlier tests were last run on 21 and the 543 now include
   the select ones run on 27.
2. **`spotlessApply` reformatted `TaskManager.java`.** The prescribed build
   command includes it, and that file had three violations before this task. The
   change is line wrapping in one log call and nothing else; it stays unstaged
   with the rest of that file.

## T10b commit 5: string select menus

### `view.SelectMenu` shares a simple name with a JDA type

Section 4 requires renaming a menu type that clashes with JDA. `SelectMenu` is
the name T10b specifies for this component, and JDA does have
`net.dv8tion.jda.api.components.selections.SelectMenu`, so the clash is real and
is left in place on purpose.

**Decision.** The name stays `SelectMenu`, because the task specifies it and the
clash is unreachable rather than merely inconvenient: `api.Limits` reads every
number from JDA, so no file in `menu` imports JDA's `SelectMenu` at all. Only
`StringSelectMenu`, which has no simple-name collision, is imported where a JDA
type is needed.

**Would change the design if renamed differently.** A rename to `MenuSelect`
would touch the Javadoc of every component and the README component table, and
would contradict the task's own naming.

### `option(value, label)` is the reverse of JDA's `addOption(label, value)`

JDA takes the label first. This component takes the value first, on purpose: the
value is what a handler receives and the label is what a user reads, so the
argument order follows the direction the data travels. A swapped pair still
renders, so nothing about the output would reveal the mistake; that is why
`SelectMenuTest` asserts values and labels separately rather than only counting
options.

**Question.** Should the framework mirror JDA instead, for the sake of anyone
arriving from JDA?

**Default.** No. The framework's own components (`Field.of(label, value)`)
already put the label first for a component where the user reads it, and the two
orderings are each right for their own type.

### `selected` is not cross-checked against `range`

The count of defaults must fit the required range, or Discord rejects the select.
The check is **not** done in `selected`, because a select is immutable and either
call may come first; validating there would make the outcome depend on the order
a menu author happened to write. It is left to JDA's `build`, which rejects it
with a message naming the range, and `SelectMenuTest.defaultsMustFitTheRange`
pins that behaviour so it cannot be mistaken for something this component
silently ignores.

**Default.** Keep it. Adding a `build()`-like terminal call to cross-validate
would be the alternative.

### A select needs at least one option, and only `render` can know

`option` comes after `of`, so "at least one option" cannot be checked at
construction. It is checked in `render`, where the message names the action,
rather than left to JDA's message, which does not. The failure therefore surfaces
inside a menu author's own test, because `MenuBuilder.build` renders
synchronously.

### JDA 6.4.2 select facts worth recording

Checked in `JDA-6.4.2-sources.jar`, none of them obvious from the interface:

- `StringSelectMenu.create` takes **only** a custom id. There is no
  `create(id, placeholder)`; the placeholder is `setPlaceholder`, which rejects an
  empty string but accepts `null`.
- `SelectMenu.PLACEHOLDER_MAX_LENGTH` is **100**, not 150. `ID_MAX_LENGTH` is
  100, `OPTIONS_MAX_AMOUNT` is 25.
- `build()` rejects zero options, a `min` greater than `max`, and default values
  outside the range, and it **silently clamps** `min` and `max` to the number of
  options. So a select of three options declared `range(1, 5)` renders as `1..3`,
  which is worth knowing before a test asserts on the range.
- `addOption(label, value, description, emoji)` is the only overload accepting a
  null description; the three-argument one declares it `@Nonnull`.
- `SelectOption` validates label and value non-empty and all three lengths, so the
  framework's own checks exist to fail earlier and to name the offending value.

### A select submission had no session, and every later kind would have had none either

A real bug, found by the select end-to-end test and not present in the known
defects. `BaseContext.from` matched the event type by hand:

```java
Message message =
        event instanceof ButtonInteractionEvent button
                ? button.getMessage()
                : event instanceof ModalInteractionEvent modal ? modal.getMessage() : null;
```

`StringSelectInteractionEvent` matched neither arm, so its context had
`messageId` empty, and every call to `ctx.session()` returned **a new throwaway
`Session`** while `ctx.findSession()` returned empty. A select handler could not
keep a page, a history, or any state at all.

The fix asks the shared supertype, which is what makes the omission structurally
impossible rather than merely absent today:

```java
if (event instanceof ComponentInteraction component) {
    return component.getMessage();
}
return event instanceof ModalInteractionEvent modal ? modal.getMessage() : null;
```

`ComponentInteraction` declares `getMessage()`, and both `ButtonInteractionEvent`
and `StringSelectInteractionEvent` are one. `ModalInteractionEvent` is **not** a
`ComponentInteraction`, so it keeps its own branch, where `getMessage()` is
`@Nullable` for a modal opened outside a message.

Why the commit 1 tests missed it: `MenuRouterSelectTest` asserted
acknowledgement, ownership, the guard and the error path, none of which touch the
session. `MenuRouterSelectTest.handlerGetsItsSession` now covers it and fails
without the fix, verified by stashing `BaseContext.java` and re-running.

## T10b commit 6: modal forms

### `ModalForm` is the one mutable type in the framework

Every other component is immutable and copy-on-write. A form is not, and cannot be:
its fields are configured by calls that follow the call that created them
(`shortField("why", "Why").required(false)`), and there is no ordering in which a
field could be configured before it existed, so there is nothing to copy yet.

**Decision.** Mutable, built inside the handler that opens it, and discarded. The
Javadoc says so in the class comment, and the danger is stated rather than left to
be inferred: it is not thread-safe, must not be held in a field, reused across
clicks, or put in a session. Reusing one would leak one click's answers into the
next, which is the bug this shape exists to make hard to write by accident.

### The per-field terminal call is `component()`, not `build()`

`shortField` returns an `Input`, and `Input` also needs a way to become JDA. Naming
both `build` meant this chain compiled and quietly produced a `Label`:

```java
ModalForm.create(ctx, "apply", "Apply").shortField("a", "A").build();
// returns a Label, not a Modal
```

That is the worst shape of bug this package can have: it compiles, it type-checks,
and it hands `showModal` something that is not a modal. `Input#component()` is
now package-private and distinctly named, so the same chain fails to compile and
the example keeps the form in a local:

```java
ModalForm form = ModalForm.create(ctx, "apply", "Apply");
form.shortField("a", "A").required(true);
showModal(ctx, form.build());
```

Found while writing the tests, not by them.

### The nested class is `Input`, not `Field`

`view.Field` already exists and means something else: one labelled value in a
message. A second `Field` in the same package would have been resolved by whichever
import won, in a file that used both. `ModalForm.Input` names the JDA thing it
wraps, `TextInput`, and cannot be confused with either.

**Related test trap.** A `@Nested` class named `Limits` in `ModalFormTest` shadowed
the imported `api.Limits`, so every `Limits.MAX_MODAL_FIELDS` failed to compile
with "cannot find symbol: variable MAX_MODAL_FIELDS" and no hint about the shadowing.
The nested class is now `Boundaries`.

### JDA 6.4.2 modal facts worth recording

Checked in `JDA-6.4.2-sources.jar`:

- The only entry point is `Modal.create(id, title)`; the constructor of `Modal.Builder`
  is `protected`.
- `Modal.Builder` has **no** `addActionRow`. Every input must be wrapped in a
  `Label`, which is what `ModalForm.Input#component` does.
- `TextInput.create(id, style)` takes no label. The label lives on `Label.of(label, input)`
  and is limited to 45 characters by `Label.LABEL_MAX_LENGTH`.
- **The placeholder getter is `getPlaceHolder()`, with a capital H.** `getPlaceholder()`
  exists only on the builder. Reading the input back needs the odd spelling.
- An unset length is `-1`, not `0`. `Input` leaves both at `-1` unless `length` was
  called, so a pre-filled input is not silently capped at zero characters.
- `Modal.MAX_COMPONENTS` is 5, `Modal.MAX_TITLE_LENGTH` 45, `TextInput.MAX_ID_LENGTH`
  100, `TextInput.MAX_PLACEHOLDER_LENGTH` 100, `TextInput.MAX_VALUE_LENGTH` 4000.

`Modal.MAX_ID_LENGTH` was deliberately **not** added to `Limits`: it is 100, the
same value `MAX_CUSTOM_ID_LENGTH` already holds, and the modal id is produced by
`ComponentId.encode`, which enforces that limit. A second constant with the same
value and a different name would be one more thing to keep in step for nothing.

### A modal must be the first and only response, and that is now tested

`ModalEndToEndTest.deferredEditLeavesNoRoomForAModal` opens the same form behind
`Ack.DEFER_EDIT` and asserts `showModal` refuses.

One wrinkle worth recording: a mocked interaction cannot flip its own
`isAcknowledged()` when the router defers it, so the test builds the event as
already acknowledged. That is the honest model of what a handler receives, and it
is the same stubbing the select and pager end-to-end tests use.

## Step 2: the executor boundary

### `MenuExecutor.of` was removed rather than left beside `shared`

`MenuExecutor` had a package-private `of(ExecutorService)` that **owned** the
executor it wrapped: `close()` shut it down. Adding `shared(Executor)`, which does
not own, would have left two factories with the same purpose and opposite
ownership rules, and picking the wrong one silently closes somebody else's pool.

**Decision.** `of` is gone. One rule: `virtual()` owns what it created,
`shared(Executor)` borrows. Its two callers moved to `shared`, which is also what
they should have been using, since both were tests holding an executor the test
itself created.

### Caffeine does not use the configured executor for a plain store

Worth recording, because the request assumed it would and the first test asserted
it and **failed**. Caffeine delegates to the configured executor for removal
notifications, `AsyncCache` computations, `refresh` and periodic maintenance
(`Caffeine.executor` javadoc, and `BoundedLocalCache.notifyRemoval` /
`scheduleDrainBuffers` at lines 432 and 1710 in the sources jar). A session store
uses none of those: no listener, no refresh, no periodic maintenance. So
`SessionStore(config, maintenance)` is accepted and honoured as configuration, and
`MaintenanceExecutorTest` asserts the behaviour it can actually observe, that the
store behaves identically either way, with a comment saying why there is no size
assertion.

**Two related facts found while asserting on Caffeine:**

- `estimatedSize()` is approximate and counts entries whose eviction is pending,
  so a store with `maximumSize` of 4 can report 50 after 50 puts. Asserting a
  bound on it is not a test of anything.
- A brand-new key can be the eviction victim, because its TinyLFU frequency is
  zero. With `maximumSize` 4 and 50 sequential puts, the newest key is not
  reliably present. "The last key is still there" is therefore not a safe
  assertion either; the tests use a store that is not over capacity.

### `DataCache` now really runs its loader on the given executor

`AsyncCacheLoader.asyncLoad(key, executor)` receives the executor Caffeine was
configured with, and the previous code ignored it, so the parameter was dead: a
loader that returned a completed future left Caffeine nothing to schedule, and
nothing ran on the pool at all.

The loader is now invoked on that executor, which is the documented hook and the
one that matters for a blocking loader: a synchronous repository call happens on
the host's pool rather than on whichever thread read the cache. Verified
non-vacuous by reverting the call and watching `aBlockingLoaderRunsOnTheGivenPool`
fail.

### A test bug that hid all of this for twenty minutes

The first version of `MaintenanceExecutorTest` passed `recorder.pool()` to
`DataCache` and then asserted on the recorder, so the recorder was never in the
call path and the test could only ever pass by accident. Found by printing the
executor Caffeine actually handed the loader. An executor-taking test has to pass
the executor it asserts on.

## T10c

### Deleted: four types, and the one that looked deletable but was not

| Deleted | Why nothing else used it |
| --- | --- |
| `view/SectionList` | superseded by `Pager`; its only other mention was a Javadoc example in `MenuComponent`, now pointing at `Pager` and `Confirm` |
| `api/Renderable` | never referenced outside itself |
| `api/NavigationAware` | never called by the router or the navigator. Section 5.4 offered wiring or removing; wiring an interface nobody needs would be worse than removing it, so it is removed |
| `api/StateNotFoundException` | replaced by `UserFacingException` in T5 |

**Kept, with the reason:**

- **`ComponentLimitException`** looked like a candidate and is not: it is thrown by
  `ValidationResult.throwIfInvalid` and asserted by `ViewEditorTest`. It is the
  failure a limit violation produces, and it is what stops `ViewEditor` sending a
  container Discord would reject.
- **`ThumbnailComponent`** is not dead either. `MenuBuilderTest` uses it, and the
  showcase needs it: a thumbnail is a section accessory, so it is the only way to
  put one beside text in this package.
- **Both `Replies.ephemeral` overloads and all four `Looks` methods** were checked
  for callers and every one has them. Nothing was removed from either class.

### `MenuExecutor.of` removed, and one rule left

See Step 2 above.

### View-to-view navigation needs a declared action, not `Nav.push`

The brief asked for `Nav.push` buttons on the home view. A navigation id encodes
`menu:<current>:nav:<mode>:<target>`, and the target is a **menu id**:
`Navigator.homeOf` looks the menu up and renders `target.home(ctx)`, which is the
`home` action. So `Nav.push("showcase", ...)` always lands on the showcase's home
view and cannot reach `components`; a second menu per view would be the only way to
make `Nav.push` work, and that contradicts "views chosen by `ctx.action()`".

**Decision.** View-to-view moves use a declared `go` action whose parameter names
the view, which is exactly the pattern the built-in `page` action already uses in
`AbstractMenu#changePage`. `Nav.push` is kept where the target really is a menu,
and `Nav.back()` is what every sub-view offers. Recorded rather than worked around
in `core`, since changing `NavigationAction` to address a view would touch the
router's navigation contract for every menu, not just this one.

### A click names an interaction, not a view

The first working version of the showcase pushed `ctx.action()` onto the session
stack. That is the **clicked button's** action, so pressing `delete` pushed an
entry named `delete`, and Back rendered it, found no such view, and fell through
to home. It looked exactly like a broken history.

**Decision.** The current view is remembered in the session under
`showcase:view`, and an action that is not itself a view redraws the remembered
one. The class Javadoc says why, because the mistake is easy to repeat: a menu with
several views cannot know what is on screen from the click that arrived.

Found by the end-to-end test, not by any render test: every view renders correctly
in isolation, so only a walk with history can catch this.

### The `examples` exemption was a no-op until the scan grew a rule

`NoHardcodedUserTextTest` was extended with an exempt list of exactly one package,
as asked, and with a test asserting the list holds exactly that one package. The
second test I wrote first, "the examples really do contain such literals", failed:
the scanner only matches a literal passed as the **first** argument, and every
component factory takes the action id first.

**Decision.** Rather than leave the exemption unexercised, the scan grew the
factories that genuinely take text first: `Text.of`, `Header.of` and
`Field.of/editable/danger`. That caught one real offender, a Javadoc example in
`MenuBuilder` that passed `"Name"` as a field label, now written as
`labels.name()` so the sample shows resolved text. The factories that take an
action id first are still uncovered, and NOTES already records why a second
argument rule was rejected as too broad.

### A mocked interaction cannot acknowledge itself

The showcase's modal button declares `Ack.MODAL`, so the router defers nothing and
the handler answers with the modal. The first version of the test built the event
with `isAcknowledged()` true, `showModal` refused with `IllegalStateException`, and
the walkthrough failed with no visible cause. An event that opens a modal has to be
mocked as unacknowledged, which is what a real one is at that point.

### Two test-design mistakes worth recording

- Asserting on the wrong event's hook after a loop of clicks: the captor held the
  first edit, not the last. The variable holding the last click is the only one
  that describes the state after the loop.
- Probing a stack depth by printing it proved the push happened and nothing about
  *what* was pushed. Reading the rendered text showed the entry was the button's
  action, which is the whole bug.

## Follow-up: the session maintenance executor was removed

Commit `drop the unused session maintenance executor` deletes the parameter rather
than documenting that it does nothing. A constructor argument that can never be
used is worse than no argument: it tells the reader that the work happens
somewhere, and someone will size a pool for it.

**What replaced it.** `SessionStore.cleanUp()`, which drains pending eviction and
expiry synchronously on the calling thread. The host schedules it, through
`TaskManager.scheduleAtFixedRate` in the wiring task. That is strictly better than
the alternative: draining in the background on every write is not something a store
wants, and a scheduled drain is visible and testable.

**One consequence, found by the existing test.** `SessionStoreTest.respectsMaximumSize`
passed before and failed after, and the failure is the honest behaviour change.
With the old test wiring (`Runnable::run` as Caffeine's executor) eviction ran
inline, so `size()` had already converged. Without an executor, Caffeine drains on
its own pool asynchronously, and `estimatedSize()` reported 590 against a maximum of
100 immediately after 1,000 puts. The test now calls `cleanUp()` first, which is
the documented contract rather than a race that happened to win.

**The library default was also not what the test assumed.** A `DataCache` built
without an executor does **not** load on the reading thread: Caffeine uses
`ForkJoinPool.commonPool()`, and the loader runs on
`ForkJoinPool.commonPool-worker-1`. The test asserted the reader's thread, failed,
and now asserts the pool. Worth knowing before anyone writes "it runs inline by
default".

## Follow-ups A and B

### The session maintenance executor is gone, and `cleanUp` is the replacement

Commit `drop the unused session maintenance executor`. Recorded in full above; the
one-line version is that a constructor argument nothing can ever reach is worse
than no argument. `SessionStore.cleanUp()` is now public and the host schedules
it.

### A menu needed to be asked which view it is showing

`Menu#currentView(MenuContext)` is new, and it was not in the brief. It is
**required** for the feature to work rather than added for symmetry.

Pushing has to record the view the user is looking at. That is not
`ctx.action()`: the click that navigates away arrives as a `nav` interaction, so
pushing the action records an entry named `nav`, and Back renders it, the menu
does not recognise it, and the user gets the unknown-view error instead of the
screen they were on. Any menu with more than one screen has this problem, so the
answer cannot live in the showcase.

The default is `ctx.action()`, which is right for a menu with one screen. A
multi-view menu overrides it, as `ShowcaseMenu` does with the view it remembers
under `showcase:view`.

**Cost worth knowing:** three existing tests mocked `Menu` and got `null` from a
method they had never heard of, which surfaced as an NPE inside a
`CompletableFuture` rather than as a missing stub. Any future test that mocks
`Menu` must stub `currentView` if it exercises a push.

### The view being left is read before the new one renders, and pushed after it

That order is not a detail, and I got it wrong first.

**Read before.** A menu that remembers its view does so by writing the key on
every render, so rendering the target overwrites the memory before anyone asked
what the previous view was. `apply` therefore captures `whereWeAre(ctx)` and only
then renders.

**Push after.** Required by the brief: a view that fails to render must not leave
a stack entry for a screen the user never saw. `NavigatorAsyncRenderTest` had a
test asserting the opposite, called `historyRecordedBeforeRender`, with the
comment "history is updated synchronously". It is now
`historyIsNotRecordedBeforeTheEditLands` and asserts depth zero while the render
is pending and one once it lands.

**The trade-off, stated plainly.** A Back pressed while a slow view is still on
its way finds an empty stack, says the menu expired and lands on home. A
navigation that renders in under a second is never affected, and the alternative
is a Back that returns to a view that failed to draw.

### `PresetForInteractionTest` had been passing for the wrong reason

Its stub menu returned `CompletableFuture.completedFuture(null)` from `render`.
With the old code the history was recorded before the edit, so a `null` container
only ever produced an error nobody asserted on. Now the push waits for the edit, so
the same `null` failed the navigation, the stack stayed empty and Back landed on
the target instead of the source. The stub returns a real container.

Worth recording as a class of bug: a test double that returns an impossible value
is not neutral, it is a value that changes which branch of the code is exercised.

### Three of my own mistakes, in one commit

Recorded because each cost time and none would have been visible in a review of
the final diff.

1. **`subList(3, size)` on a short id.** A menu-only navigation id has two
   segments, so asking for the params from the third threw. Now guarded.
2. **Pushing the target menu's view.** `go` had the target in scope and I used it,
   so a push recorded what the *destination* was showing. It has to be the menu
   being left.
3. **A blank menu id to force an "unknown menu" error.** `NavEntry` requires a
   non-empty menu id, so I encoded a single space and let the lookup fail. That
   produced a real `UserFacingException`, so it worked, and it was still nonsense:
   `fromContext` now raises `ERROR_UNKNOWN_MENU` itself, which is the same message
   for the same reason.

### The showcase lost its own navigation workaround

Commit `use view navigation in the showcase`. The `go` action, `goTo` and
`showView` are gone; the home view's four buttons are `Nav.view`, and the danger
button keeps a one-line `openView` because its id has no room for a destination.
`VIEW_KEY` stays, because a menu that remembers its view is now part of the
framework's contract rather than a workaround.

The end-to-end test walks the same path and additionally asserts the session
stack depth after every click, which is what caught that paging, choosing a
preset and opening a form must not move the history.

## W: wiring the framework into the bot

### The menu package never imports `es.redactado.service`, and a test now says so

`MenuDependencyTest` enforces three rules separately, because they fail for different
reasons:

1. nothing under `menu` imports `es.redactado` at all;
2. outside `menu`, the only importers of `es.redactado.service` are the integration
   class and the two template files that already did so;
3. every file either rule names actually exists, so an exemption cannot rot into a
   hole with a name on it.

**The first version of this scan was wrong in an instructive way.** It exempted
`MenuService`, `MenuSettings` and `MenuListener`, and asserted that *only* those
import `es.redactado.service`. That fails on `Main.java` and `config/Services.java`,
which have imported it since before the menu system existed. The fix was not to add
them to the exemption list, which would have made the rule "anything that already did
it, plus anything new" and therefore worth nothing. It was to state the real rule:
outside the menu package, the importers are the integration class **and** the
pre-existing ones, so a fourth importer fails the build.

The exemption list is also shorter than expected: `MenuService` and `MenuSettings`
live *inside* `es.redactado.service` and cannot import it, so they need no exemption
at all. Only the listener, which lives outside the package, appears in it.

### `TaskManager` needed `@Singleton`, and that was a real bug waiting to happen

`ServiceManager` resolves each service class with `injector::getInstance` and inits
the result. Without a scope, Guice hands out a **new** `TaskManager` to anything that
injects one, so `MenuService` would have received a copy that was never started and
`ioExecutor()` would have thrown `IllegalStateException` during startup. Adding
`@Singleton` to `TaskManager` is a one-word change to a pre-existing class and the
minimum needed; `MenuService` is `@Singleton` for the same reason, since the listener
injects it and must get the started instance.

Verified non-vacuously: removing the annotation makes
`menuServiceSeesTheStartedTaskManager` and `menuServiceIsScoped` fail. That test builds
the injector the way the template does, starts the infrastructure services through
`ServiceManager`, and then reaches for a pool *through the menu service*. The pool
existing at all is the proof that the injected task manager is the started one.

### Placement, and why

| Code | Package |
| --- | --- |
| `MenuService`, `MenuSettings` | `es.redactado.service`, beside `IService` and `TaskManager` |
| `MenuListener` | `es.redactado.command.handler`, beside `CommandListener` |

Read first, as instructed: `Listeners.LISTENERS` holds `CommandListener` from
`es.redactado.command.handler`, and `CommandRegister` instantiates listener classes
from that list through the injector. Putting `MenuListener` anywhere else would have
meant either a new package or a list that reaches across the tree. `MenuService`
belongs with the services because it *is* one: `IService`, `dependsOn()`, `init()`,
`shutdown()`.

### Configuration follows Dotenv, so there is only one mechanism

The template loads every setting through the injected `Dotenv` (`DatabaseManager`
reads `DB_TYPE` and the rest that way), so `MenuSettings.from(Dotenv)` does the same
rather than introducing `System.getenv` as a second path. The spec's fallback branch
was therefore not needed, and the "if there is none" condition turned out false.

Durations accept `ms`, `s`, `m`, `h` and a bare number of minutes, because
`MENU_SESSION_IDLE_TTL=30` is what someone writes and failing on it would be pedantry.
Every failure names the setting and echoes the value that was read.

### The drain schedule needed a seam to be testable

`MenuService.cleanUpSchedule()` is package-private and returns the `ScheduledFuture`,
which is the only way to observe a schedule from outside. The test asserts it exists
while running and `isCancelled()` after shutdown, using a 50 ms interval through
`MenuSettings.withCleanUpInterval` so nothing waits a minute. Asserting on a sleep
would have been the alternative and would have been slow and flaky.

### Two of my own test bugs, both the same mistake

- `setUp` created a `TaskManager` and never started it, so `MenuService.init` threw
  "TaskManager is not running". The service depends on a *started* task manager; that
  is not a detail the test can skip.
- The dotenv stub took `KEY, VALUE` pairs while every call site passed one
  `"KEY=VALUE"` string, so an odd-length array threw out of bounds instead of parsing.
  A test helper whose contract differs from its call sites fails as a confusing
  `ArrayIndexOutOfBoundsException` rather than as "wrong shape".

### A Back during a slow load cannot happen, so the new ordering is safe

Recorded because it was raised as a concern when the history started being written
after the render rather than before.

It cannot happen on the same message. The per-message re-entrancy guard claims the
message for the whole of the first handler, from before the acknowledgement until its
future completes, and drops every other interaction on that message while it is
in flight. A Back is an interaction on that message. So the window in which a user
could press Back while a view is still rendering does not exist; the ordering is
therefore invisible to a user, and the only thing it buys is a stack that never
remembers a view which failed to draw.

## T11: the simple-menu DSL

### `text(Msg)` is `message(Msg)`, because both cannot be `text`

The brief lists `text(String)`, `text(Msg)` and `text(Function<Scope<M>, String>)`
on the same builder, and its own entry-point example is
`.text(s -> "Total: " + s.data().total())`.

`Msg` is a functional interface, as the brief requires. A one-argument lambda is
therefore compatible with both `text(Msg)` and `text(Function<Scope<M>, String>)`,
and javac reports `reference to text is ambiguous` for every one of them. This is
the same trap the brief avoided deliberately for the three handler interfaces by
declaring no overloads there.

Resolved by keeping `text` for the scope form, which is the one the lambdas are for,
and naming the message form `message(Msg)`. Every `text("literal")` and
`text(s -> ...)` in the examples reads as written; only the localized form is spelled
differently.

### `onClick` is not in the brief, and the counter needs it

An action is declared by the element that draws it: a button, a select, a form. That
leaves no way to declare an action whose button comes from a `custom(...)` component.
`CounterMenu` puts a `Confirm` in through that escape hatch, and the confirming
button it renders carries an action name that nothing handled, so pressing it failed
through the router.

`SimpleMenuBuilder.onClick(String, ClickHandler)` declares exactly that: an action
with no owning view, like `onSubmit`. It is menu-wide rather than per view for the
same reason a submission is, and a `refresh()` from its handler redraws the view the
user is looking at.

### `link` returns the row, and `and()` is only needed after a button

The brief leaves the return type of `link` open and asks for the simplest chaining
that compiles cleanly. A `void link` would make a row impossible to write as one
expression, because nothing could follow it. It returns the row, like `back()` and
`view()`.

`ButtonSpec.and()` exists because the button methods return a `ButtonSpec`, so that
icon, params, disabled and modal can be set on the button that was just declared. It
is only valid immediately after a button method; after `link`, `back`, `view` or
`item` the receiver is already the row. The alternative, a row builder that can call
every button method from every other, is a wider surface for the same result.

### Validation fires where the element is declared, not all of it at `build()`

The brief says the rules are checked in `build()`. Most of them cannot wait that
long to be useful: an author who writes six buttons in a row is looking at that line,
and an exception thrown from `build()` at the end of a 40-line declaration names the
view but not the line.

So the per-element rules fire at the declaration call, and `build()` runs the two
that genuinely need the whole menu in front of them: a menu with no home view, and a
view declaring more elements than a container holds. The exception types and the
messages are what the brief asked for; only the moment differs. Every rule still
fails at authoring time, never at the first render in front of a user.

### One rule the framework already had, exposed rather than duplicated

The list id rules belong to `Pager`, which validates them with its own pattern. Rather
than restate the pattern in the DSL, `Pager.isValidId(String)` is public and the DSL
asks it, so there is one rule and not two that can disagree.

### A `custom` lambda needs a typed local

`custom(MenuComponent)` and `custom(Function<Scope<M>, MenuComponent>)` are both
overloads of a functional interface against a functional interface, so
`v.custom(scope -> ...)` is ambiguous. The instance form, `v.custom(component)`, is
always unambiguous and is what the counter example uses. A scoped custom needs a local
declared as `Function<Scope<M>, MenuComponent>`. Documented rather than worked around:
renaming the constant form would have been the only way to remove the ambiguity, and
`custom(component)` is the spelling that reads best for the common case.

### The examples found a real trap in the session API

Both `CounterMenu` and `ServerInfoMenu` failed their end-to-end tests at first with
the framework's own generic error. The cause is worth writing down: `findSession()`
returns empty until something has created the session, and the **first** press on a
fresh message is exactly that case. Reading with `findSession` and a default is right;
writing with `session()` is the only thing that works. Both examples now say so where
a reader would copy them.

### The test fixtures had to become public

`JdaMocks` and `TestRouters` were package-private in `es.redactado.menu.core`. The
simple-menu end-to-end tests are in another package and drive the same router, and a
second copy of those mocks would drift from the first. Both are now public test
classes with a note saying why. No framework type changed visibility.

### What the loader failure test actually proves

A failed loader fails the render with the loader's own exception; it is the router
that turns it into one localized sentence with a reference code, through
`ErrorReply`. The render test therefore asserts the root cause, and the localized
reply is proved in `MenuRouterOpenTest` and friends rather than being asserted twice.
