package es.redactado.menu.examples;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Msg;
import es.redactado.menu.simple.Menus;
import es.redactado.menu.view.Confirm;
import es.redactado.menu.view.Text;

/**
 * A counter, to show state that lives in the session rather than in the menu.
 *
 * <p>Three things worth copying from here:
 *
 * <ul>
 *   <li>The count is read from and written to the session on every render, so it survives
 *       the redraw the framework does for every press, and it is per user because the
 *       session is.
 *   <li>A change is {@code refresh()}, not {@code go}: the view is already the right one, and
 *       pushing it again would put a duplicate on the history for Back to walk into.
 *   <li>A destructive action gets a confirmation view rather than a second press. The
 *       confirmation is a {@link Confirm}, dropped in through {@code custom}, because the DSL
 *       has no word for it and that is what the escape hatch is for.
 * </ul>
 *
 * <p>Not registered by default.
 */
public final class CounterMenu {

    /** The menu id. */
    public static final String ID = "counter";

    /** The view a menu opens on, and the only one with the buttons. */
    public static final String HOME = "home";

    /** The view asking before a reset. */
    public static final String CONFIRM = "confirm";

    /** Where the count lives, under this name, in the session of whoever pressed the button. */
    public static final String COUNT = "count";

    private static final String PLUS = "plus";
    private static final String MINUS = "minus";
    private static final String ASK_RESET = "ask_reset";
    private static final String DO_RESET = "do_reset";

    private CounterMenu() {}

    /**
     * Builds the menu.
     *
     * @return a menu, ready to be registered with the router
     */
    public static Menu build() {
        return Menus.simple(ID)
                .tone(es.redactado.menu.preset.Tone.SUCCESS)
                .home(
                        v ->
                                v.header(Msg.literal("Counter"))
                                        .text(scope -> "Count: " + count(scope.ctx()))
                                        .row(
                                                r ->
                                                        r.success(
                                                                        PLUS,
                                                                        "+1",
                                                                        CounterMenu::increment)
                                                                .and()
                                                                .secondary(
                                                                        MINUS,
                                                                        "-1",
                                                                        CounterMenu::decrement)
                                                                .and()
                                                                .danger(
                                                                        ASK_RESET,
                                                                        "Reset",
                                                                        click ->
                                                                                click.go(CONFIRM))))
                .view(
                        CONFIRM,
                        v ->
                                v.header(Msg.literal("Reset the counter"))
                                        .custom(
                                                Confirm.of(
                                                        Text.of(
                                                                "This sets the count back to"
                                                                        + " zero."),
                                                        DO_RESET))
                                        .row(r -> r.back()))
                .onClick(DO_RESET, CounterMenu::reset)
                .build();
    }

    private static java.util.concurrent.CompletableFuture<Void> increment(
            es.redactado.menu.simple.Click click) {
        write(click.ctx(), count(click.ctx()) + 1);
        return click.refresh();
    }

    private static java.util.concurrent.CompletableFuture<Void> decrement(
            es.redactado.menu.simple.Click click) {
        write(click.ctx(), count(click.ctx()) - 1);
        return click.refresh();
    }

    /** Zeroes the count and leaves the confirmation, rather than staying on it. */
    private static java.util.concurrent.CompletableFuture<Void> reset(
            es.redactado.menu.simple.Click click) {
        write(click.ctx(), 0);
        return click.back();
    }

    /**
     * The count, or zero for a session that has never been touched.
     *
     * <p>Read with {@code findSession}, because a session that does not exist yet is not an
     * error: it is a counter nobody has pressed.
     */
    private static int count(MenuContext ctx) {
        return ctx.findSession().flatMap(session -> session.state(COUNT, Integer.class)).orElse(0);
    }

    /**
     * Stores the count.
     *
     * <p>Written with {@code session()} rather than {@code findSession()}, because that is
     * the one that creates the session on first use. Reading with {@code findSession} and
     * writing with {@code session} is the pair worth remembering: the first press on a fresh
     * message is exactly the case where there is nothing to find.
     */
    private static void write(MenuContext ctx, int value) {
        ctx.session().putState(COUNT, value);
    }
}
