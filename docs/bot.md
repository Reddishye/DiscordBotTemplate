# The bot

How to add a command, a listener, a service, a table, and a setting. Menus are a
separate guide: the README from "Menus: how an interaction flows", and
`docs/menus-inventory.md` for the full API.

Everything you add is a line in `src/main/java/es/redactado/config/TemplateBindings.java`.
`PingCommand` is already there. New code follows the same line.

## A slash command

`PingCommand` is the whole pattern.

1. A class implements `BaseSlashCommand`.
2. `getCommandData()` is the name and description Discord shows. The name is what
   the bot looks up when someone runs it. Two commands with the same name stop
   startup.
3. `handle` runs after the bot has already acknowledged the interaction. Send the
   reply with `event.getHook()`, as `PingCommand` does. Do not call `event.reply`.
4. Add `slashCommand(YourCommand.class)` in `TemplateBindings.contribute()`.

The class is constructed by Guice, so a constructor marked `@Inject` can take
`DatabaseManager`, `MenuService`, `BotConfig`, or another service.

`commands.scope` in `config.yml` decides where the command is registered.

| `commands.scope` | What happens |
| --- | --- |
| `GUILD` | Registered on `commands.guildId`. `0` registers nothing and logs that. |
| `GLOBAL` | Registered once, from shard 0. Discord can take up to an hour to show a new global command. |

Optional methods on `BaseSlashCommand`:

| Method | When you leave it alone | When you override it |
| --- | --- | --- |
| `cooldown()` | Uses `commands.defaultCooldown`. `0s` in config means no cooldown. | Return a `Duration` for this command. `Duration.ZERO` turns the cooldown off for this command only. |
| `ephemeral()` | The reply is public. | Return `true` and only the person who ran it sees the reply. |
| `permissions()` | Anyone can run it. | Return the Discord permissions the member must have. A missing permission gets one ephemeral refusal and the handler does not run. |

Throw `CommandFailure` with the sentence the user should read. Any other exception
becomes "Something went wrong while running that command." and the stack trace
goes to the log.

A command that fills autocomplete options implements `Autocomplete` as well.
`complete` receives the autocomplete event. No extra line in `TemplateBindings`.

## A message command or a user command

A message command implements `BaseMessageContextCommand` and is registered with
`messageCommand`. A user command implements `BaseUserContextCommand` and is
registered with `userCommand`.

`getCommandData()` uses `Commands.message(...)` or `Commands.user(...)`. `handle`
again replies through the hook. The reply is ephemeral.

These two do not take a cooldown or a permission set. A slash command does.

## A listener

A class that extends `ListenerAdapter` and is not a command is registered with
`listener(YourListener.class)`. `Main` registers it on the shard manager. Use
`@Inject` for whatever the listener needs.

Command classes do not use `listener`. `CommandListener` already receives slash,
message, user, and autocomplete events and passes them to the command.

## A service

A service implements `IService`:

| Method | What you put there |
| --- | --- |
| `init` | Open clients, build caches, schedule work. |
| `shutdown` | Close what `init` opened. |
| `dependsOn` | The service classes that must already have finished `init`. The default is none. |

Mark it `@Singleton`. `ServiceManager` calls `init` on the instance Guice returns,
and another copy would not have been started.

Two registration lines:

| Line | When `init` runs |
| --- | --- |
| `service(YourService.class)` | Before the bot connects. The database, caches, and anything that does not call Discord yet. |
| `ready(YourService.class)` | After Discord sends ready. Guild lookups and messages sent at startup. |

`TaskManager`, `DatabaseManager`, and `MenuService` are `service(...)` entries.
`MenuService` depends on `TaskManager` and `DatabaseManager`, so those two start
first even though the lines are not in that order.

`TaskManager` is the pools. `ioExecutor()` is for database and HTTP. `cpuExecutor()`
is for CPU work. Both throw if you call them before `init`.

## A class in another jar

Subclass `BotFeature`, call the same methods from `contribute()`, and put the
class name on its own line in
`src/main/resources/META-INF/services/es.redactado.feature.BotFeature` inside that
jar. This repository's own line stays in `TemplateBindings`. Do not add
`TemplateBindings` to that file.

## The database

`database.type` in `config.yml` is `SQLITE`, `MARIADB`, or `H2`.

| Type | What the other database fields do |
| --- | --- |
| `SQLITE` | A file under `database.path`. The pool is one connection. `host`, `port`, `user`, and `password` are unused. |
| `H2` | A file under `database.path`. |
| `MARIADB` | `host`, `port`, `name`, `user`, and `password`. |

`hibernate.schema: VALIDATE` runs the SQL files, then checks that the tables match
the entity classes. `UPDATE` skips the SQL files and lets Hibernate change the
database. Use `VALIDATE` once a database has data you care about.

