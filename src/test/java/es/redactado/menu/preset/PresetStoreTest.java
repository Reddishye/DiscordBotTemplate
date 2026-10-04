package es.redactado.menu.preset;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetStoreTest {

    /** Generous, because file watching has no upper bound on a slow file system. */
    private static final long TIMEOUT_SECONDS = 10L;

    @TempDir Path directory;

    @Nested
    @DisplayName("reload")
    class Reload {

        @Test
        @DisplayName("valid presets are applied and invalid ones are skipped without throwing")
        void appliesValidAndSkipsInvalid() throws IOException {
            write("good", "{\"name\": \"good\", \"density\": \"compact\"}");
            write("bad", "{\"name\": \"bad\", \"density\": \"roomy\"}");
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = new PresetStore(registry, directory);

            LoadResult result = store.reload();

            assertThat(result.problems()).hasSize(1);
            assertThat(registry.find("good")).isPresent();
            assertThat(registry.find("bad")).isEmpty();
            assertThat(registry.all()).extracting(Preset::name).contains("good");
        }

        @Test
        @DisplayName("a file that becomes invalid keeps its last good version")
        void keepsLastGoodVersion() throws IOException {
            write("kept", "{\"name\": \"kept\", \"palette\": {\"accent\": \"#010203\"}}");
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = new PresetStore(registry, directory);
            store.reload();
            assertThat(registry.find("kept").orElseThrow().palette().accent()).isEqualTo(0x010203);

            write("kept", "{\"name\": \"kept\", \"palette\": {\"accent\": \"not a colour\"}}");
            store.reload();

            Preset retained = registry.find("kept").orElseThrow();
            assertThat(retained.palette().accent())
                    .as("the old accent is still published")
                    .isEqualTo(0x010203);
        }

        @Test
        @DisplayName("a file that is deleted is dropped")
        void dropsDeletedFile() throws IOException {
            write("gone", "{\"name\": \"gone\"}");
            write("stays", "{\"name\": \"stays\"}");
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = new PresetStore(registry, directory);
            store.reload();
            assertThat(registry.find("gone")).isPresent();

            Files.delete(directory.resolve("gone.json"));
            store.reload();

            assertThat(registry.find("gone")).as("a missing file is not a failed one").isEmpty();
            assertThat(registry.find("stays")).isPresent();
        }

        @Test
        @DisplayName("listeners see the result of every reload")
        void listenersAreNotified() throws IOException {
            write("one", "{\"name\": \"one\"}");
            PresetStore store = new PresetStore(new PresetRegistry(), directory);
            List<LoadResult> seen = new CopyOnWriteArrayList<>();
            store.onReload(seen::add);

            store.reload();

            assertThat(seen).hasSize(1);
            assertThat(seen.getFirst().presets()).extracting(Preset::name).containsExactly("one");
        }

        @Test
        @DisplayName("a throwing listener does not break the reload")
        void throwingListenerIsIgnored() throws Exception {
            write("one", "{\"name\": \"one\"}");
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = new PresetStore(registry, directory);
            store.onReload(
                    result -> {
                        throw new IllegalStateException("listener is broken");
                    });
            CountDownLatch reached = new CountDownLatch(1);
            store.onReload(result -> reached.countDown());

            store.reload();

            assertThat(reached.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.find("one")).as("the reload still applied").isPresent();
        }

        @Test
        @DisplayName("eight threads may reload at once")
        void concurrentReloadsAreSerialized() throws Exception {
            for (int i = 0; i < 10; i++) {
                write("p" + i, "{\"name\": \"p" + i + "\"}");
            }
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = new PresetStore(registry, directory);

            int threads = 8;
            CyclicBarrier gate = new CyclicBarrier(threads);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<Void>> all = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    Callable<Void> body =
                            () -> {
                                gate.await();
                                for (int n = 0; n < 50; n++) {
                                    store.reload();
                                }
                                return null;
                            };
                    all.add(pool.submit(body));
                }
                for (var future : all) {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(registry.all()).hasSize(BuiltinPresets.all().size() + 10);
        }

        @Test
        @DisplayName("a missing directory is not a problem")
        void missingDirectoryIsEmpty() {
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = new PresetStore(registry, directory.resolve("absent"));

            assertThat(store.reload().problems()).isEmpty();
            assertThat(registry.all()).hasSize(BuiltinPresets.all().size());
        }
    }

    @Nested
    @DisplayName("watching")
    @Tag("filesystem")
    class Watching {

        @Test
        @DisplayName("a created file is picked up")
        void createTriggersReload() throws Exception {
            PresetStore store = startedStore();
            CountDownLatch loaded = new CountDownLatch(1);
            store.onReload(
                    result -> {
                        if (result.presets().stream()
                                .anyMatch(preset -> preset.name().equals("fresh"))) {
                            loaded.countDown();
                        }
                    });

            write("fresh", "{\"name\": \"fresh\"}");

            assertThat(loaded.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("an edited file is picked up")
        void editTriggersReload() throws Exception {
            write("edited", "{\"name\": \"edited\", \"palette\": {\"accent\": \"#000001\"}}");
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = startedStore(registry);
            store.reload();

            CountDownLatch changed = new CountDownLatch(1);
            store.onReload(
                    result -> {
                        registry.find("edited")
                                .filter(preset -> preset.palette().accent() == 0x000002)
                                .ifPresent(preset -> changed.countDown());
                    });
            write("edited", "{\"name\": \"edited\", \"palette\": {\"accent\": \"#000002\"}}");

            assertThat(changed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("a deleted file is picked up")
        void deleteTriggersReload() throws Exception {
            write("doomed", "{\"name\": \"doomed\"}");
            PresetRegistry registry = new PresetRegistry();
            PresetStore store = startedStore(registry);
            store.reload();
            assertThat(registry.find("doomed")).isPresent();

            CountDownLatch gone = new CountDownLatch(1);
            store.onReload(
                    result -> {
                        if (registry.find("doomed").isEmpty()) {
                            gone.countDown();
                        }
                    });
            Files.delete(directory.resolve("doomed.json"));

            assertThat(gone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        @Test
        @DisplayName("a burst of twenty writes produces at most two reloads")
        void burstIsDebounced() throws Exception {
            PresetStore store = startedStore();
            List<LoadResult> reloads = new CopyOnWriteArrayList<>();
            store.onReload(reloads::add);

            for (int i = 0; i < 20; i++) {
                write("burst", "{\"name\": \"burst\", \"description\": \"revision " + i + "\"}");
            }

            // Real time is unavoidable here: the quiet period is 300 ms, so waiting
            // five of them is generous without making the suite crawl.
            Thread.sleep(PresetStore.DEBOUNCE_MILLIS * 5);
            assertThat(reloads)
                    .as("20 writes, debounced into at most 2 reloads")
                    .hasSizeLessThanOrEqualTo(2);
            assertThat(reloads).isNotEmpty();
        }

        @Test
        @DisplayName("close stops the watcher thread")
        void closeStopsThread() {
            PresetStore store = startedStore();
            assertThat(store.watching()).isTrue();

            store.close();

            assertThat(store.watching()).isFalse();
        }

        @Test
        @DisplayName("close is idempotent")
        void closeIsIdempotent() {
            PresetStore store = startedStore();

            store.close();
            store.close();

            assertThat(store.watching()).isFalse();
        }

        @Test
        @DisplayName("a missing directory starts no thread")
        void missingDirectoryStartsNothing() {
            PresetStore store = new PresetStore(new PresetRegistry(), directory.resolve("absent"));

            store.startWatching();

            assertThat(store.watching()).isFalse();
        }

        private PresetStore startedStore() {
            return startedStore(new PresetRegistry());
        }

        private PresetStore startedStore(PresetRegistry registry) {
            PresetStore store = new PresetStore(registry, directory);
            store.startWatching();
            return store;
        }
    }

    private void write(String name, String json) throws IOException {
        Files.writeString(directory.resolve(name + ".json"), json, StandardCharsets.UTF_8);
    }
}
