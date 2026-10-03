package es.redactado.menu.preset;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one pass over a preset directory.
 *
 * <p>Both lists are immutable copies, so a caller can hold on to the result while a
 * later reload runs.
 *
 * @param presets the presets that loaded, sorted by name
 * @param problems everything that did not, one entry per file at most
 */
public record LoadResult(List<Preset> presets, List<LoadProblem> problems) {

    /**
     * Creates a result.
     *
     * @param presets the presets that loaded
     * @param problems the problems found
     * @throws NullPointerException if either argument is null
     */
    public LoadResult {
        presets = List.copyOf(Objects.requireNonNull(presets, "presets"));
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
    }

    /**
     * A result with no presets and no problems, used for a missing directory.
     *
     * @return the empty result
     */
    public static LoadResult empty() {
        return new LoadResult(List.of(), List.of());
    }

    /**
     * Whether every file loaded.
     *
     * @return {@code true} when nothing failed
     */
    public boolean clean() {
        return problems.isEmpty();
    }
}
