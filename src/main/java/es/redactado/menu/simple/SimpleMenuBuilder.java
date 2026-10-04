package es.redactado.menu.simple;

import es.redactado.menu.api.Loader;
import es.redactado.menu.api.Menu;
import es.redactado.menu.preset.Tone;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Collects the views and the settings of one menu, then builds it.
 *
 * <p>Made by {@link Menus#simple(String)}, used once and discarded:
 *
 * <pre>{@code
 * Menu help = Menus.simple("help")
 *     .tone(Tone.INFO)
 *     .home(v -> v
 *         .header(Msg.key("help.title"), Msg.key("help.subtitle"))
 *         .text("Pick a topic."))
 *     .build();
 * }</pre>
 *
 * <p>Mutable and not thread-safe, because it exists only while the menu is being written.
 * What {@link #build()} returns is immutable and safe to share; a built menu is expected to
 * be built once and registered once.
 *
 * @param <M> the model type, or {@link Void} when the menu declares no loader
 */
public final class SimpleMenuBuilder<M> {

    /** Ten seconds, the same default a hand-written menu gets. */
    private static final Duration DEFAULT_LOAD_TIMEOUT = Duration.ofSeconds(10);

    /**
     * The loader a menu without one gets.
     *
     * <p>An already-completed future of null, so a menu with no data still goes through
     * the same render path as one with a loader and there is no second code path to keep
     * correct.
     */
    private static final Loader<Void> NO_LOADER = ctx -> CompletableFuture.completedFuture(null);

    private final String id;
    private final Loader<M> loader;
    private final Declarations<M> declarations;
    private final List<View<M>> views = new ArrayList<>();
    private Tone tone = Tone.ACCENT;
    private Duration loadTimeout = DEFAULT_LOAD_TIMEOUT;
    private boolean shared;
    private String presetName;
    private boolean homeDeclared;

    SimpleMenuBuilder(String id, Loader<M> loader) {
        this.id = Menus.requireId(id);
        this.declarations = new Declarations<>(this.id);
        this.loader = loader;
    }

    /**
     * Sets what this menu is for, and so which colour the preset gives it.
     *
     * @param tone the tone
     * @return this builder
     */
    public SimpleMenuBuilder<M> tone(Tone tone) {
        this.tone = Menus.requireTone(tone);
        return this;
    }

    /**
     * Forces a look for this menu, whatever the guild and the user asked for.
     *
     * @param name a preset name
     * @return this builder
     */
    public SimpleMenuBuilder<M> preset(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A preset name must not be blank");
        }
        this.presetName = name;
        return this;
    }

    /**
     * Makes this menu usable by everyone who can see it, instead of by its owner alone.
     *
     * @return this builder
     */
    public SimpleMenuBuilder<M> shared() {
        this.shared = true;
        return this;
    }

    /**
     * How long a render waits for the loader.
     *
     * <p>Only meaningful with a loader. Past it the user gets the localized timeout message;
     * it does not break Discord's acknowledgement rule, because the interaction was
     * acknowledged before the load started.
     *
     * @param loadTimeout the timeout; null means the default
     * @return this builder
     */
    public SimpleMenuBuilder<M> loadTimeout(Duration loadTimeout) {
        this.loadTimeout = loadTimeout == null ? DEFAULT_LOAD_TIMEOUT : loadTimeout;
        return this;
    }

    /**
     * Declares the view this menu opens on.
     *
     * <p>Required, and only once: a second call would be a view silently replacing the one
     * that opens the menu, which is a mistake rather than a merge.
     *
     * @param view builds the view
     * @return this builder
     * @throws IllegalStateException if a home view was already declared
     */
    public SimpleMenuBuilder<M> home(Consumer<ViewBuilder<M>> view) {
        if (homeDeclared) {
            throw new IllegalStateException(
                    "Menu '" + id + "' already has a home view; a menu has exactly one");
        }
        homeDeclared = true;
        addView(SimpleMenu.HOME_VIEW, view);
        return this;
    }

    /**
     * Declares a view reachable by navigation.
     *
     * <p>The name is also the action that opens it, which is why it cannot be one of the
     * reserved names.
     *
     * @param name the view name
     * @param view builds the view
     * @return this builder
     * @throws IllegalStateException if the view name is already declared
     * @throws IllegalArgumentException if the name is reserved or cannot fit a component id
     */
    public SimpleMenuBuilder<M> view(String name, Consumer<ViewBuilder<M>> view) {
        addView(Menus.requireViewName(name, id, false), view);
        return this;
    }

    private void addView(String name, Consumer<ViewBuilder<M>> view) {
        for (View<M> declared : views) {
            if (declared.name().equals(name)) {
                throw new IllegalStateException(
                        "Menu '" + id + "' already has a view named '" + name + "'");
            }
        }
        ViewBuilder<M> builder = new ViewBuilder<>(name, declarations);
        view.accept(builder);
        View<M> built = new View<>(name, builder.elements(), builder.ownedActions());
        Menus.requireNotEmpty(built, id);
        built.checkNameLength(id.length());
        views.add(built);
    }

    /**
     * Declares what a submitted modal does.
     *
     * <p>Menu-wide rather than per view: a form is opened by a button on one view and
     * submitted while the message may well be showing another, so tying the handler to a view
     * would be a claim the flow cannot keep.
     *
     * @param action the action name; the id the form carries
     * @param handler what the answers run
     * @return this builder
     * @throws IllegalStateException if the action name is already used
     * @throws IllegalArgumentException if the name cannot be used in an id
     */
    public SimpleMenuBuilder<M> onSubmit(String action, SubmitHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException(
                    "Submission '" + action + "' in menu '" + id + "' needs a handler");
        }
        declarations.submit(action, handler);
        return this;
    }

    /**
     * The checks that need the whole menu in front of it.
     *
     * <p>Most rules fire where their element is declared, because that is when the author is
     * looking at the line. These need every view declared, and they are the ones a render
     * could not recover from: a view holding more elements than a container may hold has no
     * valid output at all, and a name that cannot fit an id is refused by Discord rather than
     * by this framework.
     */
    private void validate() {
        for (View<M> view : views) {
            view.checkNameLength(id.length());
            Menus.requireFitsInContainer(view, id);
        }
        declarations.validate();
    }

    /**
     * Declares what a button the DSL did not draw will do.
     *
     * <p>For the action that a {@code custom(...)} component renders, such as the confirming
     * button of a {@link es.redactado.menu.view.Confirm}. Every action the DSL builds itself
     * is declared by the element that draws it, which leaves no way to handle an action that
     * arrives from a component the author supplied, and an action with no handler is a button
     * that fails when pressed.
     *
     * <p>Menu-wide, like a form submission: a component's button does not belong to one view,
     * and a {@code refresh()} from its handler redraws the view the interaction is on, which
     * is the one the user is looking at.
     *
     * @param action the action name; the id the button carries
     * @param handler what the press runs
     * @return this builder
     * @throws IllegalStateException if the action name is already used
     * @throws IllegalArgumentException if the name cannot be used in an id
     */
    public SimpleMenuBuilder<M> onClick(String action, ClickHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException(
                    "Action '" + action + "' in menu '" + id + "' needs a handler");
        }
        declarations.button(null, action, List.of(), false, handler);
        return this;
    }

    /**
     * Builds the menu.
     *
     * <p>Every problem the framework can see is reported here, naming the view and the
     * element, rather than at the first render in front of a user.
     *
     * @return an ordinary menu, ready to be registered with the router
     * @throws IllegalStateException if no home view was declared
     */
    public Menu build() {
        if (!homeDeclared) {
            throw new IllegalStateException(
                    "Menu '" + id + "' has no home view; a menu needs at least one view");
        }
        validate();
        Loader<M> effective = loader;
        if (effective == null) {
            @SuppressWarnings("unchecked")
            Loader<M> none = (Loader<M>) NO_LOADER;
            effective = none;
        }
        return new SimpleMenu<>(
                id,
                SimpleMenu.index(views),
                effective,
                tone,
                loadTimeout,
                shared,
                presetName,
                declarations.actionToView(),
                declarations.actions());
    }
}
