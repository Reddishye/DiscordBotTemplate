# Design decisions

Why the menu framework is shaped the way it is. One line per decision, the reason after
the dash. What the code *is* is in `docs/menus-inventory.md`; what is still open is in
`NOTES.md`.

## Dispatch and concurrency

- Every interaction is acknowledged by the router before any work runs, because Discord gives
  three seconds and a handler that loads first has already spent them.
- The owner check and the per-message claim both happen **before** the acknowledgement, so a
  rejection costs nothing and cannot leave a message claimed by a handler that never ran.
- A click names the button, not the screen, so a rejected duplicate press is swallowed with a
  deferred edit rather than an error: the first press already owns the message and will
  produce the visible result.
- Handlers run on a `MenuExecutor` supplied by the host, never on a JDA thread - the menu
  package imports `java.util.concurrent.Executor` and nothing else from the template.
- `MenuExecutor.supply` is the one sanctioned way to call a blocking service; a loader that
  blocks there is a loader the framework can wait for, because the interaction was already
  acknowledged.
- The framework exposes no global concurrency cap: `close()` releases the executor and the
  sessions, and in-flight handlers drain rather than being interrupted.
- `close()` waits for a handler's *task*, not for its future, so a handler that never completes
  cannot hold shutdown open; its message claim is released either way.
- One `catch (Exception)` remains, in `MenuRouter#submit`, because a handler that throws
  asynchronously has nowhere else to be caught and the user still needs an answer.
- A handler that throws *synchronously* is turned into a failed future by `view(...)` and the
  loader guard, so a lambda written inline cannot break the dispatcher's contract.
- Unexpected failures become one localized sentence with a short reference that ties it to
  the log; a `UserFacingException` is a failure whose message key the user is meant to read.
- `Ack` is declared per action rather than per menu, because the two answers a component
  interaction can give differ per action and a menu-wide default would have to be wrong
  somewhere.

## Navigation and sessions

- A component id is `menu:<menuId>:<action>[:<param>...]`, decoded with `indexOf` and
  `substring` only - `String#split` compiles a regular expression and runs on every
  interaction.
- `nav` and `page` are registered for every menu, so a subclass or a simple-menu action that
  redeclares one would replace the built-in behaviour with its own.
- The view being left is read **before** the new one renders and pushed **after** it has been
  shown, so a view that fails to render leaves no history entry for a screen the user never saw.
- A Back pressed while a slow view is still on its way finds an empty stack and lands on home,
  which is the honest outcome: the per-message guard means the interaction cannot be in flight
  at the same moment.
- A menu with several views overrides `currentView`, because the default would record the
  action that was pressed and Back would render an action the menu does not recognise.
- `refresh(ctx)` renders `ctx.at(currentView(ctx))` rather than the context as it stands,
  because a handler is reached through an action that is usually not a view - a submission is
  `save_birth`, not a screen.
- Sessions hang off the message id, not off the event, so state survives between clicks and
  two users looking at two messages never share any.
- An interaction with no message gets a detached session whose state is discarded, because
  there is nothing to hang it on; writing to it is harmless.
- Session state is read with `findSession` semantics and written with `session` semantics:
  a read must not create a session for a message that merely displayed a view, and the first
  press on a fresh message must still be able to write.
- The session's history stack is depth-bounded, so a menu that pushes in a loop cannot grow a
  session without limit.
- Pagination state is stored per pager id and read back through the same session helpers, so a
  page survives every redraw the framework performs.
- `MessageFormat` was rejected for placeholder substitution: its quoting rules would put
  apostrophes in a user's name into the wrong place in six languages.

## Presets

- A preset file is JSON only; YAML would add a module and a second thing to validate.
- Preset files are parsed with Jackson's tree model, not data binding, because a preset is
  user-editable input and every unknown key has to be *reported* rather than silently ignored.
- `extends` is resolved depth-first with a cycle report that names every file in the cycle, and
  a parent that failed to load is distinguished from one that does not exist, because the two
  need different messages.
- A preset that fails to load leaves the last good copy in place: a bot must not stop rendering
  because someone saved a broken file.
- Hot reload is on when a preset directory exists and off when it does not, gated by a boolean
  an operator can turn off without deleting files.
