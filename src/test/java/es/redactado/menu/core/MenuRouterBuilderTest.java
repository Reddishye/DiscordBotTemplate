package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the builder's defaults and the rule for who closes what.
 *
 * <p>The rule is that a component is closed by whoever created it. A router that closed
 * a shared executor would break every other router using it, which is the kind of bug
 * that only appears in production with two features enabled and never in a test that
 * owns everything it builds.
 */
class MenuRouterBuilderTest {

    @Test
    @DisplayName("a router with nothing set works on the template defaults")
    void defaultsAreUsable() {
        try (MenuRouter router = TestRouters.create()) {
            router.register("m", new NoopMenu());

            assertThat(router.isRegistered("m")).isTrue();
            assertThat(router.executor()).as("the default executor is present").isNotNull();
        }
    }

    @Test
    @DisplayName("closing a router that created its own executor releases it")
    void closesWhatItCreated() {
        MenuRouter router = TestRouters.create();
        // The template default is a virtual-thread executor, which is closed by the
        // router. Closing twice must not fail, because shutdown paths in a bot often run
        // more than once.
        router.close();

        assertThatCode(() -> router.close()).as("close is safe twice").doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a caller-supplied executor is not closed by the router")
    void doesNotCloseSuppliedExecutor() {
        CountingExecutor service = new CountingExecutor();
        MenuRouter router = MenuRouter.builder().executor(MenuExecutor.shared(service)).build();

        router.close();

        assertThat(service.shutdowns.get())
                .as("the router did not create this executor, so it must not close it")
                .isZero();
        service.shutdown();
    }

    @Test
    @DisplayName("a caller-supplied session store is not closed by the router")
    void doesNotCloseSuppliedSessions() {
        SessionStore sessions = new SessionStore(SessionConfig.defaults());
        MenuRouter router = MenuRouter.builder().sessions(sessions).build();
        sessions.getOrCreate(42L);

        router.close();

        assertThat(sessions.find(42L))
                .as("the store outlives the router, so its contents must too")
                .isPresent();
        sessions.close();
    }

    @Test
    @DisplayName("every setter returns the builder, so a router is one expression")
    void settersAreFluent() {
        SessionStore sessions = new SessionStore(SessionConfig.defaults());
        PresetResolver presets =
                new PresetResolver(
                        new es.redactado.menu.preset.PresetRegistry(),
                        new es.redactado.menu.preset.InMemoryPresetPreferences(),
                        false);

        MenuRouter router =
                MenuRouter.builder()
                        .executor(MenuExecutor.virtual())
                        .sessions(sessions)
                        .messages(Messages.standard())
                        .presets(presets)
                        .build();

        assertThat(router.isRegistered("nothing")).isFalse();
        router.close();
        sessions.close();
    }

    /**
     * A real executor that counts shutdowns, so ownership is observable.
     *
     * <p>Extends {@link java.util.concurrent.AbstractExecutorService} because the
     * interface itself carries eight methods that have nothing to do with this test.
     */
    private static final class CountingExecutor
            extends java.util.concurrent.AbstractExecutorService {
        private final java.util.concurrent.ExecutorService delegate =
                Executors.newSingleThreadExecutor();
        private final AtomicInteger shutdowns = new AtomicInteger();

        @Override
        public void shutdown() {
            shutdowns.incrementAndGet();
            delegate.shutdown();
        }

        @Override
        public java.util.List<Runnable> shutdownNow() {
            shutdowns.incrementAndGet();
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit)
                throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }
    }

    /** A menu that does nothing, for tests about the router rather than a menu. */
    private static final class NoopMenu implements es.redactado.menu.api.Menu {

        @Override
        public String id() {
            return "m";
        }

        @Override
        public java.util.concurrent.CompletableFuture<
                        net.dv8tion.jda.api.components.container.Container>
                render(es.redactado.menu.api.MenuContext ctx) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void actions(es.redactado.menu.api.ActionTable.Builder table) {}
    }
}
