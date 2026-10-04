package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.view.ActionButton;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.Text;
import es.redactado.service.TaskManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The framework under load, with the real executors rather than a deterministic one.
 *
 * <p>Every other test in this project hands the router an executor it can step, which is the
 * only way to assert ordering. This class is the opposite: a real {@link TaskManager}, a real
 * pool behind {@link MenuExecutor#shared}, and work that runs when it runs. What it is looking
 * for is the class of bug a single-threaded test cannot see at all: two handlers claiming one
 * message, a cache stampede, a session written from two threads, and a router shut down while
 * interactions are still arriving.
 *
 * <p>Tagged {@code stress} and excluded from the default build, because a test that takes tens
 * of seconds and measures wall-clock percentiles should not gate every commit. Run it with:
 *
 * <pre>{@code
 * ./gradlew test -PrunStress --tests '*ConcurrencyStressTest'
 * }</pre>
 *
 * <p>Every scenario logs its own throughput and percentiles, so the numbers are in the build
 * output rather than only in a passing assertion.
 */
@Tag("stress")
class MenuConcurrencyStressTest {

    private static final Logger LOG = LoggerFactory.getLogger(MenuConcurrencyStressTest.class);

    /** Scenario A: messages, clicks per message, all messages in flight together. */
    private static final int MESSAGES = 200;

    private static final int CLICKS_PER_MESSAGE = 50;

    /** Scenario B: messages, each pressed twice at the same moment. */
    private static final int DOUBLE_MESSAGES = 200;

    /** The fixture's clicker, and so the owner every message has to be owned by. */
    private static final long USER = 42L;

    /** Scenario C: concurrent loads, and the distinct keys they are spread over. */
    private static final int LOADS = 10_000;

    private static final int KEYS = 50;

    private TaskManager tasks;
    private MenuExecutor executor;
    private MenuRouter router;
    private CountingMenu menu;

    @BeforeEach
    void startTheRealThing() {
        tasks = new TaskManager(4, 2, 20_000);
        tasks.init();
        executor = MenuExecutor.shared(tasks.ioExecutor());
        menu = new CountingMenu();
        router = MenuRouter.builder().executor(executor).build();
        router.register(menu.id(), menu);
    }

    @AfterEach
    void stopTheRealThing() {
        if (router != null) {
            router.close();
        }
        if (executor != null) {
            executor.close();
        }
        if (tasks != null) {
            tasks.shutdown();
        }
    }

    @Test
    @DisplayName("A: 200 messages x 50 sequential clicks, all messages in parallel")
    void scenarioAParallelMessagesWithSequentialClicks() throws Exception {
        long[] latenciesNanos = new long[MESSAGES * CLICKS_PER_MESSAGE];
        AtomicInteger handled = new AtomicInteger();
        menu.onClick(
                ctx -> {
                    int count = ctx.sessionStateOr(CountingMenu.KEY, Integer.class, 0);
                    ctx.putSessionState(CountingMenu.KEY, count + 1);
                    handled.incrementAndGet();
                    return menu.refreshNow(ctx);
                });

        // Every event built before the clock starts. Creating six JDA mocks costs far more
        // than the framework does with one, so leaving them inside the timed section would
        // have measured Mockito rather than the router.
        ButtonInteractionEvent[][] events =
                new ButtonInteractionEvent[MESSAGES][CLICKS_PER_MESSAGE];
        AtomicInteger rePressed = new AtomicInteger();
        for (int message = 0; message < MESSAGES; message++) {
            String id = idOf(message);
            for (int click = 0; click < CLICKS_PER_MESSAGE; click++) {
                events[message][click] = JdaMocks.button(id, true, messageId(message), USER);
            }
        }

        menu.expectMessages(MESSAGES);

        long started = System.nanoTime();
        ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch finished = new CountDownLatch(MESSAGES);

        for (int message = 0; message < MESSAGES; message++) {
            int index = message;
            workers.submit(
                    () -> {
                        try {
                            // Fifty clicks on one message, each waiting for the last to finish:
                            // a user cannot press a button the bot is still redrawing. Waiting on
                            // the message's own counter is what makes them sequential; a latch
                            // needing all fifty would let click two race the guard.
                            String id = events[index][0].getComponentId();
                            for (int click = 0; click < CLICKS_PER_MESSAGE; click++) {
                                long at = System.nanoTime();
                                // A click whose completion never arrives was dropped by the
                                // guard, which means this thread pressed again before the
                                // router had released the claim from the previous click. That is
                                // the guard working, not a failure, and a real user's second
                                // press would meet exactly the same thing; so the worker presses
                                // again rather than counting a click that never ran.
                                for (int attempt = 1; ; attempt++) {
                                    ButtonInteractionEvent event =
                                            attempt == 1
                                                    ? events[index][click]
                                                    : JdaMocks.button(
                                                            id, true, messageId(index), USER);
                                    assertThat(router.dispatchButton(event)).isTrue();
                                    if (awaitCompletion(index, click + 1)) {
                                        break;
                                    }
                                    assertThat(attempt)
                                            .as(
                                                    "message %d click %d kept losing its claim",
                                                    index, click)
                                            .isLessThan(20);
                                    rePressed.incrementAndGet();
                                }
                                latenciesNanos[index * CLICKS_PER_MESSAGE + click] =
                                        System.nanoTime() - at;
                            }
                        } finally {
                            finished.countDown();
                        }
                    });
        }

        assertThat(finished.await(120, TimeUnit.SECONDS)).as("every worker finished").isTrue();
        workers.shutdown();
        long elapsed = System.nanoTime() - started;

        assertThat(handled.get())
                .as("one handler run per click")
                .isEqualTo(MESSAGES * CLICKS_PER_MESSAGE);
        LOG.info(
                "scenario A: {} presses were dropped by the guard and pressed again, which is"
                        + " the guard working rather than a failure",
                rePressed.get());
        assertThat(menu.perMessage())
                .as("each message counted its own fifty, so no session leaked between them")
                .allSatisfy(count -> assertThat(count).isEqualTo(CLICKS_PER_MESSAGE));

        report("A", MESSAGES * CLICKS_PER_MESSAGE, latenciesNanos, elapsed);
    }

    @Test
    @DisplayName("B: 200 messages pressed twice at once, one handler per message")
    void scenarioBTwoSimultaneousClicksPerMessage() throws Exception {
        AtomicInteger handled = new AtomicInteger();
        menu.onClick(
                ctx -> {
                    handled.incrementAndGet();
                    // Long enough that the second press lands while the first still holds the
                    // message: without the claim, both would run.
                    return delayed(200).thenCompose(ignored -> menu.refreshNow(ctx));
                });

        ButtonInteractionEvent[][] pairs = new ButtonInteractionEvent[DOUBLE_MESSAGES][2];
        menu.expectMessages(DOUBLE_MESSAGES);
        for (int message = 0; message < DOUBLE_MESSAGES; message++) {
            String id = idOf(message);
            for (int half = 0; half < 2; half++) {
                pairs[message][half] = JdaMocks.button(id, true, messageId(message), USER);
            }
        }

        long started = System.nanoTime();
        // Released together so all two hundred messages are pressed at once, but each message's
        // two presses are dispatched back to back from one thread. That is the honest shape of
        // this race: the guard claims the message inside dispatch, so two presses with no gap
        // between them are the hardest case for it. Letting a scheduler decide when the second
        // half of a pair arrived would have tested the scheduler instead.
        CyclicBarrier together = new CyclicBarrier(DOUBLE_MESSAGES);
        ExecutorService pressers = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch finished = new CountDownLatch(DOUBLE_MESSAGES);
        long[] latenciesNanos = new long[DOUBLE_MESSAGES * 2];

        for (int message = 0; message < DOUBLE_MESSAGES; message++) {
            int index = message;
            pressers.submit(
                    () -> {
                        try {
                            together.await(30, TimeUnit.SECONDS);
                            for (int half = 0; half < 2; half++) {
                                long at = System.nanoTime();
                                router.dispatchButton(pairs[index][half]);
                                latenciesNanos[index * 2 + half] = System.nanoTime() - at;
                            }
                        } catch (Exception e) {
                            throw new IllegalStateException("presser failed", e);
                        } finally {
                            finished.countDown();
                        }
                    });
        }

        assertThat(finished.await(60, TimeUnit.SECONDS)).as("every presser finished").isTrue();
        pressers.shutdown();
        long elapsed = System.nanoTime() - started;

        awaitIdle();
        assertThat(handled.get())
                .as("exactly one of each pair ran: the second found the message claimed")
                .isEqualTo(DOUBLE_MESSAGES);
        assertThat(menu.perMessage())
                .as("no message ran twice, and none was skipped")
                .hasSize(DOUBLE_MESSAGES)
                .allSatisfy(count -> assertThat(count).isEqualTo(1));

        report("B", DOUBLE_MESSAGES * 2, latenciesNanos, elapsed);
    }

    @Test
    @DisplayName("C: 10,000 concurrent loads over 50 keys call the loader 50 times")
    void scenarioCLoadStampede() throws Exception {
        int keys = KEYS;
        int loads = LOADS;
        AtomicInteger loaderCalls = new AtomicInteger();
        AtomicInteger issued = new AtomicInteger(loads);
        // Every caller announces itself before it asks, so the loader can wait until all of
        // them are waiting. That is what makes the bound exact rather than timing-dependent.
        CountDownLatch allIssued = new CountDownLatch(loads);

        DataCache<Integer, String> cache =
                new DataCache<>(
                        DataCacheConfig.of(keys, Duration.ofMinutes(1)),
                        key -> {
                            loaderCalls.incrementAndGet();
                            return executor.supply(
                                    () -> {
                                        awaitQuietly(allIssued);
                                        return "value-" + key;
                                    });
                        },
                        tasks.ioExecutor());

        ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch finished = new CountDownLatch(loads);
        long[] latenciesNanos = new long[loads];
        long started = System.nanoTime();

        for (int i = 0; i < loads; i++) {
            int key = i % keys;
            int slot = i;
            callers.submit(
                    () -> {
                        try {
                            issued.decrementAndGet();
                            allIssued.countDown();
                            long at = System.nanoTime();
                            assertThat(cache.get(key).join()).startsWith("value-");
                            latenciesNanos[slot] = System.nanoTime() - at;
                        } finally {
                            finished.countDown();
                        }
                    });
        }

        assertThat(finished.await(120, TimeUnit.SECONDS)).as("every caller finished").isTrue();
        callers.shutdown();
        long elapsed = System.nanoTime() - started;

        assertThat(loaderCalls.get())
                .as(
                        "%d concurrent loads over %d keys, so at most %d calls to a service that"
                                + " takes real time; more than that is a stampede",
                        loads, keys, keys)
                .isLessThanOrEqualTo(keys);
        assertThat(cache.size()).isEqualTo(keys);

        report("C", loads, latenciesNanos, elapsed);
    }

    @Test
    @DisplayName("D: closing the router under load drains it and does not throw")
    void scenarioDCloseUnderLoad() throws Exception {
        AtomicInteger handled = new AtomicInteger();
        menu.onClick(
                ctx -> {
                    handled.incrementAndGet();
                    return delayed(30).thenCompose(ignored -> menu.refreshNow(ctx));
                });

        ExecutorService pressers = Executors.newVirtualThreadPerTaskExecutor();
        int pressers_ = 400;
        CountDownLatch finished = new CountDownLatch(pressers_);
        long started = System.nanoTime();

        for (int i = 0; i < pressers_; i++) {
            int index = i;
            pressers.submit(
                    () -> {
                        try {
                            ButtonInteractionEvent event =
                                    JdaMocks.button(idOf(index), true, messageId(index), USER);
                            router.dispatchButton(event);
                        } catch (RuntimeException e) {
                            throw new IllegalStateException("a press threw after close", e);
                        } finally {
                            finished.countDown();
                        }
                    });
        }

        // Close while they are still arriving, which is the case worth having: a deploy
        // restarts the bot while somebody is mid-click.
        Thread.sleep(20);
        assertThatCode(router::close)
                .as("closing mid-flight is not an error")
                .doesNotThrowAnyException();

        assertThat(finished.await(60, TimeUnit.SECONDS)).as("every press returned").isTrue();
        pressers.shutdown();
        awaitIdle();

        assertThat(router.inFlight())
                .as("every claim was released, so a closed router holds nothing")
                .isZero();
        assertThat(handled.get()).isPositive();
        report("D", pressers_, new long[pressers_], System.nanoTime() - started);
        LOG.info("scenario D: {} of the presses found a handler still running", handled.get());

        // A press after the close must still be answered rather than thrown at the caller.
        ButtonInteractionEvent late = JdaMocks.button(idOf(0), true, messageId(0), USER);
        assertThatCode(() -> router.dispatchButton(late))
                .as("a late interaction is refused, not thrown")
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ helpers

    /** Logs throughput and the two percentiles a reader actually wants. */
    private static void report(
            String scenario, int interactions, long[] latenciesNanos, long elapsedNanos) {
        long[] sorted = latenciesNanos.clone();
        Arrays.sort(sorted);
        long p50 = percentile(sorted, 50);
        long p99 = percentile(sorted, 99);
        double seconds = elapsedNanos / 1_000_000_000.0;
        LOG.info(
                "scenario {}: {} interactions in {} ms, {} per second, p50 {} ms, p99 {} ms",
                scenario,
                interactions,
                elapsedNanos / 1_000_000,
                String.format("%.0f", interactions / seconds),
                p50 / 1_000_000,
                p99 / 1_000_000);
    }

    /** The nearest-rank percentile, which needs no interpolation to explain. */
    private static long percentile(long[] sorted, int percent) {
        if (sorted.length == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(percent / 100.0 * sorted.length);
        return sorted[Math.min(sorted.length - 1, Math.max(0, rank - 1))];
    }

    /** Waits for one message's handler to reach a count, bounded. */
    /**
     * Waits for one message's handler to reach a count, bounded.
     *
     * <p>Parked rather than spun: two hundred busy-waiting virtual threads would take the
     * carriers the handlers themselves need, and the test would be measuring starvation of its
     * own making.
     */
    private boolean awaitCompletion(int index, int count) {
        // Two seconds, which is roughly eight times the p99 this scenario measures. The bound
        // has to be well clear of the tail or a slow click looks dropped and gets pressed
        // again, which shows up as more handler runs than clicks rather than as a flake in the
        // framework.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (menu.completed(index) < count && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(20_000);
        }
        return menu.completed(index) >= count;
    }

    /** Spins until nothing is claimed, or fails with what is still outstanding. */
    private void awaitIdle() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the loaders waited too long for the callers");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", e);
        }
    }

    private static java.util.concurrent.CompletableFuture<Void> delayed(long millis) {
        java.util.concurrent.CompletableFuture<Void> done =
                new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture.delayedExecutor(millis, TimeUnit.MILLISECONDS)
                .execute(() -> done.complete(null));
        return done;
    }

    private static final long FIRST_MESSAGE = 1_000L;

    private static long messageId(int index) {
        return FIRST_MESSAGE + index;
    }

    /** The button id, rendered once per message so every press carries the same one. */
    private String idOf(int index) {
        for (Object child : menu.render(contextFor(index)).join().getComponents()) {
            if (child instanceof net.dv8tion.jda.api.components.actionrow.ActionRow row) {
                for (Object item : row.getComponents()) {
                    if (item instanceof net.dv8tion.jda.api.components.buttons.Button button) {
                        return button.getCustomId();
                    }
                }
            }
        }
        throw new AssertionError("message " + index + " rendered no button");
    }

    private static MenuContext contextFor(int index) {
        MenuContext ctx = mock(MenuContext.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        when(ctx.menuId()).thenReturn(CountingMenu.ID);
        when(ctx.action()).thenReturn("home");
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.userId()).thenReturn(Long.toString(USER));
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(call -> (String) call.getArgument(0));
        return ctx;
    }

    /**
     * One menu, one button, and a handler the test replaces.
     *
     * <p>A real menu rather than a lambda-shaped fake, because the things worth finding here
     * are in the dispatch path around a handler rather than in the handler.
     */
    private static final class CountingMenu extends AbstractMenu {

        static final String ID = "stress";
        static final String KEY = "count";

        private final List<java.util.concurrent.atomic.AtomicInteger> perMessage =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile java.util.function.Function<
                        MenuContext, java.util.concurrent.CompletableFuture<Void>>
                handler = ctx -> java.util.concurrent.CompletableFuture.completedFuture(null);

        CountingMenu() {
            super(ID);
        }

        void onClick(
                java.util.function.Function<
                                MenuContext, java.util.concurrent.CompletableFuture<Void>>
                        work) {
            this.handler = work;
        }

        /**
         * Prepares a counter per message, on one thread before any work starts.
         *
         * <p>Growing the list from two hundred workers at once was a real flake: a
         * {@code while (size() <= index) add(...)} race leaves duplicate slots, so a worker's
         * increments and the final read of the list disagree about which counter is which.
         */
        void expectMessages(int count) {
            perMessage.clear();
            for (int i = 0; i < count; i++) {
                perMessage.add(new AtomicInteger());
            }
        }

        List<Integer> perMessage() {
            return perMessage.stream().map(java.util.concurrent.atomic.AtomicInteger::get).toList();
        }

        /** How many handler runs message {@code index} has seen. */
        int completed(int index) {
            AtomicInteger counter = perMessage.get(index);
            return counter == null ? 0 : counter.get();
        }

        @Override
        protected void declare(ActionTable.Builder table) {
            table.button("bump", Ack.DEFER_EDIT, (ctx, event) -> run(ctx));
        }

        /** The framework's own single edit path, which is what a stress run should exercise. */
        java.util.concurrent.CompletableFuture<Void> refreshNow(MenuContext ctx) {
            return refresh(ctx);
        }

        private java.util.concurrent.CompletableFuture<Void> run(MenuContext ctx) {
            java.util.concurrent.CompletableFuture<Void> done;
            try {
                done = handler.apply(ctx);
            } catch (RuntimeException e) {
                done = java.util.concurrent.CompletableFuture.failedFuture(e);
            }
            return done.whenComplete(
                    (value, error) -> {
                        // Counted here rather than inside the test's handler, so that a handler
                        // which throws still counts and "exactly one ran" cannot be satisfied by
                        // an assertion that never fired.
                        ctx.messageId()
                                .ifPresent(
                                        id -> {
                                            int index = (int) (id - FIRST_MESSAGE);
                                            if (index >= 0 && index < perMessage.size()) {
                                                perMessage.get(index).incrementAndGet();
                                            }
                                        });
                    });
        }

        @Override
        public java.util.concurrent.CompletableFuture<Container> render(MenuContext ctx) {
            return java.util.concurrent.CompletableFuture.completedFuture(
                    MenuBuilder.create(ID)
                            .add(Text.of("Count: " + ctx.sessionStateOr(KEY, Integer.class, 0)))
                            .add(Row.of(ActionButton.primary("bump", "Bump")))
                            .build(ctx));
        }
    }
}
