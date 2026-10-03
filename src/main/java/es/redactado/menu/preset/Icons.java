package es.redactado.menu.preset;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * The icon set of a preset, keyed by meaning rather than by symbol.
 *
 * <p>Storing "this is the back icon" instead of "this is U+25C0" is what lets a
 * preset be restyled without touching the menus that read it, and lets a bot author
 * pick a different arrow without editing component code.
 *
 * <p>Each configured value is parsed once, at construction, rather than on every
 * render. {@link Emoji#fromFormatted} is neither cheap nor validating, so paying
 * for it once per icon is the whole point.
 *
 * <p>Immutable. A value that is not a valid emoji is rejected on construction,
 * naming the key, because a typo here would otherwise reach Discord unchallenged.
 */
public final class Icons {

    private static final Icons NONE = new Icons(Map.of());

    private final Map<IconKey, EmojiUnion> resolved;
    private final Map<IconKey, String> formatted;

    private Icons(Map<IconKey, String> formatted) {
        Map<IconKey, String> copy = new LinkedHashMap<>();
        Map<IconKey, EmojiUnion> emojis = new EnumMap<>(IconKey.class);
        formatted.forEach(
                (key, value) -> {
                    if (value == null || value.isBlank()) {
                        return;
                    }
                    if (!EmojiText.isValid(value)) {
                        throw new IllegalArgumentException(
                                "icon '%s' is not a valid emoji: '%s'".formatted(key, value));
                    }
                    emojis.put(key, Emoji.fromFormatted(value));
                    copy.put(key, value);
                });
        this.resolved = Collections.unmodifiableMap(emojis);
        this.formatted = Collections.unmodifiableMap(copy);
    }

    /**
     * The empty set, used by a preset that wants no icons at all.
     *
     * @return an icon set with no entries
     */
    public static Icons none() {
        return NONE;
    }

    /**
     * Builds an icon set from configured values.
     *
     * @param formatted the value for each key; a null or blank value means no icon
     * @return the icon set
     * @throws IllegalArgumentException if a value is not a valid emoji, naming the
     *     key
     * @throws NullPointerException if {@code values} is null
     */
    public static Icons of(Map<IconKey, String> formatted) {
        Objects.requireNonNull(formatted, "formatted");
        return formatted.isEmpty() ? NONE : new Icons(formatted);
    }

    /**
     * The resolved emoji for a key.
     *
     * <p>Typed as {@link EmojiUnion} because that is what JDA's own
     * {@code Emoji.fromFormatted} returns and what {@code Button.of} requires. Widening
     * it to {@link Emoji} here would force every caller that renders a button to cast.
     *
     * @param key the meaning
     * @return the emoji, or empty when this set has none for that key
     */
    public Optional<EmojiUnion> get(IconKey key) {
        return Optional.ofNullable(resolved.get(key));
    }

    /**
     * The configured text for a key, as a menu would write it into markdown.
     *
     * @param key the meaning
     * @return the configured value, or an empty string when absent
     */
    public String formatted(IconKey key) {
        return formatted.getOrDefault(key, "");
    }

    /**
     * Returns a copy with one more icon.
     *
     * @param key the meaning
     * @param value the value; null or blank removes the icon
     * @return a new icon set, leaving this one unchanged
     * @throws IllegalArgumentException if the value is not a valid emoji
     */
    public Icons with(IconKey key, String value) {
        Objects.requireNonNull(key, "key");
        Map<IconKey, String> copy = new LinkedHashMap<>(formatted);
        if (value == null || value.isBlank()) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return of(copy);
    }

    /**
     * The configured values.
     *
     * @return an unmodifiable map of key to configured value
     */
    public Map<IconKey, String> asMap() {
        return formatted;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Icons icons && formatted.equals(icons.formatted);
    }

    @Override
    public int hashCode() {
        return formatted.hashCode();
    }

    @Override
    public String toString() {
        return "Icons" + formatted;
    }
}
