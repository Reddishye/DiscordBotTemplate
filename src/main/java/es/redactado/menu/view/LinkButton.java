package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.IconKey;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * A button that opens a URL rather than running one of the menu's actions.
 *
 * <p>A link button cannot carry the menu's action id, so it is always
 * {@link ButtonStyle#LINK} and never a preset style: Discord does not render a link
 * button in any other style, and a preset mapping would have nowhere to apply. Its icon
 * still comes from the preset, so a menu that shows icons everywhere shows one here too.
 */
public final class LinkButton implements RowItem {

    private final String url;
    private final String label;
    private final IconKey icon;
    private final EmojiUnion literalEmoji;

    private LinkButton(String url, String label, IconKey icon, EmojiUnion literalEmoji) {
        this.url = url;
        this.label = label;
        this.icon = icon;
        this.literalEmoji = literalEmoji;
    }

    /**
     * A button that opens a URL.
     *
     * @param url where it goes
     * @param label the button's text
     * @return the component
     */
    public static LinkButton of(String url, String label) {
        return new LinkButton(url, label, null, null);
    }

    /**
     * Shows an icon taken from the preset.
     *
     * @param icon the meaning, resolved from the preset
     * @return a copy of this button
     */
    public LinkButton icon(IconKey icon) {
        return new LinkButton(url, label, icon, literalEmoji);
    }

    /**
     * Shows a specific emoji regardless of the preset.
     *
     * @param emoji the emoji to show
     * @return a copy of this button
     */
    public LinkButton emoji(EmojiUnion emoji) {
        return new LinkButton(url, label, icon, emoji);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        EmojiUnion resolved =
                literalEmoji != null
                        ? literalEmoji
                        : icon == null ? null : Looks.icon(ctx.preset(), icon).orElse(null);
        return Button.of(ButtonStyle.LINK, url, label, resolved);
    }
}
