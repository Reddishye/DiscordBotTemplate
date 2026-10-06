# Configuration

The first start writes `config.yml` from the defaults in `config.example.yml`.
Edit `config.yml`. The comment on each value names the environment variable that
overrides it, such as `BOT_TOKEN` and `BOT_DATABASE_HOST`. A blank variable
leaves the file value. The file is not rewritten.

`CONFIG_FILE` is the path when the file is not called `config.yml`.

A bad value stops startup and names the setting.

| Value | Effect |
| --- | --- |
| `database.type: SQLITE` | A file under `database.path`, one connection |
| `database.type: MARIADB` | `host`, `port`, `name`, `user`, `password` |
| `commands.scope: GUILD` and `commands.guildId: 0` | No commands are registered until you set a server id |
| `commands.scope: GLOBAL` | Commands are registered once, from shard 0 |
| `hibernate.schema: UPDATE` | Hibernate creates and updates tables from entity classes. This is the default |
| `hibernate.schema: VALIDATE` | The SQL files under `db/migration` own the tables. See `docs/database.md` |

## Add a setting

1. Add the field to the record in `ConfigFile`, and a default in that record's
   no-arg constructor. Name the environment variable in the `@Comment`.
2. Add the same field to `BotConfig` and set it in `BotConfig.from`. The bot
   reads `BotConfig`.

The environment name is the YAML path. `database.host` is `BOT_DATABASE_HOST`.
`bot.token` is `BOT_TOKEN`, because that path already starts with `bot`.

A class in another jar that cannot edit `ConfigFile` loads its own file from
the same directory:

```java
WelcomeSettings settings = files.load("welcome.yml", WelcomeSettings.class);
```

`files` is an injected `ConfigFiles`. `WelcomeSettings` is a ConfigLib record
with a no-arg constructor, the same shape as the records in `ConfigFile`. That
file does not get the `BOT_` overlay.
