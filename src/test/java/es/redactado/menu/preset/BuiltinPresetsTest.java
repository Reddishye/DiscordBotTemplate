package es.redactado.menu.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuiltinPresetsTest {

    @Test
    @DisplayName("there are exactly five, with unique names")
    void fiveUniqueNames() {
        List<Preset> all = BuiltinPresets.all();

        assertThat(all).hasSize(5);
        assertThat(all.stream().map(Preset::name).collect(Collectors.toSet())).hasSize(5);
    }

    @Test
    @DisplayName("no two presets are equal")
    void allDistinct() {
        List<Preset> all = BuiltinPresets.all();

        assertThat(Set.copyOf(all)).hasSize(all.size());
    }

    @Test
    @DisplayName("every accent colour is different")
    void accentsDiffer() {
        Set<Integer> accents =
                BuiltinPresets.all().stream()
                        .map(preset -> preset.palette().accent())
                        .collect(Collectors.toSet());

        assertThat(accents).hasSize(BuiltinPresets.all().size());
    }

    @Test
    @DisplayName("every preset satisfies its own validation")
    void allValid() {
        for (Preset preset : BuiltinPresets.all()) {
            assertThat(preset.name()).matches(Preset.NAME_PATTERN);
            assertThat(preset.description()).isNotBlank();
            assertThat(preset.palette()).isNotNull();
            assertThat(preset.buttons()).isNotNull();
        }
    }

    @Test
    @DisplayName("an icon set is either empty or complete")
    void iconsAreEmptyOrComplete() {
        for (Preset preset : BuiltinPresets.all()) {
            if (preset.icons().equals(Icons.none())) {
                assertThat(preset.name()).isEqualTo("minimal");
                continue;
            }
            for (IconKey key : IconKey.values()) {
                assertThat(preset.icons().get(key))
                        .as("%s must resolve %s", preset.name(), key)
                        .isPresent();
            }
        }
    }

    @Test
    @DisplayName("the vibrant icon set differs from the default one")
    void vibrantDiffersFromDefault() {
        Set<String> vibrant =
                new java.util.HashSet<>(BuiltinPresets.VIBRANT.icons().asMap().values());

        assertThat(vibrant)
                .isNotEqualTo(
                        new java.util.HashSet<>(BuiltinPresets.DEFAULT.icons().asMap().values()));
    }

    @Test
    @DisplayName("monochrome uses only black and white squares, mapped by meaning")
    void monochromeUsesOnlySquares() {
        Map<IconKey, String> icons = BuiltinPresets.MONOCHROME.icons().asMap();
        String selector = Character.toString(0xFE0F);

        assertThat(icons).hasSize(IconKey.values().length);
        assertThat(icons.values())
                .allSatisfy(
                        value -> {
                            String bare =
                                    value.endsWith(selector)
                                            ? value.substring(0, value.length() - selector.length())
                                            : value;
                            assertThat(bare.length())
                                    .as("%s is one code point", value)
                                    .isEqualTo(1);
                            assertThat((int) bare.charAt(0))
                                    .as("%s is a black or white square", value)
                                    .isIn(0x2B1B, 0x2B1C, 0x25AA, 0x25AB);
                        });

        // Colour carries no meaning here, so shape has to: OK cannot equal ERROR.
        assertThat(icons.get(IconKey.OK)).isNotEqualTo(icons.get(IconKey.ERROR));
    }

    @Test
    @DisplayName("find resolves a built-in by name")
    void findsByName() {
        assertThat(BuiltinPresets.find("midnight")).contains(BuiltinPresets.MIDNIGHT);
        assertThat(BuiltinPresets.find("absent")).isEmpty();
    }

    @Test
    @DisplayName("every footer placeholder is supported")
    void footersUseSupportedPlaceholders() {
        for (Preset preset : BuiltinPresets.all()) {
            String footer = preset.footer();
            assertThat(footer)
                    .as("%s footer", preset.name())
                    .satisfiesAnyOf(
                            text -> assertThat(text).isEmpty(),
                            text -> assertThat(text).isEqualTo("{menu}"),
                            text -> assertThat(text).isEqualTo("{user}"));
        }
    }
}

class PresetRegistryTest {

    private static Preset custom(String name) {
        return Preset.builder(name).description("A custom preset.").build();
    }

    @Test
    @DisplayName("starts with the built-ins and the default fallback")
    void initialState() {
        PresetRegistry registry = new PresetRegistry();

        assertThat(registry.all()).hasSize(BuiltinPresets.all().size());
        assertThat(registry.defaultPreset()).isEqualTo(BuiltinPresets.DEFAULT);
        assertThat(registry.find("midnight")).contains(BuiltinPresets.MIDNIGHT);
    }

