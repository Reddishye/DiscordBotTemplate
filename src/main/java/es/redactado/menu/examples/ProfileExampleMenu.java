package es.redactado.menu.examples;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.AbstractMenu;
import es.redactado.menu.core.DataCache;
import es.redactado.menu.core.DataCacheConfig;
import es.redactado.menu.core.MenuExecutor;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.view.Confirm;
import es.redactado.menu.view.Field;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.ModalForm;
import es.redactado.menu.view.Nav;
import es.redactado.menu.view.Pager;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.Text;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;

/**
 * A hand-written menu that does the things a declarative one deliberately cannot.
 *
 * <p>The three simple-menu examples stop at what the DSL can express. This one goes further,
 * because a real profile menu needs a cache, blocking writes, two modals, a paged list of
 * roles and a paged list of links, a destructive action behind a confirmation, and views that
 * take a parameter. Every one of those is here for a reason, and the reasons are the point:
 * this is the worked example for reaching past the DSL when the DSL is not enough.
 *
 * <p>What it shows, in the order a reader should notice:
 *
 * <ul>
 *   <li><strong>A cache in front of a blocking service.</strong> The service methods block;
 *       the loader runs them through {@link MenuExecutor#supply}, so the blocking happens on
 *       a virtual thread rather than on a JDA event thread or on whichever thread read the
 *       cache. Concurrent reads of one key share a single load, so fifty people opening the
 *       same profile cause one call and not fifty.
 *   <li><strong>Writes that invalidate the cache they are behind.</strong> Every write goes
 *       through {@link DataCache#invalidateAfter}, which drops the key when the write settles
 *       and passes the write's outcome through unchanged. That is the difference between a
 *       menu that shows yesterday's birth date and one that shows today's, and it holds
 *       whether the write succeeded or failed.
 *   <li><strong>Two modals.</strong> One edits a single field, one adds a two-field row. A
 *       submission is menu-wide rather than per view, because the form is opened on one view
 *       and submitted while the message may be showing another.
 *   <li><strong>A parameterised view.</strong> The role view is opened with a role id in the
 *       component id and read back with {@link MenuContext#requireLong(int)}. A view that took
 *       a parameter could not be a simple menu's view, because the DSL's view button carries
 *       no parameters.
 *   <li><strong>A {@link Pager} for each list</strong>, with the page kept in the session, so
 *       paging survives every redraw the framework performs.
 *   <li><strong>A {@link #currentView(MenuContext)} override.</strong> Without it, a click on
 *       a button named {@code nav} or a role row would record "nav" as the view being left and
 *       Back would try to render an action this menu does not recognise. See
 *       {@code NOTES.md}, "A menu needed to be asked which view it is showing".
 * </ul>
 *
 * <p>Not registered by default. It is built by {@link #create()} and, being a hand-written
 * menu, it takes its collaborators rather than reaching for them.
 */
public final class ProfileExampleMenu extends AbstractMenu implements AutoCloseable {

    /** The menu id. */
    public static final String ID = "profile";

    /** The view a menu opens on. */
    public static final String HOME = "home";

    /** A single role, opened with its id in the component id. */
    public static final String ROLE = "role";

    /** The links view, with its own pager. */
    public static final String LINKS = "links";

    /** The view asking before the profile is removed. */
    public static final String CONFIRM_REMOVE = "confirm_remove";

    /** The action that opens the birth-date form. */
    public static final String ASK_BIRTH = "ask_birth";

    /** The action that submits the birth-date form. */
    public static final String SAVE_BIRTH = "save_birth";

    /** The action that opens the add-link form. */
    public static final String ASK_LINK = "ask_link";

    /** The action that submits the add-link form. */
    public static final String SAVE_LINK = "save_link";

    /** The action that opens the links view. */
    public static final String OPEN_LINKS = "open_links";

    /** The action that asks before removing. */
    public static final String ASK_REMOVE = "ask_remove";

