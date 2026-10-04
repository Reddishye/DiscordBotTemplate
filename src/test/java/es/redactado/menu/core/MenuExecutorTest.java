package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MenuExecutorTest {

    private MenuExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.close();
        }
    }

    @Test
    @DisplayName("supply produces a value on a virtual thread named menu-*")
    void supplyOnVirtualThread() {
        executor = MenuExecutor.virtual();
        AtomicReference<Thread> thread = new AtomicReference<>();
        AtomicReference<Boolean> virtual = new AtomicReference<>();

        String value =
                executor.supply(
                                () -> {
                                    thread.set(Thread.currentThread());
                                    virtual.set(Thread.currentThread().isVirtual());
                                    return "done";
                                })
                        .join();

        assertThat(value).isEqualTo("done");
        assertThat(virtual.get()).isTrue();
        assertThat(thread.get().getName()).startsWith("menu-");
    }

    @Test
    @DisplayName("supply turns an exception into a failed future")
    void supplyFailure() {
        executor = MenuExecutor.virtual();

        CompletableFuture<String> future =
                executor.supply(
                        () -> {
                            throw new IllegalStateException("boom");
                        });

        // join blocks until the task is done, so this is also the completion check.
        assertThatThrownBy(future::join).hasRootCauseMessage("boom");
    }

    @Test
    @DisplayName("run completes after the task and returns a void future")
    void runTask() {
        executor = MenuExecutor.virtual();
        AtomicReference<String> marker = new AtomicReference<>();

        CompletableFuture<Void> future = executor.run(() -> marker.set("ran"));

        assertThat(future.join()).isNull();
        assertThat(marker.get()).isEqualTo("ran");
    }

    @Test
    @DisplayName("run turns an exception into a failed future")
    void runFailure() {
        executor = MenuExecutor.virtual();

        CompletableFuture<Void> future =
                executor.run(
                        () -> {
                            throw new IllegalStateException("boom");
                        });

        assertThatThrownBy(future::join).hasRootCauseMessage("boom");
    }

    @Test
    @DisplayName("supply rejects with a failed future once the executor is closed")
    void supplyAfterClose() {
        executor = MenuExecutor.virtual();
        executor.close();

        CompletableFuture<String> future = executor.supply(() -> "x");

        assertThatThrownBy(future::join).hasRootCauseInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("close waits for in-flight work and returns promptly")
    void closeWaitsForWork() throws InterruptedException {
        executor = MenuExecutor.virtual();
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        CompletableFuture<String> pending =
                executor.supply(
                        () -> {
                            started.countDown();
                            try {
                                release.await(5, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return "late";
                        });
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        executor.close();
        release.countDown();

        // The task was interrupted by close, so it still produces a result.
        assertThat(pending.join()).isEqualTo("late");
    }

    @Test
    @DisplayName("a shared executor survives close, because the caller owns it")
    void sharedCloseLeavesTheExecutorAlone() {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        MenuExecutor shared = MenuExecutor.shared(pool);

        shared.close();

        assertThat(pool.isShutdown()).as("a borrowed pool must stay usable").isFalse();
        assertThat(shared.supply(() -> "still here").join()).isEqualTo("still here");
        pool.shutdownNow();
    }

    @Test
    @DisplayName("a shared executor runs tasks on its own threads, not on menu-* ones")
    void sharedRunsOnTheGivenPool() {
        ExecutorService pool =
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("pool-", 0).factory());
        MenuExecutor shared = MenuExecutor.shared(pool);

        AtomicReference<String> thread = new AtomicReference<>();
        shared.run(() -> thread.set(Thread.currentThread().getName())).join();

        assertThat(thread.get()).startsWith("pool-").doesNotStartWith("menu-");
        pool.shutdownNow();
    }

    @Test
    @DisplayName("supply and run both go through the shared executor")
    void supplyAndRunUseTheSharedExecutor() {
        ExecutorService pool =
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("pool-", 0).factory());
        MenuExecutor shared = MenuExecutor.shared(pool);
        AtomicReference<Thread> supplied = new AtomicReference<>();
        AtomicReference<Thread> ran = new AtomicReference<>();

        String value =
                shared.supply(
                                () -> {
                                    supplied.set(Thread.currentThread());
                                    return "v";
                                })
                        .join();
        shared.run(() -> ran.set(Thread.currentThread())).join();

        assertThat(value).isEqualTo("v");
        assertThat(supplied.get().getName()).startsWith("pool-");
        assertThat(ran.get().getName()).startsWith("pool-");
        pool.shutdownNow();
    }

    @Test
    @DisplayName("a shared executor that rejects surfaces the rejection to the caller")
    void sharedRejectionReachesTheCaller() {
        ExecutorService rejecting = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).when(rejecting).execute(any(Runnable.class));
        MenuExecutor shared = MenuExecutor.shared(rejecting);

        assertThatThrownBy(() -> shared.execute(() -> {}))
                .isInstanceOf(RejectedExecutionException.class);
        assertThatThrownBy(shared.supply(() -> "x")::join)
                .hasRootCauseInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("a null shared executor is refused at the call")
    void nullSharedExecutorIsRefused() {
        assertThatThrownBy(() -> MenuExecutor.shared(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("io");
    }

    @Test
    @DisplayName("the router exposes its executor")
    void routerExposesExecutor() {
        executor = MenuExecutor.virtual();
        MenuRouter router = TestRouters.withExecutor(executor);

        assertThat(router.executor()).isSameAs(executor);
        assertThat(router.executor().supply(() -> List.of(1, 2)).join()).containsExactly(1, 2);
    }
}
