# NOTES

What is still open, what is deliberately not built, and the baseline that was verified.
The decisions behind the code are in `docs/design-decisions.md`; the API and the
architecture are in `docs/menus-inventory.md`.

## Open questions

Each entry states the question and the default that applies until it is answered.

- **Preset preference storage.** Guild and user preferences are stored in
  `preset_preference` when `MenuService` is started with the database. The in-memory
  implementation remains for tests and for a service constructed without one.
- **Over-limit renders.** `Validator` reports a container over the limit, and
  `ValidationResult.throwIfInvalid()` turns it into an exception. Whether a running bot should
  render the oversized container anyway rather than fail the interaction has not been decided;
  today the exception becomes one localized error reply.
- **Simple-menu naming.** `Menus.simple("help")` follows the plan. It collides with nothing in
  JDA 6, and renaming it would be cosmetic.

## Known limitations

- **The list below cannot be verified with mocks.** A mocked interaction has no client, so
  there is no layout to look at; no rendering, so nothing to compare against a screenshot; no
  network, so no rate limit, no latency and no three-second acknowledgement budget; no
  real filesystem, so no preset watcher; no restart, so no expiry; and no second account, so no
  ownership check by anybody else's hand. Those are the manual items in
  `docs/manual-test.md`.
- **No global concurrency cap.** `MenuRouter.close()` releases the executor and drains the
  sessions; it does not cap how many handlers may run at once across all messages. A global cap
  would need a policy for what happens to the interactions that do not get a slot.
- **Shared channel panels remember one message.** `ChannelPanels.publish` edits the stored
  message for a guild, channel and menu. The caller still renders the container. A channel
  message has no owner, so its buttons stay visible to everyone.
- **Per-guild and per-user preset preferences are persisted** in `preset_preference` for the
  service the bot starts. A menu service built without a database still keeps them in memory.

## Verified baseline

- Java 27 toolchain, Gradle 9.8.0 through the wrapper.
- JDA 6.5.0, Jackson Databind 2.19.1 at the version JDA already resolves.
- Build command: `./gradlew clean spotlessApply build`.
- Stress tests: `./gradlew test -PrunStress`, tagged `stress` and excluded from the build above.
- Filesystem-tagged tests: `./gradlew test -PexcludeTags=filesystem`.