    /** The action the confirmation's yes button carries. */
    public static final String DO_REMOVE = "do_remove";

    /** The views this menu knows, which is also what {@link #render} accepts. */
    private static final List<String> VIEWS = List.of(HOME, ROLE, LINKS, CONFIRM_REMOVE);

    private static final String TITLE = "Profile";
    private static final String SUBTITLE = "Everything the bot knows about you";
    private static final String BIRTH_LABEL = "Birth date";
    private static final String BIRTH_PLACEHOLDER = "YYYY-MM-DD";
    private static final String LINK_LABEL = "Title";
    private static final String LINK_URL_LABEL = "URL";
    private static final String CONFIRM_TEXT = "This deletes the profile and cannot be undone.";

    private final FakeProfileService service;
    private final DataCache<Long, Profile> cache;

    private ProfileExampleMenu(
            FakeProfileService service, DataCache<Long, Profile> cache, MenuExecutor executor) {
        super(ID);
        this.service = service;
        this.cache = cache;
        this.executor = executor;
    }

    /**
     * Builds the menu over a fake service that takes twenty milliseconds per call.
     *
     * <p>Owns the executor it creates, so whoever builds this closes it. In a real bot the
     * executor would be the host's own pool, shared with everything else, and the menu would
     * borrow rather than own.
     *
     * @return the menu
     */
    public static ProfileExampleMenu create() {
        return using(new FakeProfileService(20));
    }

    /**
     * Builds the menu over a given service, with its own virtual executor.
     *
     * @param service where the data comes from; its methods block
     * @return the menu, which the caller closes
     */
    public static ProfileExampleMenu using(FakeProfileService service) {
        MenuExecutor executor = MenuExecutor.virtual();
        DataCache<Long, Profile> cache =
                new DataCache<>(
                        DataCacheConfig.of(1_000, Duration.ofMinutes(5)),
                        // The loader is the blocking call, handed to the executor. This is the
                        // whole point of the cache's loader being a Function of a key rather
                        // than the service being called at every render.
                        key -> executor.supply(() -> service.loadBlocking(key)),
                        executor::execute);
        return new ProfileExampleMenu(service, cache, executor);
    }

    /**
     * The executor this menu loads and writes on.
     *
     * <p>Named apart from {@link #service} and {@link #cache} because the three are the only
     * things a handler needs and reading them in one place is worth the field.
     */
    protected final MenuExecutor executor;

    @Override
    protected void declare(ActionTable.Builder table) {
        // Ack.MODAL on the two that open a form: a modal has to be the only answer, and
        // deferring an edit would spend the interaction before the modal could.
        table.button(ASK_BIRTH, Ack.MODAL, this::askBirth);
        table.button(ASK_LINK, Ack.MODAL, this::askLink);
        table.button(OPEN_LINKS, Ack.DEFER_EDIT, (ctx, event) -> push(ctx, LINKS));
        table.button(ASK_REMOVE, Ack.DEFER_EDIT, (ctx, event) -> push(ctx, CONFIRM_REMOVE));
        table.button(DO_REMOVE, Ack.DEFER_EDIT, this::removeProfile);
        table.modal(SAVE_BIRTH, Ack.DEFER_EDIT, this::saveBirth);
        table.modal(SAVE_LINK, Ack.DEFER_EDIT, this::saveLink);
    }

    @Override
    public CompletableFuture<Container> render(MenuContext ctx) {
        if (!VIEWS.contains(ctx.action())) {
            // Before the load, not after: an id this menu does not recognise is answered
            // without touching the service, which is the whole point of the failure being a
            // localized sentence rather than a blank container.
            return unknownView(ctx);
        }
        return view(
                ctx,
                this::profileLoader,
                (context, profile) ->
                        switch (context.action()) {
                            case HOME -> home(context, profile);
                            case ROLE -> role(context, profile);
                            case LINKS -> links(context, profile);
                            case CONFIRM_REMOVE -> confirmRemove(context, profile);
                            default ->
                                    throw new IllegalStateException(
                                            "Checked above, so unreachable: " + context.action());
                        });
    }

