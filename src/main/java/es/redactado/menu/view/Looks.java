package es.redactado.menu.view;

import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.Density;
import es.redactado.menu.preset.Gap;
import es.redactado.menu.preset.IconKey;
import es.redactado.menu.preset.Preset;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * The one place preset values become JDA values.
 *
 * <p>Everything a component needs to know about how a preset maps onto Discord lives
 * here, so a mapping is changed in one file rather than in every component. It is
 * package-private because a component reaches it through the preset on its
 * {@code MenuContext}: exposing it would invite a caller to hold a component and use a
 * different preset than the interaction it belongs to.
 */
final class Looks {

    private Looks() {}

    /**
     * The spacing a separator uses.
     *
     * <p>Density wins where the preset expresses both, because spacing is mostly about
     * how much room a menu needs rather than how its sections are drawn: a compact menu
     * is compact throughout, and a comfortable one is not going to draw a tight divider.
     * Only {@link Density#NORMAL} defers to {@link Gap}, which is where a preset can
     * choose per-section.
     *
     * @param preset the active preset
     * @return the spacing to render with
     */
    static Separator.Spacing spacing(Preset preset) {
        return switch (preset.density()) {
            case COMPACT -> Separator.Spacing.SMALL;
            case NORMAL -> spacingOf(preset.divider().gap());
            case COMFORTABLE -> Separator.Spacing.LARGE;
        };
    }

    private static Separator.Spacing spacingOf(Gap gap) {
        return gap == Gap.LARGE ? Separator.Spacing.LARGE : Separator.Spacing.SMALL;
    }

    /**
     * The style a button role renders as.
     *
     * @param preset the active preset
     * @param role what the button means
     * @return the style to render with
     */
    static ButtonStyle style(Preset preset, ButtonRole role) {
        return preset.buttons().of(role);
    }

    /**
     * The emoji a preset defines for a meaning.
     *
     * <p>Empty when the preset has none, which is how a preset asks for a button with no
     * icon at all rather than falling back to a hardcoded one.
     *
     * @param preset the active preset
     * @param key the meaning
     * @return the emoji, or empty when the preset defines none
     */
    static Optional<EmojiUnion> icon(Preset preset, IconKey key) {
        return preset.icons().get(key);
    }

    /**
     * The configured icon text for a meaning.
     *
     * <p>Used where Discord wants the text rather than a resolved emoji, such as inside
     * a markdown heading, where a custom emoji mention does not render.
     *
     * @param preset the active preset
     * @param key the meaning
     * @return the configured text, or an empty string when the preset defines none
     */
    static String iconText(Preset preset, IconKey key) {
        return preset.icons().formatted(key);
    }
}
