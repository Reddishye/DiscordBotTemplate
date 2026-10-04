package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.Msg;
import es.redactado.menu.api.ValidationResult;
import es.redactado.menu.api.Validator;
import es.redactado.menu.core.Messages;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One built menu, rendered by eight threads at once.
 *
 * <p>The promise a menu declared with the DSL makes is that it is an ordinary immutable menu,
 * and the only way that promise could be false is if a render wrote to something the next
 * render reads: the element array, the action table, the view map. The framework renders on
 * the menu executor, so this is a real possibility rather than a theoretical one.
 *
 * <p>Each thread has its own context with its own user id, and the loader derives its model
 * from that id, so a render that leaked state from another thread would produce the wrong
 * text and be caught rather than merely being unlikely.
 */
class SimpleMenuThreadSafetyTest {

    private static final int THREADS = 8;
    private static final int RENDERS = 1_000;

    @Test
    @DisplayName("eight threads rendering one menu each get their own correct output")
    void concurrentRendersAreIndependent() throws Exception {
        Menu menu =
                Menus.simple(
                                "shop",
                                ctx -> CompletableFuture.completedFuture(new Model(ctx.userId())))
                        .tone(es.redactado.menu.preset.Tone.INFO)
                        .home(
                                v ->
                                        v.message(Msg.key("menu.nav.back"))
                                                .text(scope -> "Total: " + scope.data().total())
                                                .field(
                                                        Msg.literal("User"),
                                                        scope -> scope.data().user())
                                                .divider()
                                                .list(
                                                        "items",
                                                        scope ->
                                                                List.of(
                                                                        scope.data().user(),
                                                                        "second"),
                                                        5,
                                                        String::toString)
                                                .row(
                                                        r ->
                                                                r.primary(
                                                                                "add",
                                                                                Msg.literal("Add"),
                                                                                click ->
                                                                                        click
                                                                                                .done())
                                                                        .and()
                                                                        .secondary(
                                                                                "remove",
                                                                                Msg.literal(
                                                                                        "Remove"),
                                                                                click ->
                                                                                        click
                                                                                                .done())))
                        .view("detail", v -> v.text(scope -> "Detail for " + scope.data().user()))
                        .build();

        CountDownLatch start = new CountDownLatch(1);
        List<String> failures = new CopyOnWriteArrayList<>();
        List<Thread> workers = new java.util.ArrayList<>();

        for (int t = 0; t < THREADS; t++) {
            String user = "user-" + t;
            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    start.await();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                es.redactado.menu.api.MenuContext ctx = context(user);
                                for (int i = 0; i < RENDERS; i++) {
                                    Container container = menu.render(ctx).join();
                                    String text = textOf(container);
                                    if (!text.contains("Total: " + total(user))
                                            || !text.contains(user)
                                            || text.contains("user-" + otherThan(user))) {
                                        failures.add(user + " rendered " + text);
                                        return;
                                    }
                                    if (i == 0) {
                                        ValidationResult result = Validator.validate(container);
                                        if (!result.isValid()) {
                                            failures.add(user + " invalid: " + result.errors());
                                        }
                                        if (container.getComponents().size()
                                                > Limits.MAX_CONTAINER_CHILDREN) {
                                            failures.add(user + " too many children");
                                        }
                                    }
                                }
                            },
                            "simple-menu-render-" + t);
            workers.add(worker);
            worker.start();
        }

        start.countDown();
        for (Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(60));
        }

        assertThat(failures).as("no render may see another render's model").isEmpty();
        assertThat(workers)
                .as("every worker finished rather than hanging on shared state")
                .allMatch(worker -> !worker.isAlive());
    }

    /** Another thread's user, so a leak is visible as a name that should not be there. */
    private static String otherThan(String user) {
        int index = Integer.parseInt(user.substring("user-".length()));
        return String.valueOf((index + 1) % THREADS);
    }

    private static int number(String user) {
        return Integer.parseInt(user.substring("user-".length()));
    }

    /** The total the model reports for a user, which is derived rather than the id itself. */
    private static int total(String user) {
        return number(user) * 2;
    }

    private record Model(String user) {
        int total() {
            return number(user) * 2;
        }
    }

    private static String textOf(Container container) {
        StringBuilder out = new StringBuilder();
        for (Object child : container.getComponents()) {
            if (child instanceof TextDisplay display) {
                out.append(display.getContent()).append('\n');
            }
        }
        return out.toString();
    }

    /** One context per thread, sharing nothing with the others. */
    private static es.redactado.menu.api.MenuContext context(String user) {
        es.redactado.menu.api.MenuContext ctx = mock(es.redactado.menu.api.MenuContext.class);
        when(ctx.menuId()).thenReturn("shop");
        when(ctx.action()).thenReturn("home");
        when(ctx.params()).thenReturn(List.of());
        when(ctx.userId()).thenReturn(user);
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        when(ctx.session()).thenReturn(new es.redactado.menu.api.Session());
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(Locale.ENGLISH, (String) all[0], args);
                        });
        return ctx;
    }
}