    /**
     * The loader: one cache read per render, shared by every view.
     *
     * <p>The key is the user, so two people opening two profiles are two keys and one person
     * opening the menu twice is one load. Nothing in a render touches the service directly.
     */
    private CompletableFuture<Profile> profileLoader(MenuContext ctx) {
        return cache.get(keyOf(ctx));
    }

    private Container home(MenuContext ctx, Profile profile) {
        return MenuBuilder.create(ID)
                .add(Text.title(TITLE))
                .add(Text.small(SUBTITLE))
                .add(Field.of("Id", Long.toString(profile.id())))
                .add(Field.of(BIRTH_LABEL, profile.birthDate()))
                .add(
                        Pager.of(
                                "roles",
                                profile.roles(),
                                5,
                                // Each row opens the role view with its id in the id, which is
                                // the only way this menu takes a parameter.
                                role ->
                                        Row.of(
                                                Nav.view(
                                                        ROLE,
                                                        role.name() + " - " + role.permissions(),
                                                        Long.toString(role.id())))))
                .add(Text.of("Links: " + profile.links().size()))
                .add(
                        Row.of(
                                es.redactado.menu.view.ActionButton.primary(
                                        ASK_BIRTH, "Birth date"),
                                es.redactado.menu.view.ActionButton.secondary(OPEN_LINKS, "Links"),
                                es.redactado.menu.view.ActionButton.danger(ASK_REMOVE, "Remove")))
                .build(ctx);
    }

