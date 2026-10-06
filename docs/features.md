# Features

`TemplateBindings` is the list of what this bot runs. `PingCommand` is already
on it. Add a line in `contribute()` next to that one.

| You are adding | Line |
| --- | --- |
| Slash command | `slashCommand(MyCommand.class)` |
| Message command | `messageCommand(MyCommand.class)` |
| User command | `userCommand(MyCommand.class)` |
| Gateway listener | `listener(MyListener.class)` |
| Service, started before the bot connects | `service(MyService.class)` |
| Service, started when Discord sends ready | `ready(MyService.class)` |
| Entity | `entity(MyEntity.class)` |

Guice constructs these classes. An `@Inject` constructor can take
`DatabaseManager`, `MenuService`, `BotConfig`, or another service.

A class in another jar extends `BotFeature`, calls the same methods, and is
named on its own line in
`META-INF/services/es.redactado.feature.BotFeature` inside that jar. Do not put
`TemplateBindings` in that file.

## Commands

A slash command implements `BaseSlashCommand`. Copy `PingCommand`.

`getCommandData()` is the name Discord shows. Two commands with the same name
stop startup. `handle` runs after the interaction is already acknowledged, so
the reply goes through `event.getHook()`.

| Method | Default | Override |
| --- | --- | --- |
| `cooldown()` | `commands.defaultCooldown` from `config.yml`. `0s` means none | A `Duration` for this command. `Duration.ZERO` turns it off |
| `ephemeral()` | The reply is public | `true` shows it only to the person who ran the command |
| `permissions()` | Anyone can run it | The member needs these Discord permissions, or they get one refusal and the handler does not run |

Throw `CommandFailure` with the sentence the user should read. Any other
exception becomes "Something went wrong while running that command." and the
stack trace goes to the log.

Autocomplete is the `Autocomplete` interface on the same class. `CommandListener`
calls `complete`. No extra registration line.

A message command implements `BaseMessageContextCommand` and uses
`Commands.message(...)`. A user command implements `BaseUserContextCommand` and
uses `Commands.user(...)`. Both replies are ephemeral and go through the hook.
They do not take a cooldown or a permission set.

Where the command is registered depends on `commands.scope`. `GUILD` uses
`commands.guildId`. `0` registers nothing. `GLOBAL` registers once from shard 0.

## Listeners

A `ListenerAdapter` that is not a command is registered with `listener(...)`.
`Main` adds it to the shard manager. Commands do not use this. `CommandListener`
already receives slash, message, user, and autocomplete events.

## Services

A service implements `IService` and is `@Singleton`.

| Method | Role |
| --- | --- |
| `init` | Open clients, build caches, schedule work |
| `shutdown` | Close what `init` opened |
| `dependsOn` | Services that must already have finished `init`. The default is none |

`service(...)` runs before the bot connects. `ready(...)` runs after Discord
sends ready. `TaskManager`, `DatabaseManager`, and `MenuService` are
`service(...)` entries. `MenuService` depends on the other two, so they start
first.

`TaskManager.ioExecutor()` is for database and HTTP. `cpuExecutor()` is for CPU
work. Both throw if called before `init`.
