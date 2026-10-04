package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.IconKey;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

/**
 * The title of a menu, and optionally a line beneath it.
 *
 * <p>Three things come from the preset: how many hash characters make the heading, whether
 * a subtitle is rendered at all, and which icon sits before the title. The words are the
 * caller's, because they are text a user reads and therefore already localized.
 *
 * <p>A subtitle the preset does not want is dropped rather than rendered. That is the
 * difference between "this menu offers a subtitle" and "this menu has one", and a
 * compact preset that quietly grew a second line of headings would not be compact.
 */
public final class Header implements MenuComponent {

    /** Discord's small-text syntax, which must follow a line of ordinary text. */
    private static final String SMALL_TEXT_PREFIX = "-# ";

    private final String title;
    private final String subtitle;
    private final IconKey icon;

    private Header(String title, String subtitle, IconKey icon) {
        this.title = title;
        this.subtitle = subtitle;
        this.icon = icon;
    }

    /**
     * A header with a title and nothing else.
     *
     * @param title the menu's title
     * @return the component
     */
    public static Header of(String title) {
        return new Header(title, null, null);
    }

    /**
     * A header with a line beneath the title.
     *
     * @param subtitle the line, rendered only when the preset wants one
     * @return a copy of this header
     */
    public Header subtitle(String subtitle) {
        return new Header(title, subtitle, icon);
    }

    /**
     * A header showing a semantic icon before the title.
     *
     * @param icon the meaning, resolved from the preset
     * @return a copy of this header
     */
    public Header icon(IconKey icon) {
        return new Header(title, subtitle, icon);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        StringBuilder text = new StringBuilder();
        text.append("#".repeat(ctx.preset().header().level()));
        text.append(' ');

        if (icon != null) {
            String iconText = Looks.iconText(ctx.preset(), icon);
            if (!iconText.isEmpty()) {
                text.append(iconText).append(' ');
            }
        }
        text.append(title);

        if (subtitle != null && ctx.preset().header().subtitle()) {
            text.append('\n').append(SMALL_TEXT_PREFIX).append(subtitle);
        }
        return List.of(TextDisplay.of(text.toString()));
    }
}
