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