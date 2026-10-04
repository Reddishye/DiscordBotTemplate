package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetPreferences;
import es.redactado.menu.preset.PresetRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.components.container.Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PresetResolverTest {

    private static final long TIMEOUT_SECONDS = 10L;
    private static final long GUILD = 42L;
    private static final long USER = 7L;

    private PresetRegistry registry;
    private RecordingPreferences preferences;

    @BeforeEach
    void setUp() {
        registry = new PresetRegistry();
        preferences = new RecordingPreferences();
    }

    @Test
    @DisplayName("a preset forced by the menu wins over everything")
    void menuLevelWins() {
        preferences.guild = Optional.of("minimal");
        preferences.user = Optional.of("midnight");

        Preset resolved = resolve(menuFor("monochrome"), GUILD, USER);

        assertThat(resolved.name()).isEqualTo("monochrome");
        assertThat(preferences.guildCalls).hasValue(0);
    }

    @Test
    @DisplayName("the guild preference beats the user's")
    void guildBeatsUser() {
        preferences.guild = Optional.of("minimal");
        preferences.user = Optional.of("midnight");

        assertThat(resolve(menuFor(null), GUILD, USER).name()).isEqualTo("minimal");
    }

    @Test
    @DisplayName("the user preference is used when the guild has none")
    void userLevelWins() {
        preferences.guild = Optional.empty();
        preferences.user = Optional.of("midnight");

        assertThat(resolve(menuFor(null), GUILD, USER).name()).isEqualTo("midnight");
    }

    @Test
    @DisplayName("with no preference anywhere the registry default is used")
    void defaultLevelWins() {
        registry.setDefault("vibrant");

        assertThat(resolve(menuFor(null), GUILD, USER).name()).isEqualTo("vibrant");
    }

    @Test
    @DisplayName("a menu forcing a name nobody has is skipped, not obeyed")
    void unknownMenuPresetFallsThrough() {
        preferences.guild = Optional.of("minimal");

        assertThat(resolve(menuFor("ghost"), GUILD, USER).name()).isEqualTo("minimal");
    }

    @Test
    @DisplayName("an unknown guild preference falls through to the user")
    void unknownGuildPresetFallsThrough() {
        preferences.guild = Optional.of("ghost");
        preferences.user = Optional.of("midnight");

        assertThat(resolve(menuFor(null), GUILD, USER).name()).isEqualTo("midnight");
    }

    @Test
    @DisplayName("an unknown user preference falls through to the default")
    void unknownUserPresetFallsThrough() {
        preferences.guild = Optional.empty();
        preferences.user = Optional.of("ghost");

        assertThat(resolve(menuFor(null), GUILD, USER).name())
                .isEqualTo(registry.defaultPreset().name());
    }

    @Test
    @DisplayName("guild id zero skips the guild level")
    void directMessageSkipsGuild() {
        preferences.guild = Optional.of("minimal");
        preferences.user = Optional.of("midnight");

        assertThat(resolve(menuFor(null), 0L, USER).name()).isEqualTo("midnight");
        assertThat(preferences.guildCalls).hasValue(0);
    }

    @Test
    @DisplayName("the user level is ignored when user presets are disabled")
    void userLevelDisabled() {
        PresetResolver resolver = new PresetResolver(registry, preferences, false);
        preferences.guild = Optional.empty();
        preferences.user = Optional.of("midnight");

        assertThat(resolver.resolve(menuFor(null), GUILD, USER).join())
                .isEqualTo(registry.defaultPreset());
        assertThat(preferences.userCalls).hasValue(0);
    }

    @Test
    @DisplayName("a failing guild lookup is treated as no preference")
    void failingGuildLookupIsIgnored() {
        PresetResolver resolver = new PresetResolver(registry, preferences, true);
        preferences.failGuild = true;
        preferences.user = Optional.of("midnight");

        assertThat(resolver.resolve(menuFor(null), GUILD, USER).join().name())
                .isEqualTo("midnight");
    }

    @Test
    @DisplayName("a failing user lookup still yields the default rather than failing")
    void failingUserLookupYieldsDefault() {
        PresetResolver resolver = new PresetResolver(registry, preferences, true);
        preferences.failUser = true;

        assertThat(resolver.resolve(menuFor(null), GUILD, USER).join())
                .isEqualTo(registry.defaultPreset());
    }

    @Test
    @DisplayName("the user is not looked up when the guild already answered")
    void userLookupSkippedWhenGuildResolved() {
        preferences.guild = Optional.of("minimal");

        assertThat(resolve(menuFor(null), GUILD, USER).name()).isEqualTo("minimal");
        assertThat(preferences.userCalls).as("no needless query").hasValue(0);
    }

    @Test
    @DisplayName("resolution never returns null or throws while the registry is swapped")
    void survivesConcurrentSwaps() throws Exception {
        Preset one =
                Preset.builder("one").density(es.redactado.menu.preset.Density.COMPACT).build();
        Preset two =
                Preset.builder("two").density(es.redactado.menu.preset.Density.COMFORTABLE).build();
        PresetResolver resolver = new PresetResolver(registry, preferences, true);
        preferences.guild = Optional.of("one");
        preferences.user = Optional.of("two");

        int threads = 8;
        int swaps = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CyclicBarrier gate = new CyclicBarrier(threads + 1);
            List<Future<?>> readers = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                readers.add(
                        pool.submit(
                                () -> {
                                    gate.await();
                                    for (int n = 0; n < 500; n++) {
                                        Preset preset =
                                                resolver.resolve(menuFor(null), GUILD, USER).join();
                                        if (preset == null) {
                                            throw new AssertionError("resolver returned null");
                                        }
                                    }
                                    return null;
                                }));
            }

            gate.await();
            for (int n = 0; n < swaps; n++) {
                registry.replaceCustom(List.of(n % 2 == 0 ? one : two));
            }

            for (Future<?> reader : readers) {
                reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Preset resolve(Menu menu, long guildId, long userId) {
        return new PresetResolver(registry, preferences, true)
                .resolve(menu, guildId, userId)
                .join();
    }

    private static Menu menuFor(String presetName) {
        return new Menu() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public Optional<String> presetName() {
                return Optional.ofNullable(presetName);
            }

            @Override
            public CompletableFuture<Container> render(MenuContext ctx) {
                throw new UnsupportedOperationException("not rendered in these tests");
            }

            @Override
            public void actions(ActionTable.Builder table) {
                throw new UnsupportedOperationException("not used in these tests");
            }
        };
    }

    /** Counts lookups so the tests can prove a level was skipped rather than guessed. */
    private static final class RecordingPreferences implements PresetPreferences {

        private final AtomicInteger guildCalls = new AtomicInteger();
        private final AtomicInteger userCalls = new AtomicInteger();
        private Optional<String> guild = Optional.empty();
        private Optional<String> user = Optional.empty();
        private boolean failGuild;
        private boolean failUser;

        @Override
        public CompletableFuture<Optional<String>> guildPreset(long guildId) {
            guildCalls.incrementAndGet();
            if (failGuild) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("database is down"));
            }
            return CompletableFuture.completedFuture(guild);
        }

        @Override
        public CompletableFuture<Optional<String>> userPreset(long userId) {
            userCalls.incrementAndGet();
            if (failUser) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("database is down"));
            }
            return CompletableFuture.completedFuture(user);
        }
    }

    @Test
    @DisplayName("the built-in presets are the ones resolution can reach")
    void builtinsAreReachable() {
        assertThat(BuiltinPresets.all()).isNotEmpty();
        for (Preset preset : BuiltinPresets.all()) {
            assertThat(registry.find(preset.name())).contains(preset);
        }
    }
}
