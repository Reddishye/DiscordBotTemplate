package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.ButtonHandler;
import es.redactado.menu.api.Done;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Throughput guard for the dispatch path.
 *
 * <p>Design target from the plan is constant per-interaction overhead: 1,000
 * clicks on 1,000 distinct messages must all run, and the total must stay far
 * below the ~50 s that a serial implementation would need for the same work.
 */
class MenuRouterThroughputTest {

    private static final int CLICKS = 1_000;
    private static final long HANDLER_DELAY_MILLIS = 50;
    private static final long BUDGET_MILLIS = 10_000;

    private static final class CountingMenu implements Menu {
        private final ButtonHandler handler;

        CountingMenu(ButtonHandler handler) {
            this.handler = handler;
        }

        @Override
        public String id() {
            return "m";
        }

        @Override
        public boolean shared() {
            return true;
        }

        @Override
        public Container render(MenuContext ctx) {
            return Container.of(TextDisplay.of("body"));
        }

        @Override
        public void actions(ActionTable.Builder table) {
            table.button("go", Ack.DEFER_EDIT, handler);
        }
    }

    @Test
    @DisplayName(
            "1,000 clicks on 1,000 messages each run exactly once and finish well inside the"
                    + " budget")
    void thousandClicks() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(CLICKS);
        AtomicInteger started = new AtomicInteger();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // The handler returns an incomplete future that a scheduler completes
        // later. The worker thread is never blocked, so the test measures the
        // framework's overhead rather than a sleep.
        ButtonHandler handler =
                (ctx, event) -> {
                    started.incrementAndGet();
                    CompletableFuture<Void> future = new CompletableFuture<>();
                    scheduler.schedule(
                            () -> {
                                future.complete(null);
                                done.countDown();
                            },
                            HANDLER_DELAY_MILLIS,
                            TimeUnit.MILLISECONDS);
                    return future;
                };

        MenuRouter router = new MenuRouter(MenuExecutor.virtual());
        router.register("m", new CountingMenu(handler));

        long before = System.nanoTime();
        for (int i = 0; i < CLICKS; i++) {
            ButtonInteractionEvent event =
                    JdaMocks.button("menu:m:go", false, i, JdaMocks.NO_OWNER);
            assertThat(router.dispatchButton(event)).isTrue();
        }

        assertThat(done.await(BUDGET_MILLIS * 2, TimeUnit.MILLISECONDS)).isTrue();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

        scheduler.shutdownNow();
        router.close();

        assertThat(started.get()).isEqualTo(CLICKS);
        assertThat(router.inFlight()).isZero();
        assertThat(elapsedMillis).isLessThan(BUDGET_MILLIS);
    }

    @Test
    @DisplayName("the executor runs a virtual thread per task")
    void virtualThreads() throws InterruptedException {
        CountDownLatch ran = new CountDownLatch(1);
        List<Thread> threads = new java.util.ArrayList<>();
        ButtonHandler handler =
                (ctx, event) -> {
                    synchronized (threads) {
                        threads.add(Thread.currentThread());
                    }
                    ran.countDown();
                    return Done.NOW;
                };

        MenuRouter router = new MenuRouter(MenuExecutor.virtual());
        router.register("m", new CountingMenu(handler));
        for (int i = 0; i < 8; i++) {
            router.dispatchButton(JdaMocks.button("menu:m:go", false, i, JdaMocks.NO_OWNER));
        }

        assertThat(ran.await(BUDGET_MILLIS, TimeUnit.MILLISECONDS)).isTrue();
        router.close();

        synchronized (threads) {
            assertThat(threads).hasSize(8).allMatch(Thread::isVirtual);
            assertThat(threads).allMatch(thread -> thread.getName().startsWith("menu-"));
            assertThat(threads.stream().map(Thread::getId).distinct().count())
                    .as("each task gets its own thread")
                    .isEqualTo(8);
        }
    }
}