- The built-in `default` preset is built by overriding nothing, so it and the override
  mechanism cannot drift apart.
- `Palette` holds raw 24-bit RGB values rather than colours JDA understands, because Discord
  decides how they look against a user's theme and a preset is a hint.
- Icons are semantic (`IconKey.OK`, not a glyph) and the preset maps them to text, so one bot
  can be restyled without a code change and a monochrome theme can render the same menu.
- Emoji in JSON are `\uXXXX` escapes, so the preset files stay ASCII and the encoding test is
  meaningful.
- `Presets.all()` sorts once per reload rather than once per call, because it is read on every
  render.
- `PresetDependenciesTest`-style checks keep the preset package free of the rest of the menu
  code: a preset must be loadable with nothing else on the classpath.

## Internationalization

- The JVM default locale cannot leak in: the requested locale is the only one consulted, and
  the chain is exact tag, then bare language, then English.
- A missing key renders as the key, which is loud on purpose - a missing translation should be
  visible rather than an empty gap.
- User-facing text is a `String` resolved through the context, never a key held by a component,
  so the same declared view renders in two languages.
- Developer-facing text - log messages, exception messages, Javadoc - is English and is not
  translated, because it is read by whoever is debugging rather than by a user.
- Message keys are constants in `MessageKeys` rather than strings at the call site, so a
  renamed key is a compile error instead of a key that renders as itself.
- Argument substitution is `String.formatted`, not `MessageFormat`, for the apostrophe reason
  above.

## Components

- A component names what it *means* (`ActionButton.danger`) and the preset decides how that
  looks, so a monochrome theme renders the same menu without any component knowing.
- A button with no icon renders with no icon: there is no default glyph, because a default is a
  hardcoded emoji every bot would ship and no preset could remove.
- A field with no action renders as a `TextDisplay` rather than a section with a button, so the
  view layer never emits a custom id that resolves to nothing.
- `Nav` targets a view by action name rather than by a lambda, because the target has to
  survive in a component id that a client can send back.
- `Pager` stores its page in the session and renders one page of items plus, when there is more
  than one page, three buttons: previous, the indicator, next.
- `Confirm` renders one prompt with two actions rather than a modal, because a modal has to be
  the only answer and a confirmation has to leave the message on screen.
- `SelectMenu.option(value, label)` takes the value first, which is the reverse of JDA's own
  `addOption`: the value is what a handler receives.
- A select's `selected` values are not cross-checked against its `range`; Discord rejects an
  inconsistent pair and the component drops what it can rather than guessing.
- `ModalForm` is the one mutable type in the framework, because a form is configured in steps
  and built once.
- `Limits` holds the numbers read from the JDA version rather than from Discord's
  documentation, because the documentation does not say what the current version enforces.

## Testing approach

- Every handler runs on a deterministic executor in tests, and the stress class is the one place
  that uses the real pools: ordering cannot be asserted on a real pool, and nothing about
  concurrency can be observed on a fake one.
- Interaction events are mocked, and every test takes component ids from a rendered view rather
  than writing them by hand, because an id written by hand proves nothing about the menu that
  produced it.
- `NoHardcodedUserTextTest` exists because the framework's own strings are the ones a user
  reads; the exemption is exactly one package, pinned by a test that also asserts an exempt
  file really does contain literals.
- `NoBlockingCallsTest` has exactly one exempt file, the fake service that blocks on purpose;
  an exemption a package can grow into is not an exemption.
- `ViewEditorIsTheOnlyEditPathTest` exists because two edits of one message is the failure mode
  that produces duplicate messages in a real bot and is invisible in a unit test.
- `AsciiSourcesTest` exists because Java sources are kept ASCII, which is what makes the emoji
  escapes in preset files and the encoding test meaningful.
- `SourceStyleTest`, `ApiJavadocTest` and `ClassSizeTest` turn style disagreements into tests,
  because a rule that lives only in a reviewer's head is a rule that gets broken on a busy day.
- `ClassSizeTest`'s exception list is checked for staleness in both directions, so a class that
  was split has to have its exception removed.
- The stress class is tagged and excluded from the default build: a test that measures
  wall-clock percentiles should not fail a build for being slow.

