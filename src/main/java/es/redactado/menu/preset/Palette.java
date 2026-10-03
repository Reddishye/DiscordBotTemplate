package es.redactado.menu.preset;

/**
 * The colours a menu uses, as packed 24-bit RGB integers.
 *
 * <p>These are raw values. Discord decides how they actually look against a user's
 * theme, so they are a hint rather than a promise.
 *
 * @param accent the container's accent stripe
 * @param success the colour for a completed outcome
 * @param warning the colour for something needing attention
 * @param danger the colour for a destructive or failed outcome
 * @param info the colour for neutral information
 * @param neutral the colour for chrome such as dividers and muted text
 */
import java.util.Objects;

public record Palette(int accent, int success, int warning, int danger, int info, int neutral) {

    /** Largest value a packed RGB colour can hold. */
    public static final int MAX = 0xFFFFFF;

    public Palette {
        require("accent", accent);
        require("success", success);
        require("warning", warning);
        require("danger", danger);
        require("info", info);
        require("neutral", neutral);
    }

    private static void require(String name, int value) {
        if (value < 0 || value > MAX) {
            throw new IllegalArgumentException(
                    "palette colour %s must be between 0 and 0xFFFFFF, got 0x%X"
                            .formatted(name, value));
        }
    }

    /**
     * The colour a tone means.
     *
     * @param tone what the colour is for
     * @return the packed RGB value
     * @throws NullPointerException if {@code tone} is null
     */
    public int color(Tone tone) {
        return switch (Objects.requireNonNull(tone, "tone")) {
            case ACCENT -> accent;
            case SUCCESS -> success;
            case WARNING -> warning;
            case DANGER -> danger;
            case INFO -> info;
            case NEUTRAL -> neutral;
        };
    }
}
