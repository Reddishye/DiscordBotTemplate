package es.redactado.menu.preset;

import java.util.regex.Pattern;

/**
 * Checks that a configured string really is an emoji.
 *
 * <p>JDA does not do this. {@code Emoji.fromFormatted("garbage")} returns a
 * {@code UnicodeEmoji} whose name is the literal text {@code garbage}, because the
 * implementation stores whatever string it is handed. A typo in a preset file would
 * therefore reach Discord silently and render as the wrong thing, so the preset
 * package validates before resolving.
 *
 * <p>The check is deliberately coarse: it recognises a custom emoji mention, and
 * otherwise requires every code point to fall inside a range that emoji live in.
 * That is enough to catch a typo, a truncated escape, or a plain word, and it is
 * not a complete Unicode emoji validator.
 */
final class EmojiText {

    /** {@code <:name:id>} or {@code <a:name:id>} for a custom emoji. */
    private static final Pattern CUSTOM_MENTION =
            Pattern.compile("<a?:[A-Za-z0-9_]{2,32}:\\d{17,20}>");

    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int VARIATION_SELECTOR_16 = 0xFE0F;
    private static final int COMBINING_KEYCAP = 0x20E3;

    /** Inclusive ranges that standard emoji and their modifiers fall inside. */
    private static final int[][] RANGES = {
        {0x00A9, 0x00A9}, // copyright
        {0x00AE, 0x00AE}, // registered
        {0x203C, 0x203C}, // double exclamation
        {0x2049, 0x2049}, // exclamation question mark
        {0x2122, 0x2122}, // trade mark
        {0x2139, 0x2139}, // information source
        {0x2194, 0x21AA}, // arrows
        {0x231A, 0x231B}, // watch, hourglass
        {0x2328, 0x2328}, // keyboard
        {0x23CF, 0x23FA}, // media controls, gauges
        {0x24C2, 0x24C2}, // circled M
        {0x25AA, 0x25FE}, // geometric shapes
        {0x2600, 0x27BF}, // misc symbols and dingbats
        {0x2934, 0x2935}, // arrows
        {0x2B00, 0x2BFF}, // misc symbols and arrows
        {0x3030, 0x3030}, // wavy dash
        {0x303D, 0x303D}, // part alternation mark
        {0x3297, 0x3297}, // circled congratulation
        {0x3299, 0x3299}, // circled secret
        {0x1F000, 0x1FAFF}, // symbols, pictographs, and extended-A
    };

    private EmojiText() {}

    /**
     * Reports whether a value is usable as an emoji.
     *
     * @param value the configured value
     * @return {@code true} when the value is a custom emoji mention or consists
     *     only of emoji code points
     */
    static boolean isValid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (CUSTOM_MENTION.matcher(value).matches()) {
            return true;
        }
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (!isEmojiCodePoint(codePoint)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmojiCodePoint(int codePoint) {
        if (codePoint == ZERO_WIDTH_JOINER
                || codePoint == VARIATION_SELECTOR_16
                || codePoint == COMBINING_KEYCAP) {
            return true;
        }
        for (int[] range : RANGES) {
            if (codePoint >= range[0] && codePoint <= range[1]) {
                return true;
            }
        }
        return false;
    }
}
