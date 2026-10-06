# Discord Bot Template

A Discord bot built on JDA 6 with Guice for dependency injection, a Hibernate
database layer, and a menu framework for everything a user interacts with.

## Table of contents

1. [Overview](#overview)
2. [Architecture of the template](#architecture-of-the-template)
3. [Menus: how an interaction flows](#menus-how-an-interaction-flows)
4. [Building menus](#building-menus)
5. [Simple menus](#simple-menus)
6. [Components reference](#components-reference)
7. [Presets](#presets)
8. [Internationalization](#internationalization)
9. [Opening menus](#opening-menus)
10. [Performance and concurrency](#performance-and-concurrency)
11. [Testing](#testing)
12. [Contributing](#contributing)

## Overview

### What this is

The template gives you a bot that starts, connects, registers its listeners and
commands, and has a database. On top of that it ships a menu framework: containers
of components with buttons, selects and modals, laid out per guild and per user,
with translations, sessions and asynchronous loading.

It also ships four menus that exist to be read. None is registered by default:
opening a menu is your decision, and the framework's job is to make that decision
easy.

### Requirements

| Item | Version |
| --- | --- |
| Java | 27 |
| Gradle | 9.8.0, through the wrapper |
| JDA | 6.5.0 |

### Project structure

```
src/main/java/es/redactado/
  Main.java                 entry point: shard manager, injector, services,
                            listeners, shutdown hook
  BotModule.java            Guice bindings
  command/                  slash, message and user commands, and their dispatch
  config/                   config.yml loading, and TemplateBindings
  database/                 DatabaseManager, migrations, entities
  feature/                  BotFeature, the methods TemplateBindings calls
  service/                  IService, ServiceManager, TaskManager, MenuService
  menu/                     the menu framework, see "Menus" below
config.example.yml          every config.yml field, with its environment variable
src/main/resources/
  logback.xml               logging
  menu/messages.properties  English strings
  menu/messages_es.properties  Spanish strings
src/test/java/es/redactado/
  menu/                     the menu tests, including the scan tests
docs/
  configuration.md          config.yml and environment variables
  features.md               commands, listeners, services, entities
  database.md               tables and queries
  menus-inventory.md        the menu API and the architecture, in full
  manual-test.md            what to check by hand, with a real bot
```

Settings are `config.yml`. What the bot runs is a line in `TemplateBindings`.
The steps are in `docs/configuration.md`, `docs/features.md`, and
`docs/database.md`.

### Running

```
./gradlew run
```

`Main.run()` redirects standard output into the logger, loads `config.yml`, builds the
shard manager, builds the Guice injector, starts the services, registers the
listeners, and installs a shutdown hook.

### Running the tests

```
./gradlew clean spotlessApply build
```

Two kinds of test are left out of that command on purpose:

```
./gradlew test -PexcludeTags=filesystem   # WatchService tests, slow on some file systems
./gradlew test -PrunStress                # the concurrency stress class
```

## Architecture of the template

### TaskManager and its pools

`TaskManager` owns three pools, sized by its constructor:

| Pool | Size | What runs there |
| --- | --- | --- |
| `io` | one thread per task | blocking I/O: database calls, HTTP, anything that waits |
| `cpu` | `availableProcessors()` | CPU-bound work, with a bounded queue and an abort policy |
| timer | 2 | scheduled tasks: the session drain, the preset watcher |

`ioExecutor()` and `cpuExecutor()` return bare `Executor` values, not
`ExecutorService`, so a caller cannot shut down a pool it does not own. Both throw
`IllegalStateException` before `init()`, which is the same contract as the rest of
the manager: an accessor has no future to fail, so it fails at the call.

### The database layer

`DatabaseManager` opens Hibernate from the `database` section of `config.yml`.
Queries and schema are in `docs/database.md`.

### How DI scopes services

`BotModule` binds `Main`, `ShardManager`, `BotConfig` and `DatabaseManager`, and
installs `TemplateBindings` plus any `BotFeature` listed in
`META-INF/services/es.redactado.feature.BotFeature`. Injection is by constructor.

## Menus: how an interaction flows

`MenuListener` receives the component, modal and select interactions and hands each
to `MenuRouter`, which does the same seven things in the same order every time:

1. **Decode.** The component id is `menu:<menuId>:<action>[:<param>...]`. An id
   that does not decode is not ours and the router returns `false`, leaving it to
   whatever system owns it.
2. **Check the owner.** A personal menu is bound to the user whose interaction
   produced its message, read from the message's interaction metadata. A channel
   message carries no owner, which is what makes it a shared menu. A stranger gets
   one localized sentence and nothing else happens.
3. **Claim the message.** A per-message set, so two interactions on one message
   cannot both run. The second is swallowed with a deferred edit.
4. **Acknowledge.** Before any work: `deferEdit`, `deferReply` or nothing, as the
   action declared.
5. **Hand to the executor.** The handler body runs on a `MenuExecutor`, never on a
   JDA thread.
6. **Resolve the preset.** What the menu forces, then the guild's choice, then the
   user's, then the registry default.
7. **Edit through one path.** Every redraw goes through `ViewEditor`, so a message
   is never written by two things at once.

### The three-second rule

Discord gives an interaction three seconds to be answered. That is why the
acknowledgement is step 4 and the work is step 5: anything slow happens after the
answer. It is also why a menu cannot wait for a loader before acknowledging, and
why `loadTimeout` produces a localized message rather than a dropped interaction.

### Why a modal cannot follow a deferral

A modal has to be the first and only response to an interaction. Once the router
has deferred an edit, the interaction is spent and Discord will refuse the modal.
So an action that opens a modal declares `Ack.MODAL`, and the router acknowledges
nothing for it. `Click.modal` refuses to open one on a button that did not declare
`opensModal()`, because at that point the interaction is already acknowledged and
the failure would surface to the user as nothing happening.

### Sessions and expiry

State hangs off the message id, so it survives between clicks, and two users
looking at two messages never share any. Sessions are bounded three ways: a maximum
size, an idle lifetime, and a depth limit on the navigation stack. An expired
session is not an error: the user gets one localized sentence and lands on the
menu's home view.

### Why the guard drops duplicate clicks

Two handlers on one message would race on the session and on the rendered output,
so the second is dropped rather than queued: the user pressed twice, the first
press already owns the message, and the visible result is the one from the first.
A press while a redraw is in flight is the common case, which is why the guard is
per message rather than per user.

## Building menus

A menu is a container of components plus a table of actions. `AbstractMenu` gives
you the loader, the timeout, the refresh, the modal path and the edit path;
`MenuBuilder` assembles the container.

### A class-based menu, step by step

Declare the views in `render`, and switch on the action:

```java
@Override
public CompletableFuture<Container> render(MenuContext ctx) {
    if (!VIEWS.contains(ctx.action())) {
        // Before the load, not after: an id this menu does not recognise is answered
        // without touching the service, which is the whole point of the failure being a
        // localized sentence rather than a blank container.
        return unknownView(ctx);
    }
    return view(
            ctx,
            this::profileLoader,
            (context, profile) ->
                    switch (context.action()) {
                        case HOME -> home(context, profile);
                        case ROLE -> role(context, profile);
                        case LINKS -> links(context, profile);
                        case CONFIRM_REMOVE -> confirmRemove(context, profile);
                        default ->
                                throw new IllegalStateException(
                                        "Checked above, so unreachable: " + context.action());
                    });
}

/**
 * The loader: one cache read per render, shared by every view.
 *
 * <p>The key is the user, so two people opening two profiles are two keys and one person
 * opening the menu twice is one load. Nothing in a render touches the service directly.
 */
```

Declare the actions, and say how each one is acknowledged:

```java
public static ProfileExampleMenu using(FakeProfileService service) {
    MenuExecutor executor = MenuExecutor.virtual();
    DataCache<Long, Profile> cache =
            new DataCache<>(
                    DataCacheConfig.of(1_000, Duration.ofMinutes(5)),
                    // The loader is the blocking call, handed to the executor. This is the
                    // whole point of the cache's loader being a Function of a key rather
                    // than the service being called at every render.
                    key -> executor.supply(() -> service.loadBlocking(key)),
                    executor::execute);
    return new ProfileExampleMenu(service, cache, executor);
}

/**
 * The executor this menu loads and writes on.
 *
 * <p>Named apart from {@link #service} and {@link #cache} because the three are the only
 * things a handler needs and reading them in one place is worth the field.
 */
protected final MenuExecutor executor;
```

### The id format and its limit

A component id is `menu:<menuId>:<action>[:<param>...]` and may be 100 characters.
Three segments are always present, so a menu id and an action name have about 88
characters between them once params are counted. The framework refuses a name or a
set of params that could not be encoded, where it can know that statically, and
`ComponentId.encode` refuses the rest.

`nav` and `page` are registered for every menu. Declaring either is a duplicate and
is refused, because the redeclaration would silently replace the built-in
navigation with your own.

### `currentView`

A click names the button that was pressed, not the screen it was pressed on. A
menu with more than one view therefore has to say which view it is showing, or Back
would try to render an action the menu does not recognise:

```java
private static CompletableFuture<Void> push(MenuContext ctx, String view) {
    return ctx.navigate(NavigationMode.PUSH, new NavEntry(ID, view, List.of()));
}

/**
 * The view this menu is showing.
 *
 * <p>Not the action: a click names the button pressed rather than the screen it was
 * pressed on, so the default implementation would record "nav" or "ask_birth" and Back
 * would render an action this menu does not have. A role row records "role" with its
 * params, which is right, and is kept for the same reason.
 */
@Override
public NavEntry currentView(MenuContext ctx) {
    String action = ctx.action();
    String view =
            switch (action) {
                case ROLE, LINKS, CONFIRM_REMOVE -> action;
                default -> HOME;
            };
    return new NavEntry(ID, view, ctx.params());
}
```

`refresh(ctx)` does this for you: it renders `ctx.at(currentView(ctx))`, never the
context as it stands, so a handler that redraws after a submission lands on the
view the user is looking at rather than on a view named after the form.

### Session state, and the first-press trap

State is read without creating a session and written with one that exists:

```java
private static java.util.concurrent.CompletableFuture<Void> increment(
        es.redactado.menu.simple.Click click) {
    click.putSessionState(COUNT, click.sessionStateOr(COUNT, Integer.class, 0) + 1);
    return click.refresh();
}
```

A session does not exist until something creates it, and the first press on a
fresh message is exactly that case. Reading with `findSession` and writing with
`session` is the pair that survives it; `sessionStateOr` and `putSessionState` are
that pair with the decision already made.

### Asynchronous loading

`view(ctx, loader, renderer)` runs the loader, applies `loadTimeout`, and then
renders:

```java
@Override
public CompletableFuture<Container> render(MenuContext ctx) {
    if (!VIEWS.contains(ctx.action())) {
        // Before the load, not after: an id this menu does not recognise is answered
        // without touching the service, which is the whole point of the failure being a
        // localized sentence rather than a blank container.
        return unknownView(ctx);
    }
    return view(
            ctx,
            this::profileLoader,
            (context, profile) ->
                    switch (context.action()) {
                        case HOME -> home(context, profile);
                        case ROLE -> role(context, profile);
                        case LINKS -> links(context, profile);
                        case CONFIRM_REMOVE -> confirmRemove(context, profile);
                        default ->
                                throw new IllegalStateException(
                                        "Checked above, so unreachable: " + context.action());
                    });
}

/**
 * The loader: one cache read per render, shared by every view.
 *
 * <p>The key is the user, so two people opening two profiles are two keys and one person
 * opening the menu twice is one load. Nothing in a render touches the service directly.
 */
```2

The renderer is a pure function of its context and its model, so it must not do
I/O and must not block. Everything slow belongs in the loader.

### A blocking service behind a cache

The service blocks; the loader does not, because the blocking call goes to the
executor:

```java
public static ProfileExampleMenu using(FakeProfileService service) {
    MenuExecutor executor = MenuExecutor.virtual();
    DataCache<Long, Profile> cache =
            new DataCache<>(
                    DataCacheConfig.of(1_000, Duration.ofMinutes(5)),
                    // The loader is the blocking call, handed to the executor. This is the
                    // whole point of the cache's loader being a Function of a key rather
                    // than the service being called at every render.
                    key -> executor.supply(() -> service.loadBlocking(key)),
                    executor::execute);
    return new ProfileExampleMenu(service, cache, executor);
}

/**
 * The executor this menu loads and writes on.
 *
 * <p>Named apart from {@link #service} and {@link #cache} because the three are the only
 * things a handler needs and reading them in one place is worth the field.
 */
protected final MenuExecutor executor;
```2

Every write goes through `invalidateAfter`, which drops the cached entry once the
write has settled and passes the write's outcome through unchanged:

```java
private CompletableFuture<Void> write(
        MenuContext ctx, java.util.function.Supplier<Object> write) {
    long key = keyOf(ctx);
    return cache.invalidateAfter(executor.supply(write), key)
            .thenCompose(ignored -> refresh(ctx));
}
```

Invalidating before the write would leave a window in which a render reloaded the
old value and cached it again, so the redraw would show what the user had just
replaced. Invalidating on failure too is deliberate: a failed write may still have
changed what is stored.

### Errors

`UserFacingException` carries a message key the user is meant to read, and the
router replies with it in their language. Anything else becomes one generic
sentence with a short reference that ties it to the log entry. Nothing internal is
ever shown to a user.

### Parsing parameters safely

A parameter came out of a component id, which a client can edit, so parse it with
the context's own accessors:

```java
private Container role(MenuContext ctx, Profile profile) {
    // requireLong rather than a cast: the id came out of a component id, which a client
    // can edit, and a malformed one has to fail as a bad request rather than as a
    // NumberFormatException from somewhere unrelated.
    long roleId = ctx.requireLong(0);
    Profile.Role found =
            profile.roles().stream()
                    .filter(candidate -> candidate.id() == roleId)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new es.redactado.menu.api.UserFacingException(
                                            MessageKeys.ERROR_UNKNOWN_VIEW));
    return MenuBuilder.create(ID)
            .add(Text.title(found.name()))
            .add(Field.of("Permissions", Integer.toString(found.permissions())))
            .add(Text.small(found.description()))
            .add(Row.of(Nav.back()))
            .build(ctx);
}
```

`requireLong` refuses a missing or malformed value with a localized message rather
than letting a `NumberFormatException` escape from somewhere unrelated.

## Simple menus

For a menu that is content and navigation, the DSL declares it without a class.
What `build()` returns is an ordinary `Menu`, so routing, the owner check, the
guard, sessions, presets, translations and asynchronous loading are all still the
framework's.

```java
return Menus.simple(ID)
        .tone(Tone.INFO)
        .home(
                v ->
                        v.header(Msg.literal("Help"), Msg.literal("Pick a topic"))
                                .text("Everything the bot can do, in two screens.")
                                .row(
                                        r ->
                                                r.primary(
                                                                OPEN_FAQ,
                                                                "FAQ",
                                                                click -> click.go(FAQ))
                                                        .and()
                                                        .link(
                                                                "Docs",
                                                                "https://example.com/docs")))
        .view(
                FAQ,
                v ->
                        v.header(Msg.literal("Frequently asked"))
                                .text("How do I reset my profile?")
                                .text("How do I change the language?")
                                .row(r -> r.back()))
```

With a loader, a pager and a select:

```java
return Menus.simple(ID, ctx -> service.load(ctx))
        .tone(es.redactado.menu.preset.Tone.INFO)
        .loadTimeout(java.time.Duration.ofSeconds(5))
        .home(
                v ->
                        v.header(Msg.literal("Server"))
                                .text(scope -> scope.data().name())
                                .field(
                                        Msg.literal("Online"),
                                        scope -> String.valueOf(scope.data().online()))
                                .select(
                                        SECTION_ACTION,
                                        Msg.literal("Show"),
                                        o ->
                                                o.option(OVERVIEW, "Overview")
                                                        .option(MEMBERS, "Members")
                                                        .option(ROLES, "Roles")
                                                        .selected(OVERVIEW),
                                        ServerInfoMenu::show)
                                .list(
                                        "members",
                                        scope -> scope.data().members(),
                                        5,
                                        member -> member.name() + " - " + member.role())
                                .text(scope -> sectionBody(scope.ctx(), scope.data()))
                                .row(
                                        r ->
                                                r.secondary(
                                                        "ping",
                                                        "Ping",
                                                        click -> {
                                                            click.reply(
                                                                    Msg.literal("Pong"));
                                                            return click.done();
                                                        })))
```

The full reference, with the limits of every method, is in
`docs/menus-inventory.md`, section 3.6.

Three examples ship, compiled and tested, none registered by default:

| Example | Shows |
| --- | --- |
| `HelpMenu` | two static views, a link button, Back |
| `CounterMenu` | state in the session, `refresh()`, a reset behind a `Confirm` |
| `ServerInfoMenu` | a slow loader, a paged list, a select that switches the section shown |

## Components reference

| Component | Purpose | Limits |
| --- | --- | --- |
| `MenuBuilder` | assembles a container, owns the validation on `build` | 25 children, 20 before a warning |
| `Text` | a line of text | Discord's message limit |
| `Header` | a title and an optional subtitle | a preset without subtitles drops the subtitle |
| `Divider` | a rule or a blank line, per density | a preset may draw neither |
| `Field` | a labelled value | label and value are the caller's, already localized |
| `Row` | up to five items, buttons or one select | 5 items; a select must be alone |
| `ActionButton` | runs one action, in a named role | 80 character label |
| `LinkButton` | opens a URL, leaves the menu system | 80 character label |
| `Nav` | pushes, replaces or goes back, by view | targets a view by action name |
| `Pager` | a paged list of text items | id `[a-z0-9_]{1,20}`, page 1 to 20 |
| `Confirm` | a prompt with two actions | renders in a row of its own |
| `SelectMenu` | a string select | 25 options, 100 characters for value, label, description and placeholder |
| `ModalForm` | a modal of labelled inputs | 5 fields, 45 for a title and a label, 100 for a field id and a placeholder, 4000 for a value |
| `Section` | text with an accessory beside it | text up to 4000 characters |
| `Gallery` | up to ten media items | 10 items |
| `Looks` | resolves icons and text for a preset | nothing to configure |

`SelectMenu.option(value, label)` takes the value first, which is the reverse of
JDA's own `addOption`: the value is what a handler receives.

## Presets

A preset decides how a menu looks: colours, icons, density, header style and
footer. A menu names a tone, and the preset maps that tone to a colour, so no
component ever names a colour itself.

### The built-in presets

| Preset | Header | Subtitle | Icons | Divider | Density | Footer |
| --- | --- | --- | --- | --- | --- | --- |
| `default` | `###` | no | yes | drawn | normal | none |
| `minimal` | `###` | no | none at all | not drawn | compact | none |
| `midnight` | `##` | yes | yes | drawn, large gap | normal | menu name |
| `vibrant` | `#` | yes | yes, large | drawn, large gap | comfortable | none |
| `monochrome` | `##` | no | greyscale shapes | drawn | normal | menu name |

`minimal` has no emoji anywhere and still renders every button, because a button
with no icon falls back to its label rather than to a default glyph.

### Custom preset files

Put JSON files in the directory named by `MENU_PRESETS_DIR`. This one is in
`docs/presets/ocean.json`:

```json
{
  "name": "ocean",
  "extends": "midnight",
  "description": "Cool blue theme for support menus.",
  "palette": {
    "accent": "#1E90FF",
    "success": "#2ECC71"
  },
  "icons": {
    "ok": "\uD83D\uDE80",
    "back": "\u2B07\uFE0F",
    "delete": ""
  },
  "density": "comfortable",
  "divider": {
    "visible": true,
    "gap": "large"
  },
  "header": {
    "level": 2,
    "subtitle": true
  },
  "buttons": {
    "primary": "secondary",
    "danger": "danger"
  },
  "footer": "{menu}"
}
```

Every key is optional. `extends` names another preset to inherit from, and
inheritance is depth-first; a cycle is reported naming every file in it. An unknown
key is an error rather than something silently ignored, because a typo in a preset
should not look like it worked.

A preset that fails to load leaves the last good copy in place: a bot must not stop
rendering because someone saved a broken file.

### Resolution order

Four levels, most specific first:

1. what the menu itself forces, through `presetName`
2. what the guild chose
3. what the user chose, when `MENU_USER_PRESETS_ENABLED` is true
4. the registry default, which is `MENU_DEFAULT_PRESET`

A name that no longer resolves is skipped and resolution continues, so a preset
file that is renamed while the bot runs does not stop menus from rendering.

### Hot reload

The preset directory is watched. A changed file is reloaded, and the last good copy
stays in place if the new one does not parse. Preferences are in memory, so they
reset on restart; persistence is not implemented.

## Internationalization

Strings live in `src/main/resources/menu/`, one properties file per locale:
`messages.properties` for English and `messages_es.properties` for Spanish.

### Adding a locale

1. Copy `messages.properties` to `messages_<tag>.properties`, for example
   `messages_fr.properties`.
2. Translate the values. Leave the keys alone.
3. Nothing else: the chain resolves the exact tag, then the bare language, then
   English.

The JVM default locale cannot leak in. A bot whose host is set to German must not
start answering in German because of it.

### Adding a key

1. Add the key to `messages.properties`.
2. Add a constant to `MessageKeys`, so a renamed key is a compile error.
3. Use the constant in the component. A missing key renders as the key itself,
   which is loud on purpose: a missing translation should be visible rather than an
   empty gap.

User-facing text is a `String` resolved through the context, never a key held by a
component, so one declared view renders in two languages. Developer-facing text,
meaning log messages, exception messages and Javadoc, is English and is not
translated.

## Opening menus

### From an interaction

`MenuService.open` is the primitive. A command that opens the showcase:

```java
public class ShowcaseCommand implements BaseSlashCommand {

    private final MenuService menuService;

    /**
     * @param menuService the service that owns the router
     */
    @Inject
    public ShowcaseCommand(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("showcase", "Open the menu showcase")
                .setNSFW(false)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.BOT_DM);
    }

        @Override
        public boolean ephemeral() {
            return true;
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
        menuService.register(new ShowcaseMenu(menuService.presets()));
        menuService.open(event, "showcase", true);
    }
```

Register it once, not per click: `register` is idempotent per id but refuses a
duplicate, so a command that registers on every use is fine for a test bot and
wrong for a busy one. The second argument is ephemeral, which is right for a
personal menu because its buttons are bound to whoever ran the command.

### From another menu

`MenuContext.navigate` moves between menus and views and keeps the history that
Back walks:

```java
return ctx.navigate(NavigationMode.PUSH, new NavEntry(ID, view, List.of()));
```

### A shared channel panel

A channel message has no owner, so its buttons are visible to everyone.
`ChannelPanels.publish` stores one message id per guild, channel and menu, and
the next publish edits that message. Render the container first, then hand it
over. A deleted message is sent again and the stored id is replaced.

## Performance and concurrency

### The execution model

| Work | Where it runs |
| --- | --- |
| Acknowledgement | the JDA thread, immediately |
| Owner check, id decode, message claim | the JDA thread, immediately |
| The handler body | `MenuExecutor`, on `TaskManager`'s pools |
| A blocking service call | `MenuExecutor.supply` |
| Rendering | the menu executor, as a pure function of context and model |
| The redraw | `ViewEditor`, after the render completes |

### The rules

- Never block a JDA thread. A handler that blocks a JDA thread stalls every other
  event that thread owns, which in a sharded bot is a whole shard.
- Reach a blocking service through `MenuExecutor.supply`.
- A renderer does no I/O and returns without waiting.
- Sessions and caches are bounded, so a menu in a busy channel cannot grow memory
  without limit.

### Measured

From `MenuConcurrencyStressTest`, twenty consecutive runs on one machine. The
hardware is not recorded, so treat these as indicative of shape rather than as
numbers to plan against.

| Scenario | Interactions | Elapsed (median) | Rate (median) | p50 | p99 |
| --- | --- | --- | --- | --- | --- |
| 200 messages x 50 sequential clicks | 10000 | 3237 ms | 3182/s | 22 ms | 179 ms |
| 200 messages pressed twice at once | 400 | 57 ms | 6984/s | 0 ms | 10 ms |
| 10000 loads over 50 keys | 10000 | 71 ms | 142753/s | 31 ms | 58 ms |
| 400 presses, router closed under load | 400 | 183 ms | 2195/s | 0 ms | 0 ms |

The number that matters in the third row is not the rate: it is that 10000
concurrent loads over 50 keys called the service at most 50 times, which is the
stampede the cache exists to prevent. In the first row, a small number of presses
were dropped by the guard and pressed again, which is the guard working.

## Testing

### Layout

```
src/test/java/es/redactado/
  menu/api/           the context, the session, the message keys
  menu/core/          the router, the navigator, the components, end to end
  menu/view/          each component in isolation, plus golden renders
  menu/preset/        loading, resolving, hot reload
  menu/simple/        the DSL, its validation and its thread safety
  menu/examples/      the shipped examples, rendered and pressed
  command/            an example command, compiled but not registered
```

Every test that presses a button takes the component id out of a rendered view
rather than writing it by hand, so an id that a menu would never produce cannot
pass a test.

### The scan tests

These are tests about the shape of the code rather than its behaviour, and each one
exists because of something that actually went wrong:

| Test | What it protects |
| --- | --- |
| `NoHardcodedUserTextTest` | the framework's own strings are the ones a user reads. One package is exempt and the exemption is pinned by a test |
| `NoHardcodedColorsOrEmojiTest` | a component names a meaning, and the preset decides the glyph |
| `NoBlockingCallsTest` | no blocking call outside the one file that blocks on purpose |
| `ViewEditorIsTheOnlyEditPathTest` | two edits of one message, which no unit test would otherwise see |
| `AsciiSourcesTest` | sources stay ASCII, which is what makes the emoji escapes meaningful |
| `MessageKeysTest` | every key is used, every locale has every key |
| `MenuDependencyTest` | the menu package never imports the template's services |
| `SourceStyleTest` | no TODO, no divider comments, no filler words, no role-word type names |
| `ApiJavadocTest` | the public API is documented, and every package says what it is for |
| `ClassSizeTest` | no file grows past 300 lines without a written reason |
| `ReadmeSnippetsTest` | every Java block in this file exists in the code |
| `TestStackTest` | the template's own layers load in order |

## Contributing

### Style

- English only, in code, comments and commits.
- Java sources stay ASCII; emoji belong in presets as `\uXXXX` escapes.
- No filler words in comments. A sentence that says "simply" says it twice.
- Javadoc on every public type in `api` and `preset`, and on every public method
  of the classes a caller writes against. Not on getters, record accessors or
  overrides.
- One `package-info.java` per package, saying what belongs in it.
- No type named after a role rather than a thing: not `Manager`, `Helper`, `Util`
  or `Impl`. `TaskManager` is the template's own and is exempt by name.
- No file over 300 lines without a reason written in `ClassSizeTest`.

### Commits

One concern per commit, and the message says what changed and why. A commit that
also reformats the world is a commit nobody can review.

```
add simple menu foundations
make session state safe to read and write
narrow the blocking exemption
```

### Before you push

```
./gradlew clean spotlessApply build
./gradlew test -PrunStress
```
