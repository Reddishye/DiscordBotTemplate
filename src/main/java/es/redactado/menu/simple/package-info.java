/**
 * A declarative way to write a menu, for the ones that do not need a class.
 *
 * <p>{@link es.redactado.menu.simple.Menus#simple(String)} returns a builder that collects
 * views and actions, and its {@code build()} returns an ordinary
 * {@link es.redactado.menu.api.Menu}. Nothing here is a second menu system: routing, the
 * owner check, the duplicate-click guard, sessions, presets, translations and asynchronous
 * loading are all the framework's, because the built menu is the framework's.
 *
 * <p>The builders are mutable and not thread-safe, because each one is built inside the code
 * that declares it and then thrown away. What {@code build()} returns is immutable and safe
 * to share, and is expected to be built once and registered once.
 *
 * <p>What this package deliberately cannot do is anything a class could: read its own
 * configuration, decide its own actions at runtime, or render differently per interaction
 * beyond what the data loader provides. A menu that needs that is a class, and
 * {@code custom(...)} is the bridge between the two.
 */
package es.redactado.menu.simple;