## JDA facts that differed from expectations

- Upgraded from `6.0.0-rc.3` to `6.5.0`: components v2 is the only way to render a container,
  and the release candidate's API differs from the release's.
- `Components` has no v2 convenience factories, so the framework builds containers itself.
- JDA does not validate emoji on the way in, so the preset package validates them instead.
- A modal must be the first and only response to an interaction, which is why a modal action
  declares `Ack.MODAL` and why a modal cannot follow a deferral.
- There is no single JDA interface exposing both `deferEdit` and `deferReply`, so ephemeral
  follow-ups after an edit go through the hook.
- A `StringSelectInteractionEvent` has no session of its own, which is why every later kind of
  interaction gets its context from the router rather than from the event.
- JDA 6.5.0 has no user locale on `User`; it is on `Member` and the package is not where it
  looks, so `Locales` resolves it in one place.
- `ButtonInteractionEvent.getMessage()` can be null for a message with no interaction metadata,
  which is how a shared channel message differs from a personal one.

## Features

- A bot adds a `BotFeature` instead of editing `Listeners`, `Services` or
  `TemplateBindings`, because those files are the template's own contribution and a
  fork that patches them cannot take an upstream change.
- Features are found with `ServiceLoader`, one class name per line in
  `META-INF/services/es.redactado.feature.BotFeature`, so a jar can contribute a feature
  without this repository knowing the jar exists. `TemplateBindings` is installed by
  `BotModule` and is not listed there.
- `BotFeature.configure` opens an empty Guice set for every contribution kind before
  `contribute()` runs. An empty bot still injects `Set<BaseSlashCommand>` and the rest;
  a missing binder would fail startup for a feature that simply had nothing to add.
- The template's listeners and services stay in the static lists. Features append, and
  `FeatureCatalog` drops a class that appears twice. Replacing the lists would have
  made the template's own tests depend on a service file.
- Infrastructure services start before the gateway, business services after the first
  ready event. The split is the same one `Services` already had: a service that needs
  `ShardManager` cannot start in the first wave, because the shard manager is built
  from the injector that is still starting those services.
- A feature's settings are a separate YAML file next to `config.yml`. Folding every
  bot's fields into `ConfigFile` would make the core record a grab bag, and a feature
  in another jar could not add a field to it.
- Environment variables overlay the loaded config in memory and are never written
  back. A Compose file can set `BOT_TOKEN` without the container rewriting the mounted
  file, and a blank variable must not wipe a value that was set on purpose.
- SQL migrations are a small runner rather than Flyway. Flyway Community has no SQLite
  module, and SQLite is the database a checkout uses before anyone provisions MariaDB.
  Each dialect has its own scripts because the DDL is not the same.
- A migration version is unique per dialect across every feature. Applying two scripts
  with the same number would depend on classpath order, which is not an order anyone
  wrote down.
- `bot.shards` defaults to 1 and `setShardsTotal` is called only when the value is
  higher. One shard is the right process until Discord requires more, and the template
  does not guess a count from the gateway.

## Environment

- Java 27 toolchain, Gradle 9.8.0 through the wrapper, JDA 6.5.0, Jackson Databind 2.19.1 at the
  version JDA already resolves.
- Build command: `./gradlew clean spotlessApply build`. Spotless is part of it because the
  format is checked rather than discussed.
- The menu package must not import `es.redactado.service`; it receives executors and nothing
  else. `MenuDependencyTest` enforces it.

## Kept on purpose

These looked like dead code and are not, with the reason each stays.

- `ActionTable`'s count accessors (`buttonCount`, `selectCount`, `modalCount`) - one family on a
  public type, and a caller registering a menu wants to know what it registered.
- `Session.MAX_DEPTH` - a bound on a stack a menu author cannot otherwise bound.
- `Limits` in full - a limit constant is a documented fact about Discord, not an unused method.
- `ValidationResult.warnings()` - distinct from errors because a container can be legal and
  close to a limit, and a caller logging the difference is the point of a warning.
- `ViewComponents` that this project does not use but a bot will, such as `Gallery`: the
  component set is a library, and a library that only holds what its first caller needed is a
  library with an arbitrary edge.