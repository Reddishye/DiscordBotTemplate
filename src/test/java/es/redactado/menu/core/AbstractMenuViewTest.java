package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Render;
import es.redactado.menu.api.UserFacingException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Covers the load-then-render helper that keeps I/O out of rendering. */
class AbstractMenuViewTest {

    private static final long AWAIT_MS = 10_000;

    /** A menu that exposes {@link AbstractMenu#view} for testing. */
    private static final class ViewMenu extends AbstractMenu {
        private Duration timeout = Duration.ofSeconds(10);

        ViewMenu() {
            super("view");
        }

        @Override
        protected void declare(es.redactado.menu.api.ActionTable.Builder table) {}

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return Render.now(Container.of(TextDisplay.of("plain")));
        }

        @Override
        protected Duration loadTimeout() {
            return timeout;
        }

        <M> CompletableFuture<Container> run(
                MenuContext ctx,
                es.redactado.menu.api.Loader<M> loader,
                es.redactado.menu.api.Renderer<M> renderer) {
            return view(ctx, loader, renderer);
        }
    }

    private static Container container(String label) {
        return Container.of(TextDisplay.of(label));
    }

    private final ViewMenu menu = new ViewMenu();

    @Test
    @DisplayName("the loader runs first and the renderer receives its model")
    void loaderThenRenderer() {
        AtomicBoolean rendererCalled = new AtomicBoolean();

        Container result =
                menu.<String>run(
                                context(),
                                ctx -> CompletableFuture.completedFuture("model"),
                                (ctx, model) -> {
                                    rendererCalled.set(true);
                                    return container(model);
                                })
                        .join();

        assertThat(rendererCalled).isTrue();
        assertThat(result.getComponents()).hasSize(1);
    }

    @Test
    @DisplayName("the renderer is not called when the loader fails")
    void rendererSkippedOnLoaderFailure() {
        AtomicBoolean rendererCalled = new AtomicBoolean();

        CompletableFuture<Container> future =
                menu.<String>run(
                        context(),
                        ctx ->
                                CompletableFuture.failedFuture(
                                        new IllegalStateException("load failed")),
                        (ctx, model) -> {
                            rendererCalled.set(true);
                            return container(model);
                        });

        assertThat(future).isCompletedExceptionally();
        assertThat(rendererCalled).isFalse();
    }

    @Test
    @DisplayName("a loader that throws synchronously yields a failed future, not a throw")
    void syncThrowBecomesFailedFuture() {
        CompletableFuture<Container> future =
                menu.<String>run(
                        context(),
                        ctx -> {
                            throw new IllegalStateException("boom");
                        },
                        (ctx, model) -> container(model));

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::join).hasRootCauseMessage("boom");
    }

    @Test
    @DisplayName("a loader that never completes fails with the timeout message")
    void loaderTimeout() {
        menu.timeout = Duration.ofMillis(100);

        CompletableFuture<Container> future =
                menu.<String>run(
                        context(),
                        ctx -> new CompletableFuture<>(),
                        (ctx, model) -> container(model));

        Throwable error = failureOf(future);
        assertThat(error).isInstanceOf(UserFacingException.class);
        assertThat(error)
                .hasMessage(MessageKeys.ERROR_LOAD_TIMEOUT)
                .satisfies(
                        e ->
                                assertThat(
                                                Messages.standard()
                                                        .get(
                                                                Locale.ENGLISH,
                                                                MessageKeys.ERROR_LOAD_TIMEOUT))
                                        .isEqualTo("Loading took too long."));
    }

    @Test
    @DisplayName("the renderer runs after the loader completes on another thread")
    void waitsForAsyncLoader() throws InterruptedException {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicBoolean done = new AtomicBoolean();

        CompletableFuture<Container> future =
                menu.<String>run(
                        context(),
                        ctx -> {
                            return CompletableFuture.supplyAsync(
                                    () -> {
                                        try {
                                            release.await();
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                        return "late";
                                    });
                        },
                        (ctx, model) -> {
                            done.set(true);
                            return container(model);
                        });

        assertThat(done).isFalse();
        release.countDown();
        assertThat(future.join().getComponents()).hasSize(1);
        assertThat(done).isTrue();
    }

    private static Throwable failureOf(CompletableFuture<?> future) {
        try {
            future.get(AWAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            throw new AssertionError("Expected the future to fail");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (java.util.concurrent.ExecutionException e) {
            return e.getCause();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AssertionError("The future neither completed nor failed", e);
        }
    }

    private static MenuContext context() {
        return org.mockito.Mockito.mock(MenuContext.class, CALLS_REAL_METHODS);
    }
}
