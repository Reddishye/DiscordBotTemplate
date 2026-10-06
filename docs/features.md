# Features

Register types in `TemplateBindings.contribute()`. `PingCommand` is already registered.

| Type | Call |
| --- | --- |
| Slash command | `slashCommand(MyCommand.class)` |
| Message command | `messageCommand(MyCommand.class)` |
| User command | `userCommand(MyCommand.class)` |
| Listener | `listener(MyListener.class)` |
| Service, before connect | `service(MyService.class)` |
| Service, after ready | `ready(MyService.class)` |
| Entity | `entity(MyEntity.class)` |

Guice constructs the class. `@Inject` can request `DatabaseManager`, `MenuService`, `BotConfig`, or another `@Singleton` service.

Another jar: subclass `BotFeature`, use the same calls, and add the class name to `META-INF/services/es.redactado.feature.BotFeature` in that jar. Do not list `TemplateBindings` there.

## Slash commands

Implement `BaseSlashCommand`. Reference: `PingCommand`.

| Method | Behavior |
| --- | --- |
| `getCommandData()` | Name and description sent to Discord. Duplicate names abort startup |
| `handle` | Runs after acknowledgement. Reply with `event.getHook()` |
| `cooldown()` | `null` uses `commands.defaultCooldown`. `Duration.ZERO` disables it. `0s` in config disables the default |
| `ephemeral()` | `false`: public reply. `true`: only the invoking user |
| `permissions()` | Empty: no check. Otherwise the member must have the set. Failure is one ephemeral reply and `handle` is not called |

`CommandFailure` sends its message to the user. Any other exception sends "Something went wrong while running that command." and logs the stack trace.

Autocomplete: implement `Autocomplete` on the same class. `CommandListener` calls `complete`.

## Message and user commands

| Kind | Interface | Data |
| --- | --- | --- |
| Message | `BaseMessageContextCommand` | `Commands.message(...)` |
| User | `BaseUserContextCommand` | `Commands.user(...)` |

Replies are ephemeral and use the hook. No cooldown. No permission set.

## Command registration scope

| `commands.scope` | Target |
| --- | --- |
| `GUILD` | `commands.guildId`. `0` skips registration |
| `GLOBAL` | Shard 0, all guilds |

## Listeners

`listener(MyListener.class)` registers a `ListenerAdapter` on the shard manager. Slash, message, user, and autocomplete events are already handled by `CommandListener`.

## Services

Implement `IService`. Annotate `@Singleton`.

| Method | When |
| --- | --- |
| `init` | Startup |
| `shutdown` | Process shutdown, reverse of start order |
| `dependsOn` | Classes whose `init` must finish first. Default: none |

| Call | `init` runs |
| --- | --- |
| `service(...)` | Before the gateway connects |
| `ready(...)` | After the ready event |

`TaskManager`, `DatabaseManager`, and `MenuService` use `service(...)`. `MenuService.dependsOn` is `TaskManager` and `DatabaseManager`.

`TaskManager.ioExecutor()` is for database and HTTP. `cpuExecutor()` is for CPU work. Both throw `IllegalStateException` before `init()`.
