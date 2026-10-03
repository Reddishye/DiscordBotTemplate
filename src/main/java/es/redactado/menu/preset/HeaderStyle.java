package es.redactado.menu.preset;

/**
 * How a menu titles itself.
 *
 * @param level the markdown heading level, from 1 (largest) to 3
 * @param subtitle whether the menu shows a subtitle line beneath the title
 */
public record HeaderStyle(int level, boolean subtitle) {

    /** Smallest heading level Discord renders distinctly. */
    public static final int MIN_LEVEL = 1;

    /** Largest heading level Discord renders distinctly. */
    public static final int MAX_LEVEL = 3;

    public HeaderStyle {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "header level must be between %d and %d, got %d"
                            .formatted(MIN_LEVEL, MAX_LEVEL, level));
        }
    }
}
