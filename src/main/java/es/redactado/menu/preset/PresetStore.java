package es.redactado.menu.preset;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the custom presets of a running bot: loads them, publishes them to a
 * {@link PresetRegistry}, and optionally reloads them when the files change.
 *
 * <p><strong>Last known good.</strong> A reload that produced an invalid preset does
 * not replace the valid one that came before it. A typo in one file therefore costs
 * that one preset's new values, not the whole custom set, and the bot keeps rendering
 * the last version it understood. The alternative, applying whatever loaded, turns a
 * half-saved file into a broken bot for as long as the mistake stands.
 *
 * <p>Only a file that has stopped existing is dropped. That is the difference between
 * "this preset is wrong" and "this preset is gone", and conflating them would delete a
 * working preset every time its author made a mistake.
 *
 * <p><strong>Reloads are serialized</strong> on this store, so two reloads cannot
 * interleave and a slow watcher reload cannot race a manual one. Readers are not
 * affected: {@link PresetRegistry} is lock free, and only the swap happens under the
 * lock.
 *
 * <p>The watcher is optional and disposable. If it cannot start, the bot runs with
 * whatever was loaded at startup and says so in the log; a missing or unreadable
 * directory is never a reason to fail startup.
 */
public final class PresetStore implements AutoCloseable {

    /** Name of the single watching thread, so it is recognisable in a thread dump. */
    public static final String WATCHER_THREAD_NAME = "preset-watcher";

    /** Quiet period after the last file event before a reload runs. */
    public static final long DEBOUNCE_MILLIS = 300L;

    private static final Logger LOGGER = LoggerFactory.getLogger(PresetStore.class);

    private final PresetRegistry registry;
    private final Path directory;
    private final List<Consumer<LoadResult>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();

    /** The custom presets currently published, by name, for last-known-good. */
    private Map<String, Preset> applied = Map.of();

    private volatile Thread watcher;
    private volatile WatchService watchService;

    /**
     * Creates a store.
     *
     * <p>Nothing is read from disk until {@link #reload()} is called.
     *
     * @param registry the registry to publish presets into
     * @param directory the directory holding one JSON file per preset
     */
    public PresetStore(PresetRegistry registry, Path directory) {
        this.registry = registry;
        this.directory = directory;
    }

    /**
     * Reads the directory and publishes whatever loaded.
     *
     * @return the load result, including the problems found
     */
    public synchronized LoadResult reload() {
        LoadResult result = PresetLoader.load(directory, BuiltinPresets.all());

        Map<String, Preset> loaded = new LinkedHashMap<>();
        for (Preset preset : result.presets()) {
            loaded.put(preset.name(), preset);
        }

        // A file that failed keeps its previous version. The failures name the file, so
        // the file's base name is the preset name that must be retained.
        for (LoadProblem problem : result.problems()) {
            String baseName = baseNameOf(problem.file());
            Preset previous = applied.get(baseName);
            if (previous != null && !loaded.containsKey(baseName)) {
                loaded.put(baseName, previous);
                LOGGER.warn(
                        "Keeping the last good version of preset '{}': {}",
                        previous.name(),
                        problem);
            }
        }

        for (LoadProblem problem : result.problems()) {
            LOGGER.warn("Preset problem: {}", problem);
        }

        registry.replaceCustom(loaded.values());
        applied = Map.copyOf(loaded);

        LOGGER.info(
                "Loaded {} custom preset(s) from {} with {} problem(s)",
                loaded.size(),
                directory,
                result.problems().size());

        notifyListeners(result);
        return result;
    }

    /**
     * Registers a callback for the result of every reload.
     *
     * <p>Callbacks run on whichever thread reloaded, which is the watcher thread for a
     * watched change. An exception from one callback is logged and ignored, so a broken
     * listener cannot stop the others or leave a reload half-reported.
     *
     * @param listener the callback
     */
    public void onReload(Consumer<LoadResult> listener) {
        listeners.add(listener);
    }

    /**
     * Starts watching the directory for changes, if it exists.
     *
     * <p>A burst of writes produces one reload: after the first event the watcher keeps
     * polling and reloads only once writes have been quiet for {@value #DEBOUNCE_MILLIS}
     * milliseconds. Reloading per event would parse the same half-written file several
     * times and log several failures for one keystroke.
     *
     * <p>Doing nothing but logging when the directory is absent is deliberate. A bot
     * with no custom presets is a perfectly normal bot, and a template that refused to
     * start without a directory would be wrong for most of its users.
     */
    public void startWatching() {
        if (!Files.isDirectory(directory)) {
            LOGGER.info("Not watching presets: {} is not a directory", directory);
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            WatchService service = directory.getFileSystem().newWatchService();
            directory.register(
                    service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            this.watchService = service;
            Thread thread = new Thread(this::watchLoop, WATCHER_THREAD_NAME);
            thread.setDaemon(true);
            this.watcher = thread;
            thread.start();
            LOGGER.info("Watching presets in {} on thread {}", directory, WATCHER_THREAD_NAME);
        } catch (IOException | RuntimeException e) {
            running.set(false);
            LOGGER.error(
                    "Could not watch presets in {}, continuing without watching", directory, e);
        }
    }

    /**
     * Stops the watcher. Idempotent, and safe to call when watching never started.
     *
     * <p>Closes the watch service first so the poll returns at once, then joins briefly.
     * The join is bounded because shutdown must not hang on a file system that is slow
     * to answer; the thread is a daemon, so a late return costs nothing.
     */
    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        WatchService service = watchService;
        if (service != null) {
            try {
                service.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close the preset watch service", e);
            }
        }
        Thread thread = watcher;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
            try {
                thread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Whether the watcher thread is running.
     *
     * @return {@code true} while the watcher is active
     */
    public boolean watching() {
        return running.get();
    }

    private void watchLoop() {
        try {
            long reloadAt = -1L;
            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.poll(DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ClosedWatchServiceException e) {
                    return;
                }

                if (key == null) {
                    if (reloadAt >= 0 && System.nanoTime() >= reloadAt) {
                        reload();
                        reloadAt = -1L;
                    }
                    continue;
                }

                if (touchesPresets(key)) {
                    reloadAt = deadlineFromNow();
                }
                if (!key.reset()) {
                    LOGGER.warn("Preset watching stopped: {} is no longer watchable", directory);
                    return;
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("Preset watcher stopped after an unexpected failure", e);
        } finally {
            running.set(false);
        }
    }

    /** True when any event in this key concerns a preset file. */
    private static boolean touchesPresets(WatchKey key) {
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                // Events were dropped, so what is on disk is unknown and worth a reload.
                return true;
            }
            Object context = event.context();
            if (context instanceof Path path && path.toString().endsWith(".json")) {
                return true;
            }
        }
        return false;
    }

    private static long deadlineFromNow() {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DEBOUNCE_MILLIS);
    }

    private void notifyListeners(LoadResult result) {
        List<Consumer<LoadResult>> snapshot = new ArrayList<>(listeners);
        for (Consumer<LoadResult> listener : snapshot) {
            try {
                listener.accept(result);
            } catch (RuntimeException e) {
                LOGGER.warn("A preset reload listener threw and was ignored", e);
            }
        }
    }

    /** The file name without its extension, or the whole string when there is none. */
    private static String baseNameOf(String file) {
        int dot = file.lastIndexOf('.');
        return dot > 0 ? file.substring(0, dot) : file;
    }
}
