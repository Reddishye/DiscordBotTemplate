# Configuration

First start writes `config.yml` from `config.example.yml`. Each comment in that file is the environment variable for that field.

A blank environment variable does not replace the file value. The file is not written back after the overlay.

`CONFIG_FILE` sets the path. Default: `config.yml`.

Invalid values abort startup. The error names the field.

| Setting | Result |
| --- | --- |
| `database.type: SQLITE` | File under `database.path`. Pool size 1. `host`, `port`, `user`, and `password` are unused |
| `database.type: H2` | File under `database.path` |
| `database.type: MARIADB` | Uses `host`, `port`, `name`, `user`, `password` |
| `commands.scope: GUILD` | Registers commands on `commands.guildId`. `0` registers nothing |
| `commands.scope: GLOBAL` | Registers commands once, from shard 0 |
| `hibernate.schema: UPDATE` | Default. Hibernate creates and updates tables from entity classes |
| `hibernate.schema: VALIDATE` | Runs `db/migration` SQL, then checks tables and does not alter them. See `docs/database.md` |

## New field

1. Add the field to the record in `ConfigFile`, with a default in the no-arg constructor and the environment name in `@Comment`.
2. Add the field to `BotConfig` and assign it in `BotConfig.from`.

Environment names are the YAML path in capitals, with `.` replaced by `_`, prefixed with `BOT_`.

| YAML | Variable |
| --- | --- |
| `database.host` | `BOT_DATABASE_HOST` |
| `bot.token` | `BOT_TOKEN` |
| `menu.sessionIdleTtl` | `BOT_MENU_SESSION_IDLE_TTL` |

## Separate file

A class outside this repository loads another YAML file from the same directory as `config.yml`:

```java
WelcomeSettings settings = files.load("welcome.yml", WelcomeSettings.class);
```

`files` is `ConfigFiles`. `WelcomeSettings` is a ConfigLib record with a no-arg constructor. This file has no `BOT_` overlay.
