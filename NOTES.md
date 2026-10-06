# Notes

Settings: `docs/configuration.md`. Features: `docs/features.md`. Database: `docs/database.md`. Menu API: `docs/menus-inventory.md`.

## Open

- Oversized containers: `Validator` reports the limit and `ValidationResult.throwIfInvalid()` throws. The running bot turns that into one localized error reply. Rendering the container anyway is not implemented.
- `Menus.simple(id)` is the DSL entry point.

## Limits

- Checks that need a Discord client, a filesystem watch, or a second account are listed in `docs/manual-test.md`.
- `MenuRouter.close()` does not cap in-flight handlers across messages.
- `ChannelPanels.publish` stores one message per guild, channel, and menu. Channel messages have no owner.
- `MenuService` started with a database stores preset choices in `preset_preference`. A `MenuService` built without a database keeps them in memory.

## Baseline

- Java 27, Gradle 9.8.0.
- JDA 6.5.0. Jackson Databind 2.19.1.
- `./gradlew clean spotlessApply build`
- Stress: `./gradlew test -PrunStress` (tag `stress`, excluded from `build`)
- Skip filesystem tests: `./gradlew test -PexcludeTags=filesystem`
