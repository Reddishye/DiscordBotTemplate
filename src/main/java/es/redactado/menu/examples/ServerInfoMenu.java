package es.redactado.menu.examples;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Msg;
import es.redactado.menu.simple.Menus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A menu whose views depend on data, to show a loader and a select working together.
 *
 * <p>Three things worth copying from here:
 *
 * <ul>
 *   <li>The service is the slow part and is behind a {@link CompletableFuture}. The loader is
 *       the only place that touches it, so a render is still a pure function of its context
 *       and its model, and a failure becomes the framework's localized error rather than a
 *       stack trace in front of a user.
 *   <li>The paged list is a {@code list(...)} of the whole list, paged by the framework, with
 *       the page kept in the session, so paging survives every redraw.
 *   <li>The select stores the chosen section and refreshes, rather than opening a view per
 *       section: a filter inside one screen is not navigation, and putting it on the history
 *       would make Back walk through sections.
 * </ul>
 *
 * <p>Not registered by default.
 */
public final class ServerInfoMenu {

    /** The menu id. */
    public static final String ID = "server_info";

    /** The only view: the select changes what it shows rather than where it is. */
    public static final String HOME = "home";

    /** Where the chosen section lives in the session. */
    public static final String SECTION = "section";

    private static final String SECTION_ACTION = "section";

    private static final String OVERVIEW = "overview";
    private static final String MEMBERS = "members";
    private static final String ROLES = "roles";

    private ServerInfoMenu() {}

    /**
     * Builds the menu over the given service.
     *
     * @param service where the data comes from; may be slow, and may fail
     * @return a menu, ready to be registered with the router
     */
    public static Menu build(ServerInfoService service) {
        return Menus.simple(ID, ctx -> service.load(ctx))
                .tone(es.redactado.menu.preset.Tone.INFO)
                .loadTimeout(java.time.Duration.ofSeconds(5))
                .home(
                        v ->
                                v.header(Msg.literal("Server"))
                                        .text(scope -> scope.data().name())
                                        .field(
                                                Msg.literal("Online"),
                                                scope -> String.valueOf(scope.data().online()))
                                        .select(
                                                SECTION_ACTION,
                                                Msg.literal("Show"),
                                                o ->
                                                        o.option(OVERVIEW, "Overview")
                                                                .option(MEMBERS, "Members")
                                                                .option(ROLES, "Roles")
                                                                .selected(OVERVIEW),
                                                ServerInfoMenu::show)
                                        .list(
                                                "members",
                                                scope -> scope.data().members(),
                                                5,
                                                member -> member.name() + " - " + member.role())
                                        .text(scope -> sectionBody(scope.ctx(), scope.data()))
                                        .row(
                                                r ->
                                                        r.secondary(
                                                                "ping",
                                                                "Ping",
                                                                click -> {
                                                                    click.reply(
                                                                            Msg.literal("Pong"));
                                                                    return click.done();
                                                                })))
                .build();
    }

    /**
     * The section the view shows below the select.
     *
     * <p>Stored in the session, so it is per user: two people in the same server can be
     * looking at different sections of the same message flow without fighting over it.
     */
    private static java.util.concurrent.CompletableFuture<Void> show(
            es.redactado.menu.simple.Pick pick) {
        List<String> values = pick.values();
        String section = values.isEmpty() ? OVERVIEW : values.getFirst();
        // session() rather than findSession(): the first choice on a fresh message is exactly
        // the case where there is no session to find yet.
        pick.ctx().session().putState(SECTION, section);
        return pick.refresh();
    }

    private static String sectionBody(MenuContext ctx, ServerInfo info) {
        String section = sectionOf(ctx);
        return switch (section) {
            case MEMBERS -> "Members listed above, " + info.members().size() + " of them.";
            case ROLES -> "Roles in use: " + String.join(", ", info.roles());
            default -> "Created " + info.created() + ", owner " + info.owner() + ".";
        };
    }

    private static String sectionOf(MenuContext ctx) {
        return ctx.findSession()
                .flatMap(session -> session.state(SECTION, String.class))
                .orElse(OVERVIEW);
    }

    /** One member of the server. */
    public record Member(String name, String role) {}

    /** What the view renders. */
    public record ServerInfo(
            String name,
            int online,
            String owner,
            String created,
            List<Member> members,
            List<String> roles) {

        /**
         * Checks the model against the limits the view will draw it into.
         *
         * @throws IllegalArgumentException if the data could not be rendered
         */
        public void check() {
            if (members.size() > 1_000) {
                throw new IllegalArgumentException(
                        "A server with " + members.size() + " members is too much for one menu");
            }
            if (name.length() > Limits.MAX_MODAL_TITLE_LENGTH) {
                throw new IllegalArgumentException("The server name is too long to show");
            }
        }
    }

    /**
     * Where the data comes from.
     *
     * <p>An interface so the example can be handed a fake that takes time and sometimes
     * fails, which is the only way to see the loader's behaviour without a real service.
     */
    public interface ServerInfoService {

        /**
         * Loads what the view needs.
         *
         * @param ctx the context of the current interaction
         * @return a future for the data; it may take as long as it likes
         */
        CompletableFuture<ServerInfo> load(MenuContext ctx);
    }

    /**
     * A service that pretends, slowly.
     *
     * <p>The delay is the point: it proves the menu is rendered on the menu executor rather
     * than on a JDA event thread, which is what keeps a slow service from tripping Discord's
     * three-second rule.
     */
    public static final class FakeService implements ServerInfoService {

        private static final ScheduledExecutorService TIMER =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "server-info-fake");
                            thread.setDaemon(true);
                            return thread;
                        });

        private final long delayMillis;

        /**
         * @param delayMillis how long a load pretends to take
         */
        public FakeService(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public CompletableFuture<ServerInfo> load(MenuContext ctx) {
            CompletableFuture<ServerInfo> pending = new CompletableFuture<>();
            TIMER.schedule(
                    () ->
                            pending.complete(
                                    new ServerInfo(
                                            "Example guild",
                                            128,
                                            "Ada",
                                            "2021-04-01",
                                            List.of(
                                                    new Member("Ada", "admin"),
                                                    new Member("Grace", "moderator"),
                                                    new Member("Alan", "member"),
                                                    new Member("Barbara", "member"),
                                                    new Member("Edsger", "member"),
                                                    new Member("Katherine", "moderator")),
                                            List.of("admin", "moderator", "member"))),
                    delayMillis,
                    TimeUnit.MILLISECONDS);
            return pending;
        }
    }

    /** The section names this menu offers, in the order the select lists them. */
    public static List<String> sections() {
        return List.of(OVERVIEW, MEMBERS, ROLES);
    }
}
