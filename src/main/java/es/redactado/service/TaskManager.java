package es.redactado.service;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskManager implements IService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManager.class);

    private ScheduledExecutorService scheduler;

    @Override
    public void init() {
        scheduler =
                Executors.newScheduledThreadPool(
                        Runtime.getRuntime().availableProcessors(),
                        r -> {
                            Thread t = new Thread(r, "task-scheduler");
                            t.setDaemon(true);
                            return t;
                        });
        LOGGER.info("TaskManager initialized");
    }

    @Override
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("TaskManager shut down");
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, scheduler);
    }

    public <T> CompletableFuture<T> runAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return task.call();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                scheduler);
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return scheduler.schedule(task, delay, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(
            Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(
            Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return scheduler.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }
}
