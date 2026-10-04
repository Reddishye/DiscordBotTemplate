package es.redactado.menu.simple;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Loader;
import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.AbstractMenu;
import es.redactado.menu.preset.Tone;
import es.redactado.menu.view.MenuBuilder;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/**
 * The menu a {@link Menus} builder produces.
 *
 * <p>An ordinary menu, in every way that matters: it is registered by the router like any
 * other, its actions are declared in one table, and it inherits the owner check, the
 * duplicate-click guard, the session, the preset resolution, the translations and the loader
 * timeout from {@link AbstractMenu}. There is no second dispatch path here, and no way for a
 * simple menu to be treated differently from a hand-written one.
 *
 * <p>What it adds is the switch a class would otherwise write: a map from view name to view,
 * and a map from action name to the view that owns it. Both are immutable once built, which
 * is what lets one instance render from several threads at once.
 *
 * @param <M> the model type, or {@link Void} when the menu declares no loader
 */
final class SimpleMenu<M> extends AbstractMenu {

    /** The name of the view a menu shows when it is opened. */
    static final String HOME_VIEW = "home";

    private final Map<String, View<M>> views;
    private final Loader<M> loader;
    private final Tone tone;
    private final Duration loadTimeout;
    private final boolean shared;
    private final String presetName;
    private final Map<String, String> actionToView;
    private final List<Action<M>> actions;

    SimpleMenu(
            String id,
            Map<String, View<M>> views,
            Loader<M> loader,
            Tone tone,
            Duration loadTimeout,
            boolean shared,
            String presetName,
            Map<String, String> actionToView,
            List<Action<M>> actions) {
        super(id);
        this.views = views;
        this.loader = loader;
        this.tone = tone;
        this.loadTimeout = loadTimeout;
        this.shared = shared;
        this.presetName = presetName;
        this.actionToView = actionToView;
        this.actions = actions;
    }

    /**
     * Declares every action the DSL collected, in one table.
     *
     * <p>Called once by the router at registration, from the framework's own {@code actions}
     * method, so the table is immutable for the lifetime of the menu and the array of
     * handlers is never touched again.
     */
    @Override
    protected void declare(ActionTable.Builder table) {
        for (Action<M> action : actions) {
            switch (action.kind()) {
                case BUTTON ->
                        table.button(
                                action.name(),
                                action.ack(),
                                (ctx, event) ->
                                        ((ClickHandler) action.handler())
                                                .handle(
                                                        new Click(
                                                                support(ctx),
                                                                event,
                                                                action.opensModal())));
                case SELECT ->
                        table.select(
                                action.name(),
                                action.ack(),
                                (ctx, event) ->
                                        ((PickHandler) action.handler())
                                                .handle(new Pick(support(ctx), event.getValues())));
                case MODAL ->
                        table.modal(
                                action.name(),
                                action.ack(),
                                (ctx, event) ->
                                        ((SubmitHandler) action.handler())
                                                .handle(
                                                        new Submit(
                                                                support(ctx),
                                                                es.redactado.menu.view.ModalForm
                                                                        .read(event))));
            }
        }
    }

    /**
     * Finds a declared action by name.
     *
     * <p>The router only dispatches names that came out of this table, so the lookup cannot
     * miss; falling back to the first action rather than throwing keeps a corrupt id from
     * turning into an exception on the event thread.
     */
    private int actionIndex(String name) {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).name().equals(name)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * What a trigger of this menu can do, built once per interaction.
     *
     * <p>Exists so {@link Trigger} can reach the protected members of the base class, which
     * are the framework's single edit and modal paths, without knowing what this class is.
     */
    private Trigger.Support support(MenuContext ctx) {
        return new Trigger.Support() {
            @Override
            public MenuContext ctx() {
                return ctx;
            }

            @Override
            public CompletableFuture<Void> refresh() {
                // refresh() resolves the current view itself, so the DSL does not repeat it.
                return SimpleMenu.this.refresh(ctx);
            }

            @Override
            public CompletableFuture<Void> go(String menuId, String viewName) {
                return ctx.navigate(NavigationMode.PUSH, new NavEntry(menuId, viewName, List.of()));
            }

            @Override
            public CompletableFuture<Void> done() {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void showModal(net.dv8tion.jda.api.modals.Modal modal) {
                SimpleMenu.this.showModal(ctx, modal);
            }
        };
    }

    /**
     * Renders the view this context belongs to.
     *
     * <p>The lookup is a map read, not a switch over strings, and the loader is only called
     * once per render no matter how many views the menu has.
     */
    @Override
    public CompletableFuture<Container> render(MenuContext ctx) {
        View<M> view = views.get(ctx.action());
        if (view == null) {
            return unknownView(ctx);
        }
        return view(ctx, loader, (context, model) -> build(context, model, view));
    }

    /**
     * Renders one view into a container.
     *
     * <p>Nothing here blocks and nothing here allocates beyond the components: the tone and
     * the preset come from the menu and the context, the elements are a shared immutable
     * array, and each element resolves itself.
     */
    private Container build(MenuContext ctx, M model, View<M> view) {
        MenuBuilder builder = MenuBuilder.create(id()).tone(tone);
        Scope<M> scope = new Scope<>(ctx, model);
        for (Element<M> element : view.elements()) {
            builder.add(element.render(scope));
        }
        return builder.build(ctx);
    }

    @Override
    protected Duration loadTimeout() {
        return loadTimeout;
    }

    @Override
    public boolean shared() {
        return shared;
    }

    @Override
    public Optional<String> presetName() {
        return Optional.ofNullable(presetName);
    }

    @Override
    public NavEntry home(MenuContext ctx) {
        return new NavEntry(id(), HOME_VIEW, List.of());
    }

    @Override
    public NavEntry currentView(MenuContext ctx) {
        return new NavEntry(id(), viewOf(ctx), ctx.params());
    }

    /**
     * The view an interaction belongs to.
     *
     * <p>A context arriving with a view name is navigation landing on that view. A context
     * arriving with an action name is a button or select belonging to the view that declared
     * it. Anything else is a stale message or a hand-edited id, and the home view is the
     * honest answer, being the one view guaranteed to exist.
     *
     * @param ctx the context
     * @return the view name
     */
    String viewOf(MenuContext ctx) {
        String action = ctx.action();
        if (views.containsKey(action)) {
            return action;
        }
        String owner = actionToView.get(action);
        return owner != null ? owner : HOME_VIEW;
    }

    /**
     * Wraps a component the DSL has no builder for, so the container can take it.
     *
     * <p>A {@link MenuComponent} is a function of the context, so a JDA component that is
     * already built is one that ignores it.
     *
     * @param component the already-built component
     * @return a component that renders it
     */
    static MenuComponent wrap(ContainerChildComponent component) {
        return ctx -> List.of(component);
    }

    /** Builds an immutable view map from the declared views. */
    static <M> Map<String, View<M>> index(List<View<M>> declared) {
        Map<String, View<M>> map = new HashMap<>();
        for (View<M> view : declared) {
            map.put(view.name(), view);
        }
        return Collections.unmodifiableMap(map);
    }
}
