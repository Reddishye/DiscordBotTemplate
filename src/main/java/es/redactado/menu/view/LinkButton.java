package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * A button that opens a URL and therefore routes nowhere. Place it in a
 * {@link Row}.
 */
public final class LinkButton implements RowItem {

    private final String url;
    private final String label;
    private final Emoji emoji;

    private LinkButton(String url, String label, Emoji emoji) {
        this.url = url;
        this.label = label;
        this.emoji = emoji;
    }

    /**
     * Creates a link button.
     *
     * @param url the target URL
     * @param label the button text
     * @return the button
     */
    public static LinkButton of(String url, String label) {
        return new LinkButton(url, label, null);
    }

    /**
     * Returns a copy carrying the given emoji.
     *
     * @param emoji the emoji to show on the button
     * @return a new button
     */
    public LinkButton emoji(Emoji emoji) {
        return new LinkButton(url, label, emoji);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        return Button.of(ButtonStyle.LINK, url, label, emoji);
    }
}
