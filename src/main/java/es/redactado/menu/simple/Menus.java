package es.redactado.menu.simple;

import es.redactado.menu.api.Loader;
import es.redactado.menu.preset.Tone;
import java.util.List;

/**
 * Where a simple menu starts.
 *
 * <p>The whole entry point of the DSL is these two methods:
 *
 * <pre>{@code
 * Menu help = Menus.simple("help")
 *     .tone(Tone.INFO)
 *     .home(v -> v.text("Pick a topic."))
 *     .view("faq", v -> v.text("...").row(r -> r.back()))
 *     .build();
 * }</pre>
 *
 * <p>What {@link SimpleMenuBuilder#build()} returns is an ordinary
 * {@link es.redactado.menu.api.Menu}: register it with the router like any other and
 * everything the framework does for a menu, it does for this one.
 *
 * <p>The name checks live here rather than on each builder method, because the rule is one
 * rule and three call sites would be three chances to state it differently.
 */
public final class Menus {

    /**
     * Names the framework already uses.
     *
     * <p>{@code nav} and {@code page} are registered for every menu, so a simple menu
     * declaring one would replace the built-in behaviour with its own and silently break
     * navigation or paging. {@code home} is the one view that always exists, so a second view
     * by that name would be unreachable.
     */
    static final List<String> RESERVED = List.of("nav", "page", "home");

    private Menus() {}

    /**
     * Starts a menu with no data to load.
     *
     * @param id the menu id, used for routing and as the first segment of every component
     *     id this menu produces
     * @return a builder for the menu
     * @throws IllegalArgumentException if the id is empty or contains a colon
     */
    public static SimpleMenuBuilder<Void> simple(String id) {
        return new SimpleMenuBuilder<>(id, null);
    }

    /**
     * Starts a menu whose views depend on data.
     *
     * <p>The loader runs once per render, before any element is resolved, so no element has
     * to fetch anything itself. It may block; the framework runs it on the menu executor,
     * applies {@link SimpleMenuBuilder#loadTimeout(java.time.Duration)} and turns a failure
     * into the localized error reply.
     *
     * @param id the menu id
     * @param loader fetches the model each render
     * @param <M> the model type
     * @return a builder for the menu
     * @throws IllegalArgumentException if the id is empty or contains a colon
     */
    public static <M> SimpleMenuBuilder<M> simple(String id, Loader<M> loader) {
        if (loader == null) {
            throw new IllegalArgumentException("A loader must not be null");
        }
        return new SimpleMenuBuilder<>(id, loader);
    }

    /**
     * Checks a menu id, which becomes a segment of every component id.
     *
     * @param id the candidate id
     * @return the id
     */
    static String requireId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A menu id must not be empty");
        }
        if (id.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "A menu id must not contain ':', which separates id segments: " + id);
        }
        return id;
    }

    /**
     * Checks a tone.
     *
     * @param tone the candidate tone
     * @return the tone
     */
    static Tone requireTone(Tone tone) {
        if (tone == null) {
            throw new IllegalArgumentException("A tone must not be null");
        }
        return tone;
    }

    /**
     * Checks a view name, which is also the action that opens it.
     *
     * @param name the candidate name
     * @param menuId the menu it belongs to, for the message
     * @return the name
     */
    static String requireViewName(String name, String menuId, boolean isHome) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "A view name must not be empty in menu '" + menuId + "'");
        }
        if (name.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "A view name must not contain ':', which separates id segments: " + name);
        }
        if (RESERVED.contains(name) && !isHome) {
            throw new IllegalArgumentException(
                    ("View name '%s' in menu '%s' is reserved; 'nav' and 'page' are registered"
                                    + " for every menu and 'home' is the view a menu opens on")
                            .formatted(name, menuId));
        }
        return name;
    }

    /**
     * Checks that a view has something in it.
     *
     * @param view the finished view
     * @param menuId the menu it belongs to, for the message
     */
    static void requireNotEmpty(View<?> view, String menuId) {
        if (view.elements().length == 0) {
            throw new IllegalStateException(
                    ("View '%s' in menu '%s' has no elements; a view with nothing in it"
                                    + " renders as a blank message")
                            .formatted(view.name(), menuId));
        }
    }
}
