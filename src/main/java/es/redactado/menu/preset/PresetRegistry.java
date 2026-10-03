package es.redactado.menu.preset;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The presets available at runtime, read without locking.
 *
 * <p>Rendering a menu resolves a preset on every interaction, so the read path is
 * the hot path. Every accessor is one volatile read of an immutable snapshot
 * followed by a field access: no lock, no copy, and no allocation. Even
 * {@link #all()} returns a list built once per reload rather than sorting on each
 * call.
 *
 * <p>Writers, which only happen when presets are reloaded, build a complete new
 * snapshot and swap the reference. A reader therefore always sees one whole
 * consistent set, never a half-applied reload, and is never blocked by a writer.
 */
public final class PresetRegistry {

    /**
     * An immutable set of presets plus the one to fall back on.
     *
     * <p>The sorted view is computed here, while the set is immutable, so that
     * {@link #all()} stays a field read instead of a stream over the map.
     */
    private record Snapshot(Map<String, Preset> byName, List<Preset> sorted, Preset fallback) {
        static Snapshot of(Map<String, Preset> byName, Preset fallback) {
            List<Preset> sorted =
                    byName.values().stream().sorted(Comparator.comparing(Preset::name)).toList();
            return new Snapshot(byName, sorted, fallback);
        }
    }

    private volatile Snapshot snapshot;

    /** Creates a registry holding only the built-in presets. */
    public PresetRegistry() {
        Map<String, Preset> builtins = new LinkedHashMap<>();
        for (Preset preset : BuiltinPresets.all()) {
            builtins.put(preset.name(), preset);
        }
        this.snapshot = Snapshot.of(Map.copyOf(builtins), BuiltinPresets.DEFAULT);
    }

    /**
     * Finds a preset by name.
     *
     * @param name the preset name
     * @return the preset, or empty when no preset has that name
     */
    public Optional<Preset> find(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(snapshot.byName().get(name));
    }

    /**
     * Finds a preset by name, falling back to the default.
     *
     * <p>Never returns null, so a caller cannot accidentally render with a missing
     * preset. Use {@link #find(String)} when the distinction matters, for example
     * to report that a guild selected a preset that no longer exists.
     *
     * @param name the preset name, possibly null or unknown
     * @return the named preset, or the default preset
     */
    public Preset getOrDefault(String name) {
        Snapshot current = snapshot;
        Preset found = name == null ? null : current.byName().get(name);
        return found != null ? found : current.fallback();
    }

    /**
     * The preset used when a name is unknown.
     *
     * @return the current fallback preset
     */
    public Preset defaultPreset() {
        return snapshot.fallback();
    }

    /**
     * Every available preset, sorted by name.
     *
     * @return an unmodifiable list
     */
    public List<Preset> all() {
        return snapshot.sorted();
    }

    /**
     * Replaces the custom presets, keeping every built-in.
     *
     * <p>All or nothing: a name that shadows a built-in, or a name repeated within
     * {@code custom}, leaves the registry exactly as it was. A reload that would
     * produce a confusing registry is worse than a reload that does not happen.
     *
     * @param custom the custom presets, or an empty collection for built-ins only
     * @throws IllegalArgumentException if a custom name shadows a built-in or is
     *     duplicated, naming the offender
     * @throws NullPointerException if {@code custom} is null
     */
    public synchronized void replaceCustom(Collection<Preset> custom) {
        Objects.requireNonNull(custom, "custom");

        Map<String, Preset> next = new LinkedHashMap<>();
        for (Preset preset : BuiltinPresets.all()) {
            next.put(preset.name(), preset);
        }
        List<String> clashes = new ArrayList<>();
        for (Preset preset : custom) {
            if (preset == null) {
                throw new IllegalArgumentException("custom presets must not contain null");
            }
            if (BuiltinPresets.find(preset.name()).isPresent()) {
                clashes.add(preset.name() + " (shadows a built-in)");
                continue;
            }
            if (next.put(preset.name(), preset) != null) {
                clashes.add(preset.name() + " (declared twice)");
            }
        }
        if (!clashes.isEmpty()) {
            throw new IllegalArgumentException(
                    "custom presets rejected, registry unchanged: " + String.join(", ", clashes));
        }

        Preset fallback = snapshot.fallback();
        this.snapshot = Snapshot.of(Map.copyOf(next), fallback);
    }

    /**
     * Changes which preset is used for an unknown name.
     *
     * @param name the preset to fall back on
     * @throws IllegalArgumentException if no preset has that name
     * @throws NullPointerException if {@code name} is null
     */
    public synchronized void setDefault(String name) {
        Objects.requireNonNull(name, "name");
        Preset preset = snapshot.byName().get(name);
        if (preset == null) {
            throw new IllegalArgumentException("no preset named '" + name + "'");
        }
        this.snapshot = Snapshot.of(snapshot.byName(), preset);
    }

    @Override
    public String toString() {
        Snapshot current = snapshot;
        return "PresetRegistry"
                + current.byName().keySet()
                + " default="
                + current.fallback().name();
    }
}
