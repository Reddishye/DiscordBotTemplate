# Discord Bot Template

JDA 6 bot template. Guice, Hibernate, and a menu framework.

## Contents

1. [Overview](#overview)
2. [Run](#run)
3. [Architecture](#architecture)
4. [Menus](#menus)
5. [Building a menu](#building-a-menu)
6. [Simple menus](#simple-menus)
7. [Components](#components)
8. [Presets](#presets)
9. [Strings](#strings)
10. [Opening a menu](#opening-a-menu)
11. [Concurrency](#concurrency)
12. [Tests](#tests)

## Overview

| | |
| --- | --- |
| Java | 27 |
| Gradle | 9.8.0 (wrapper) |
| JDA | 6.5.0 |

```
src/main/java/es/redactado/
  Main.java          startup, shard manager, shutdown
  BotModule.java     Guice bindings
  command/           command types and dispatch
  config/            config.yml loading, TemplateBindings
  database/          DatabaseManager, entities
  feature/           BotFeature
  service/           IService, TaskManager, MenuService
  menu/              menu framework
config.example.yml   config.yml fields and environment variable names
docs/
  configuration.md
  features.md
  database.md
  menus-inventory.md
  manual-test.md
```

| Topic | Document |
| --- | --- |
| `config.yml` | `docs/configuration.md` |
| Commands, listeners, services, entities | `docs/features.md` |
| Tables and queries | `docs/database.md` |
| Menu API | `docs/menus-inventory.md` |
| Manual checks | `docs/manual-test.md` |

`config.yml` is written on first start. Registrations go in `TemplateBindings`.

## Run

```
./gradlew run
./gradlew clean spotlessApply build
./gradlew test -PexcludeTags=filesystem
./gradlew test -PrunStress
```

`build` excludes tests tagged `stress`. `filesystem` tests are included unless excluded.

Startup loads `config.yml`, builds the shard manager and the Guice injector, starts services, registers listeners, and installs a shutdown hook.

## Architecture

`TaskManager` pools:

| Pool | Size | Work |
| --- | --- | --- |
| `io` | one thread per task | database, HTTP |
| `cpu` | `availableProcessors()` | CPU work, bounded queue, abort policy |
| timer | 2 | session drain, preset watcher |

`ioExecutor()` and `cpuExecutor()` return `Executor`. Both throw `IllegalStateException` before `init()`.

`DatabaseManager` uses the `database` section of `config.yml`. See `docs/database.md`.

`BotModule` binds `Main`, `ShardManager`, `BotConfig`, and `DatabaseManager`, and installs `TemplateBindings` plus every `BotFeature` listed in `META-INF/services/es.redactado.feature.BotFeature`.

## Menus

`MenuListener` passes component, modal, and select events to `MenuRouter`.

1. Decode `menu:<menuId>:<action>[:<param>...]`. An id that does not decode returns `false`.
2. Owner check. A personal menu is bound to the user on the message interaction metadata. A channel message has no owner. A non-owner gets one localized reply.
3. Claim the message. A second interaction on the same message is dropped.
4. Acknowledge: `deferEdit`, `deferReply`, or nothing, per the action.
5. Run the handler on `MenuExecutor`.
6. Resolve the preset: menu override, guild, user, then the registry default.
7. Redraw through `ViewEditor`.

Discord allows 3 seconds to acknowledge. The handler runs after the acknowledgement. A loader must not run before it. `loadTimeout` sends a localized message.

A modal must be the first response. An action that opens one uses `Ack.MODAL`. `Click.modal` requires `opensModal()` on that button.

Session state is stored by message id. Limits: maximum count, idle time, navigation stack depth. An expired session returns one localized sentence and the home view.

## Building a menu

`render` switches on the action. Unknown actions return before the loader runs.

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

`currentView` returns the screen, not the button that was pressed. `refresh(ctx)` renders `ctx.at(currentView(ctx))`.

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

Read session state with `sessionStateOr`. Write it with `putSessionState`. The first press creates the session.

```java
private static java.util.concurrent.CompletableFuture<Void> increment(
        es.redactado.menu.simple.Click click) {
    click.putSessionState(COUNT, click.sessionStateOr(COUNT, Integer.class, 0) + 1);
    return click.refresh();
}
```

`view(ctx, loader, renderer)` runs the loader, applies `loadTimeout`, then renders. The renderer takes the context and the loaded value. It does not do I/O.

A blocking call goes through `MenuExecutor.supply`.

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

Writes go through `invalidateAfter`. The cache entry is dropped after the write finishes, including when the write fails.

```java
private CompletableFuture<Void> write(
        MenuContext ctx, java.util.function.Supplier<Object> write) {
    long key = keyOf(ctx);
    return cache.invalidateAfter(executor.supply(write), key)
            .thenCompose(ignored -> refresh(ctx));
}
```

`UserFacingException` carries a message key. The router sends that text in the user's language. Other exceptions become one generic sentence plus a reference code. The stack trace stays in the log.

Parse component-id parameters with `ctx.requireLong` and the other `require*` methods.

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

Component ids are at most 100 characters. `nav` and `page` are registered on every menu. Declaring either again fails.

## Simple menus

`Menus.simple` returns a `Menu`. Routing, owner check, sessions, presets, and translations still apply.

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

## Components

| Type | Role | Limit |
| --- | --- | --- |
| `MenuBuilder` | Builds a container. `build` validates it | 25 children. Warning at 20 |
| `Text` | One line | Discord message length |
| `Header` | Title, optional subtitle | A preset can drop the subtitle |
| `Divider` | Rule or blank line | A preset can draw neither |
| `Field` | Label and value | Caller supplies both, already localized |
| `Row` | Buttons, or one select | 5 items. A select is alone in the row |
| `ActionButton` | Runs one action | 80 character label |
| `LinkButton` | Opens a URL | 80 character label |
| `Nav` | Push, replace, or back | Target is an action name |
| `Pager` | Paged text list | id `[a-z0-9_]{1,20}`, page 1–20 |
| `Confirm` | Prompt with two actions | Own row |
| `SelectMenu` | String select | 25 options. Value, label, description, placeholder: 100 characters |
| `ModalForm` | Labelled inputs | 5 fields. Title and label 45. Field id and placeholder 100. Value 4000 |
| `Section` | Text plus accessory | Text 4000 |
| `Gallery` | Media items | 10 |
| `Looks` | Icons and text for a preset | — |

`SelectMenu.option(value, label)` takes the value first.

## Presets

A preset sets colours, icons, density, header, and footer. A menu sets a tone. The preset maps the tone to a colour.

| Preset | Header | Subtitle | Icons | Divider | Density | Footer |
| --- | --- | --- | --- | --- | --- | --- |
| `default` | `###` | no | yes | drawn | normal | none |
| `minimal` | `###` | no | none | not drawn | compact | none |
| `midnight` | `##` | yes | yes | drawn, large gap | normal | menu name |
| `vibrant` | `#` | yes | yes, large | drawn, large gap | comfortable | none |
| `monochrome` | `##` | no | greyscale | drawn | normal | menu name |

`minimal` has no emoji. A button with no icon uses its label.

Custom presets are JSON files in `menu.presetsDirectory` (`config.yml`). Example: `docs/presets/ocean.json`.

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

Every key is optional. `extends` is resolved depth-first. A cycle names every file in it. An unknown key is an error. A file that fails to parse leaves the previous copy loaded.

Resolution order:

1. `presetName()` on the menu
2. Guild choice
3. User choice, if `menu.userPresetsEnabled` is true
4. `menu.defaultPreset`

A name that does not resolve is skipped. Guild and user choices are stored in `preset_preference`. The preset directory is watched. A bad reload keeps the previous copy.

## Strings

Files: `src/main/resources/menu/messages.properties` (English), `messages_es.properties` (Spanish).

Add a locale: copy `messages.properties` to `messages_<tag>.properties` and translate the values. Lookup order: exact tag, language, English. The JVM default locale is not used.

Add a key: add it to `messages.properties` and add a constant in `MessageKeys`. A missing key renders as the key. Log messages, exception messages, and Javadoc stay English.

## Opening a menu

`MenuService.open`. Register the menu once. `register` accepts the same id again and rejects a different menu with that id.

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

The third argument of `open` is ephemeral. Use `true` for a menu bound to one user.

Move between views with `MenuContext.navigate`:

```java
return ctx.navigate(NavigationMode.PUSH, new NavEntry(ID, view, List.of()));
```

`ChannelPanels.publish` stores one message id per guild, channel, and menu, then edits that message. Render the container, then pass it in. A deleted message is sent again and the stored id is replaced. A channel message has no owner.

## Concurrency

| Work | Thread |
| --- | --- |
| Acknowledgement, owner check, id decode, message claim | JDA thread |
| Handler | `MenuExecutor` |
| Blocking call | `MenuExecutor.supply` |
| Render | `MenuExecutor` |
| Redraw | `ViewEditor` |

Do not block a JDA thread. A renderer does not do I/O.

`MenuConcurrencyStressTest`, 20 runs, one machine:

| Scenario | Interactions | Median elapsed | Median rate | p50 | p99 |
| --- | --- | --- | --- | --- | --- |
| 200 messages × 50 sequential clicks | 10000 | 3237 ms | 3182/s | 22 ms | 179 ms |
| 200 messages pressed twice at once | 400 | 57 ms | 6984/s | 0 ms | 10 ms |
| 10000 loads over 50 keys | 10000 | 71 ms | 142753/s | 31 ms | 58 ms |
| 400 presses, router closed under load | 400 | 183 ms | 2195/s | 0 ms | 0 ms |

In the third row the service was called at most 50 times. In the first row the guard dropped some presses.

## Tests

```
src/test/java/es/redactado/
  menu/api/        context, session, message keys
  menu/core/       router, navigator, end to end
  menu/view/       components, golden renders
  menu/preset/     load, resolve, hot reload
  menu/simple/     DSL
  menu/examples/   shipped examples
  command/         ShowcaseCommand, not registered
```

Button tests read the component id from a rendered view.

| Test | Checks |
| --- | --- |
| `NoHardcodedUserTextTest` | User-facing strings come from message keys |
| `NoHardcodedColorsOrEmojiTest` | Components do not hardcode colours or emoji |
| `NoBlockingCallsTest` | Blocking calls stay in the allowed file |
| `ViewEditorIsTheOnlyEditPathTest` | Message edits go through `ViewEditor` |
| `AsciiSourcesTest` | Sources are ASCII |
| `MessageKeysTest` | Every key is used. Every locale has every key |
| `MenuDependencyTest` | `menu` does not import `es.redactado.service` |
| `SourceStyleTest` | No TODO, divider comments, filler words, or role-word type names |
| `ApiJavadocTest` | Public API and `package-info` |
| `ClassSizeTest` | Files over 300 lines are listed |
| `ReadmeSnippetsTest` | Java blocks in this file exist in the examples or tests |
| `TestStackTest` | Template layers load in order |

## Contributing

- English in code, comments, and commits.
- Java sources are ASCII. Emoji in presets use `\uXXXX`.
- Javadoc on public types in `api` and `preset`, and on public methods callers use. Not on getters, record accessors, or overrides.
- One `package-info.java` per package.
- No type named `Manager`, `Helper`, `Util`, `Utils`, or `Impl`. `TaskManager` is exempt.
- No file over 300 lines unless it is listed in `ClassSizeTest`.

```
./gradlew clean spotlessApply build
./gradlew test -PrunStress
```