    private Container role(MenuContext ctx, Profile profile) {
        // requireLong rather than a cast: the id came out of a component id, which a client
        // can edit, and a malformed one has to fail as a bad request rather than as a
        // NumberFormatException from somewhere unrelated.
        long roleId = ctx.requireLong(0);
        Profile.Role found =
                profile.roles().stream()
                        .filter(candidate -> candidate.id() == roleId)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new es.redactado.menu.api.UserFacingException(
                                                MessageKeys.ERROR_UNKNOWN_VIEW));
        return MenuBuilder.create(ID)
                .add(Text.title(found.name()))
                .add(Field.of("Permissions", Integer.toString(found.permissions())))
                .add(Text.small(found.description()))
                .add(Row.of(Nav.back()))
                .build(ctx);
    }

    private Container links(MenuContext ctx, Profile profile) {
        return MenuBuilder.create(ID)
                .add(Text.title("Links"))
                .add(
                        Pager.of(
                                "links",
                                profile.links(),
                                5,
                                link -> Text.of(link.label() + ": " + link.url())))
                .add(
                        Row.of(
                                es.redactado.menu.view.ActionButton.primary(ASK_LINK, "Add link"),
                                Nav.back()))
                .build(ctx);
    }

    private Container confirmRemove(MenuContext ctx, Profile profile) {
        return MenuBuilder.create(ID)
                .add(Text.title("Remove profile"))
                .add(Confirm.of(Text.of(CONFIRM_TEXT), DO_REMOVE).danger())
                .add(Row.of(Nav.back()))
                .build(ctx);
    }

    private CompletableFuture<Void> askBirth(
            MenuContext ctx,
            net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        ModalForm form = ModalForm.create(ctx, SAVE_BIRTH, "Birth date");
        form.shortField("birth", BIRTH_LABEL).placeholder(BIRTH_PLACEHOLDER);
        showModal(ctx, form.build());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> askLink(
            MenuContext ctx,
            net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        ModalForm form = ModalForm.create(ctx, SAVE_LINK, "Add link");
        form.shortField("label", LINK_LABEL);
        form.shortField("url", LINK_URL_LABEL);
        showModal(ctx, form.build());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Stores a new birth date and redraws.
     *
     * @param ctx the context of the submission
     * @param event the JDA modal event
     * @return a future completing when the write is stored and the view redrawn
     */
    private CompletableFuture<Void> saveBirth(
            MenuContext ctx, net.dv8tion.jda.api.events.interaction.ModalInteractionEvent event) {
        return write(ctx, () -> service.saveBirthBlocking(keyOf(ctx), answer(event, "birth")));
    }

    /**
     * A write that goes to the service on the executor, then invalidates the cache entry and
     * redraws.
     *
     * <p>The invalidation waits for the write to settle rather than happening before it.
     * Invalidating first would leave a window in which a render reloaded the old value and
     * cached it again, so the redraw would show the value the user had replaced a moment earlier.
     *
     * @param ctx the context of the interaction
     * @param write the blocking call, already wrapped in its own future by {@code supply}
     * @return a future completing when the write is stored and the view redrawn
     */
    private CompletableFuture<Void> write(
            MenuContext ctx, java.util.function.Supplier<Object> write) {
        long key = keyOf(ctx);
        return cache.invalidateAfter(executor.supply(write), key)
                .thenCompose(ignored -> refresh(ctx));
    }

    /** One field of a submitted form, trimmed and never null. */
    private static String answer(
            net.dv8tion.jda.api.events.interaction.ModalInteractionEvent event, String field) {
        return ModalForm.read(event).getOrDefault(field, "");
    }

    /** Stores a new link the same way a birth date is, so the two cannot drift apart. */
    private CompletableFuture<Void> saveLink(
            MenuContext ctx, net.dv8tion.jda.api.events.interaction.ModalInteractionEvent event) {
        return write(
                ctx,
                () ->
                        service.addLinkBlocking(
                                keyOf(ctx), answer(event, "label"), answer(event, "url")));
    }

    /** Removes the profile, which the confirmation view asked about first. */
    private CompletableFuture<Void> removeProfile(
            MenuContext ctx,
            net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        long key = keyOf(ctx);
        return cache.invalidateAfter(executor.supply(() -> service.removeBlocking(key)), key)
                // ROOT rather than PUSH: the profile this message was about no longer exists,
                // so the view it is showing should not stay on the history behind it.
                .thenCompose(
                        ignored ->
                                ctx.navigate(
                                        NavigationMode.ROOT, new NavEntry(ID, HOME, List.of())));
    }

    /** Pushes one of this menu's views, keeping this one underneath for Back. */
    private static CompletableFuture<Void> push(MenuContext ctx, String view) {
        return ctx.navigate(NavigationMode.PUSH, new NavEntry(ID, view, List.of()));
    }

    /**
     * The view this menu is showing.
     *
     * <p>Not the action: a click names the button pressed rather than the screen it was
     * pressed on, so the default implementation would record "nav" or "ask_birth" and Back
     * would render an action this menu does not have. A role row records "role" with its
     * params, which is right, and is kept for the same reason.
     */
    @Override
    public NavEntry currentView(MenuContext ctx) {
        String action = ctx.action();
        String view =
                switch (action) {
                    case ROLE, LINKS, CONFIRM_REMOVE -> action;
                    default -> HOME;
                };
        return new NavEntry(ID, view, ctx.params());
    }

    /**
     * Closes the executor this menu created.
     *
     * <p>A menu with its own virtual executor owns it and has to shut it down; a menu over
     * the host's pool borrows it and this does nothing harmful either way, because
     * {@link MenuExecutor#close()} only closes what the executor owns.
     */
    @Override
    public void close() {
        executor.close();
    }

    private static long keyOf(MenuContext ctx) {
        return Long.parseLong(ctx.userId());
    }

    /** One profile, as the service holds it. */
    public record Profile(long id, String birthDate, List<Role> roles, List<Link> links) {

        /** A role, with the bit field the fake service made up. */
        public record Role(long id, String name, int permissions, String description) {}

        /** One link on a profile. */
        public record Link(String label, String url) {}
    }
}
