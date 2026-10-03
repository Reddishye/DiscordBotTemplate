package es.redactado.menu.preset;

import java.util.Objects;

/**
 * Something that prevented one preset file from loading.
 *
 * <p>A load never throws for a bad file: one broken file must not cost a bot every
 * other preset, so problems are collected and returned instead. Each one carries the
 * file it came from, which is what makes a message like
 * {@code ocean.json: palette.accent: expected #RRGGBB, got 'blue'} actionable without
 * a stack trace.
 *
 * @param file the file name, for example {@code ocean.json}
 * @param message the field path and the problem, for example
 *     {@code palette.accent: expected #RRGGBB, got 'blue'}
 */
public record LoadProblem(String file, String message) {

    /**
     * Creates a problem.
     *
     * @param file the file name
     * @param message the field path and problem
     * @throws NullPointerException if either argument is null
     */
    public LoadProblem {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(message, "message");
    }

    /**
     * The full message, in the format used in logs.
     *
     * @return the file name, then the field path and problem
     */
    @Override
    public String toString() {
        return file + ": " + message;
    }
}