    @Test
    @DisplayName("find returns empty for unknown and null names")
    void findUnknownAndNull() {
        PresetRegistry registry = new PresetRegistry();

        assertThat(registry.find("absent")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    @DisplayName("getOrDefault falls back for unknown and null names")
    void getOrDefaultFallsBack() {
        PresetRegistry registry = new PresetRegistry();

        assertThat(registry.getOrDefault("midnight")).isEqualTo(BuiltinPresets.MIDNIGHT);
        assertThat(registry.getOrDefault("absent")).isEqualTo(BuiltinPresets.DEFAULT);
        assertThat(registry.getOrDefault(null)).isEqualTo(BuiltinPresets.DEFAULT);
    }

    @Test
    @DisplayName("all is sorted by name and unmodifiable")
    void allSortedAndUnmodifiable() {
        PresetRegistry registry = new PresetRegistry();

        List<Preset> all = registry.all();

        assertThat(all.stream().map(Preset::name)).isSorted();
        assertThatThrownBy(() -> all.add(BuiltinPresets.DEFAULT))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("replaceCustom adds presets alongside the built-ins")
    void replaceCustomAdds() {
        PresetRegistry registry = new PresetRegistry();

        registry.replaceCustom(List.of(custom("brand"), custom("seasonal")));

        assertThat(registry.find("brand")).isPresent();
        assertThat(registry.find("seasonal")).isPresent();
        assertThat(registry.find("default")).isPresent();
        assertThat(registry.all()).hasSize(BuiltinPresets.all().size() + 2);
    }

    @Test
    @DisplayName("replaceCustom with an empty collection keeps only the built-ins")
    void replaceCustomClears() {
        PresetRegistry registry = new PresetRegistry();
        registry.replaceCustom(List.of(custom("brand")));

        registry.replaceCustom(List.of());

        assertThat(registry.find("brand")).isEmpty();
        assertThat(registry.all()).hasSize(BuiltinPresets.all().size());
    }

    @Test
    @DisplayName("a custom preset shadowing a built-in is rejected and changes nothing")
    void shadowingBuiltInRejected() {
        PresetRegistry registry = new PresetRegistry();
        registry.replaceCustom(List.of(custom("keep")));

        assertThatThrownBy(() -> registry.replaceCustom(List.of(custom("default"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default")
                .hasMessageContaining("built-in");

        assertThat(registry.find("keep")).isPresent();
        assertThat(registry.all()).hasSize(BuiltinPresets.all().size() + 1);
        assertThat(registry.find("default")).contains(BuiltinPresets.DEFAULT);
    }

    @Test
    @DisplayName("a duplicate custom name is rejected and changes nothing")
    void duplicateRejected() {
        PresetRegistry registry = new PresetRegistry();

        assertThatThrownBy(() -> registry.replaceCustom(List.of(custom("twice"), custom("twice"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("twice");

        assertThat(registry.find("twice")).isEmpty();
        assertThat(registry.all()).hasSize(BuiltinPresets.all().size());
    }

    @Test
    @DisplayName("setDefault changes the fallback, and rejects an unknown name")
    void setDefault() {
        PresetRegistry registry = new PresetRegistry();

        registry.setDefault("monochrome");
        assertThat(registry.defaultPreset()).isEqualTo(BuiltinPresets.MONOCHROME);
        assertThat(registry.getOrDefault("absent")).isEqualTo(BuiltinPresets.MONOCHROME);

        assertThatThrownBy(() -> registry.setDefault("absent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent");
        assertThat(registry.defaultPreset()).isEqualTo(BuiltinPresets.MONOCHROME);
    }

    @Test
    @DisplayName("all is a field read, not a sort on every call")
    void allIsPreComputed() {
        PresetRegistry registry = new PresetRegistry();

        assertThat(registry.all()).isSameAs(registry.all());
    }

    @Test
    @DisplayName("a reader never observes a half-applied reload")
    void atomicSwapUnderConcurrency() throws InterruptedException {
        PresetRegistry registry = new PresetRegistry();
        int builtins = BuiltinPresets.all().size();
        int smallSetSize = 2;
        int largeSetSize = 3;
        int swappers = 8;

        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicReference<String> failure = new AtomicReference<>();
        CountDownLatch readersDone = new CountDownLatch(swappers);
        ExecutorService pool = Executors.newFixedThreadPool(swappers + 1);

        for (int i = 0; i < swappers; i++) {
            pool.execute(
                    () -> {
                        try {
                            while (writing.get()) {
                                int size = registry.all().size();
                                boolean valid =
                                        size == builtins
                                                || size == builtins + smallSetSize
                                                || size == builtins + largeSetSize;
                                if (!valid) {
                                    failure.compareAndSet(
                                            null, "observed a mixed snapshot of size " + size);
                                    writing.set(false);
                                    return;
                                }
                                registry.find("default").orElse(null);
                            }
                        } finally {
                            readersDone.countDown();
                        }
                    });
        }

        List<Preset> firstSet = List.of(custom("alpha"), custom("beta"));
        List<Preset> secondSet = List.of(custom("gamma"), custom("delta"), custom("epsilon"));
        for (int i = 0; i < 1_000; i++) {
            registry.replaceCustom(i % 2 == 0 ? firstSet : secondSet);
        }
        writing.set(false);

        assertThat(readersDone.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(failure.get()).isNull();
        assertThat(registry.all()).hasSize(builtins + largeSetSize);
    }

    @Test
    @DisplayName("toString mentions the default")
    void toStringIsUseful() {
        assertThat(new PresetRegistry().toString()).contains("default=default");
    }
}
