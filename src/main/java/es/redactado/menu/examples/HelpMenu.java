package es.redactado.menu.examples;

import es.redactado.menu.api.Menu;
import es.redactado.menu.api.Msg;
import es.redactado.menu.preset.Tone;
import es.redactado.menu.simple.Menus;

/**
 * The smallest useful simple menu: two views, a link and a Back.
 *
 * <p>Nothing here loads anything and nothing here needs a class per view, which is the point
 * of the example. A help menu is static text and navigation, and it should not cost a file
 * with a {@code render} method in it.
 *
 * <p>Built by calling {@link #build()}, never registered by default: an example that opened
 * on a command would be a behaviour change wearing an example's clothes.
 */
public final class HelpMenu {

    /** The menu id, which is also the first segment of every component id. */
    public static final String ID = "help";

    /** The view a menu opens on. */
    public static final String HOME = "home";

    /** The second view, reached by the topic button. */
    public static final String FAQ = "faq";

    private static final String OPEN_FAQ = "open_faq";

    private HelpMenu() {}

    /**
     * Builds the menu.
     *
     * @return a menu, ready to be registered with the router
     */
    public static Menu build() {
        return Menus.simple(ID)
                .tone(Tone.INFO)
                .home(
                        v ->
                                v.header(Msg.literal("Help"), Msg.literal("Pick a topic"))
                                        .text("Everything the bot can do, in two screens.")
                                        .row(
                                                r ->
                                                        r.primary(
                                                                        OPEN_FAQ,
                                                                        "FAQ",
                                                                        click -> click.go(FAQ))
                                                                .and()
                                                                .link(
                                                                        "Docs",
                                                                        "https://example.com/docs")))
                .view(
                        FAQ,
                        v ->
                                v.header(Msg.literal("Frequently asked"))
                                        .text("How do I reset my profile?")
                                        .text("How do I change the language?")
                                        .row(r -> r.back()))
                .build();
    }
}