### Tables that already exist

`preset_preference` stores the preset last chosen for a guild or a user.

| Column | Contents |
| --- | --- |
| `scope` | `GUILD` or `USER` |
| `subject_id` | That guild id or user id |
| `preset_name` | Preset id, for example `default` |

`channel_panel` stores the Discord message that currently shows one menu in a
channel. One row per guild, channel, and menu. `message_id` is the message the
next publish edits.

Both tables also have `id`, `created_at`, `updated_at`, and `version` from
`BaseDomain`. `id` is an integer. `version` increments on each update, and a
write that still has the old number fails.

Discord ids are not the row id. Store them as `long` columns, the way
`subject_id`, `guild_id`, `channel_id`, and `message_id` are stored.

### Add a table

1. Write an `@Entity` class that extends `BaseDomain`. `PresetPreference` is the
   class to copy. Give it a protected no-arg constructor. Hibernate uses that.
2. Add `entity(YourEntity.class)` in `TemplateBindings`.
3. Add a SQL file for each database you run. The template ships all three:
   `src/main/resources/db/migration/sqlite/`, `h2/`, and `mariadb/`. Name it
   `V2__notes.sql` or a higher number. Version 1 is `V1__preset_and_panel.sql`.
4. Put that filename on its own line in the `manifest.txt` beside it.

The three files describe the same table. The id column differs:

| Dialect | `id` column |
| --- | --- |
| sqlite | `integer primary key autoincrement` |
| h2 | `integer generated by default as identity primary key` |
| mariadb | `integer not null auto_increment primary key` |

A Discord id column is `bigint not null` in all three. Copy the `created_at`,
`updated_at`, and `version` lines from `V1__preset_and_panel.sql`.

The same version number twice for one dialect stops startup. A number can be
reused across dialects: sqlite version 2 and mariadb version 2 are a pair.

A jar that cannot edit `manifest.txt` registers the file with
`migration("sqlite", 2, "/db/migration/sqlite/V2__notes.sql")`, and the same for
`h2` and `mariadb`. Do that instead of the manifest line, not as well as it.
Listing a file in both places is the duplicate version that stops startup.

### Read and write

Inject `DatabaseManager`. One call is one transaction.

| Call | Use it |
| --- | --- |
| `read` | A query. The session is read-only. |
| `inTransaction` | Insert, update, or delete. A read and a write that belong together go in this one call. |
| `readAsync` | The same query, on the I/O pool. Use this from a command or a listener. |
| `inTransactionAsync` | The same write, on the I/O pool. |

`StoredPresetPreferences` is a working example: it looks up a `PresetPreference`
with `readAsync` and inserts or updates it with `inTransactionAsync`.

`AbstractRepository` is the shorter version of the common operations. Subclass
it, pass `DatabaseManager` to the constructor, and you get `save`, `findById`,
`findRange`, `delete`, and `deleteById`. `findRange` takes an offset and a limit
from 1 to 200. Each of those methods is its own transaction. If the read and the
write must commit together, call `inTransaction` yourself and use the `Session`
it gives you.

Calling any of these before `DatabaseManager.init` throws "DatabaseManager is
not running". A command runs after startup, so that is already done.

## A setting

`config.example.yml` lists every field, and the comment on each line is the
environment variable that overrides it. A blank variable leaves the file value.
The file on disk is not rewritten. `CONFIG_FILE` picks a path other than
`config.yml`.

To add a field:

1. Add it to the record in `ConfigFile`, with a default in that record's no-arg
   constructor and a `@Comment` that names the environment variable.
2. Add it to `BotConfig` and set it in `BotConfig.from`. The bot reads
   `BotConfig`.

`database.host` becomes `BOT_DATABASE_HOST`. `bot.token` becomes `BOT_TOKEN`,
because that path already starts with `bot`.

A class in another jar that cannot edit `ConfigFile` loads its own file from
the same directory:

```java
WelcomeSettings settings = files.load("welcome.yml", WelcomeSettings.class);
```

`files` is an injected `ConfigFiles`. `WelcomeSettings` is a ConfigLib record
with a no-arg constructor, the same shape as the records inside `ConfigFile`.
That file does not receive the `BOT_` overlay. `config.yml` does.

## What starts, in order

1. `config.yml` is loaded and the environment is applied.
2. Guice builds the injector and installs `TemplateBindings` and any other
   `BotFeature`.
3. `service(...)` classes start. `dependsOn` picks the order.
4. Listeners are registered, including `CommandListener` and `MenuListener`.
5. The bot connects.
6. On ready, `ready(...)` classes start, then the commands are registered with
   Discord.
