# NOTES

What is still open, what is deliberately not built, and the baseline that was verified.
The decisions behind the code are in `docs/design-decisions.md`; the API and the
architecture are in `docs/menus-inventory.md`.

## Open questions

Each entry states the question and the default that applies until it is answered.

- **Preset preference storage.** Guild and user preferences live in
  `InMemoryPresetPreferences` and reset on restart. A persistent implementation would sit
  behind the same interface and would need a migration and a repository binding, neither of
  which the template has.
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
- **No shared channel panel.** A menu can be opened in a channel rather than in a private
  message, and a channel message carries no owner, so it is a shared menu with its buttons
  visible to everyone. There is no per-channel panel that a menu can update in place.
- **Per-guild and per-user preset preferences are not persisted.** `PresetPreferences` has an
  in-memory implementation only, so a preference resets when the bot restarts.

## Verified baseline

- Java 27 toolchain, Gradle 9.8.0 through the wrapper.
- JDA 6.5.0, Jackson Databind 2.19.1 at the version JDA already resolves.
- Build command: `./gradlew clean spotlessApply build`.
- Stress tests: `./gradlew test -PrunStress`, tagged `stress` and excluded from the build above.
- Filesystem-tagged tests: `./gradlew test -PexcludeTags=filesystem`.
