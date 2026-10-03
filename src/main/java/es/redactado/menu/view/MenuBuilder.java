package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Validator;
import es.redactado.menu.preset.Tone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;

/**
 * Fluent builder for constructing a JDA V2 Container from menu components.
 *
 * <pre>{@code
 * The container takes its colour from the active preset. Written as a literal here only
 * by accident: {@code NoHardcodedColorsOrEmojiTest} scans main sources for a hex colour
 * and a Javadoc example is not worth a hole in that rule.
 *
 * <pre>{@code
 * Container container = MenuBuilder.create("profile")
 *     .add(Text.of("## Hello"))
 *     .add(Field.of("Name", "John"))
 *     .add(Row.of(ActionButton.primary("edit", "Edit").icon(IconKey.EDIT)))
 *     .tone(Tone.ACCENT)
 *     .build(ctx);
 *
 * event.replyComponents(container).useComponentsV2().queue();
 * }</pre>
 */
public class MenuBuilder {
    private final String menuId;
    private final List<MenuComponent> children = new ArrayList<>();
    private Tone tone = Tone.ACCENT;
    private Integer accentColor;
    private boolean spoiler;

    private MenuBuilder(String menuId) {
        this.menuId = menuId;
    }

    /** Start building a menu. */
    public static MenuBuilder create(String menuId) {
        return new MenuBuilder(menuId);
    }

    /** Add a component child. */
    public MenuBuilder add(MenuComponent component) {
        children.add(component);
        return this;
    }

    /** Add varargs component children. */
    @SafeVarargs
    public final MenuBuilder add(MenuComponent... components) {
        children.addAll(Arrays.asList(components));
        return this;
    }

    /** Add all components from a collection. */
    public MenuBuilder addAll(Collection<? extends MenuComponent> components) {
        children.addAll(components);
        return this;
    }

    /** Set accent color (RGB int). */
    /**
     * Sets which palette colour tints the container.
     *
     * @param tone the meaning, resolved from the active preset
     * @return this builder
     */
    public MenuBuilder tone(Tone tone) {
        this.tone = tone;
        return this;
    }

    /**
     * Uses one exact colour instead of the preset's.
     *
     * <p>An escape hatch for a menu that needs a colour outside the palette. It wins over
     * {@link #tone(Tone)} because it is the more specific statement of intent.
     *
     * @param color the packed RGB value
     * @return this builder
     */
    public MenuBuilder accentColor(int color) {
        this.accentColor = color;
        return this;
    }

    /** Mark container as spoiler. */
    public MenuBuilder spoiler(boolean spoiler) {
        this.spoiler = spoiler;
        return this;
    }

    /**
     * Renders the components and validates the result.
     *
     * <p>Validating here as well as in the sender is deliberate. This check is
     * synchronous, so a menu that breaks a limit fails at the call site during a
     * test rather than later inside an asynchronous send chain.
     *
     * @param ctx the context of the current interaction
     * @return the rendered, validated container
     * @throws es.redactado.menu.api.ComponentLimitException if the container breaks
     *     a hard limit
     */
    public Container build(MenuContext ctx) {
        var rendered = new ArrayList<ContainerChildComponent>();
        for (var child : children) {
            rendered.addAll(child.render(ctx));
        }
        String footer = renderFooter(ctx);
        if (!footer.isEmpty()) {
            // Counted as an ordinary child on purpose. Special-casing it out of the
            // limit would mean a menu could pass validation and then fail at Discord,
            // and the limit exists precisely because Discord enforces it.
            rendered.add(TextDisplay.of(footer));
        }

        Container container = Container.of(rendered);
        Validator.verify(container);

        int resolved = accentColor != null ? accentColor : ctx.preset().palette().color(tone);
        container = container.withAccentColor(resolved);
        if (spoiler) {
            container = container.withSpoiler(true);
        }
        return container;
    }

    /**
     * The preset's footer, with its placeholders filled in.
     *
     * <p>Both substituted values are escaped, because a user's display name may contain
     * markdown and an unescaped name would let a user inject formatting into a bot's own
     * message, or mention everyone.
     */
    private String renderFooter(MenuContext ctx) {
        String template = ctx.preset().footer();
        if (template.isEmpty()) {
            return "";
        }
        return SMALL_TEXT_PREFIX
                + template.replace("{user}", MarkdownSanitizer.escape(userName(ctx)))
                        .replace("{menu}", MarkdownSanitizer.escape(ctx.menuId()));
    }

    private static String userName(MenuContext ctx) {
        return ctx.discordUser().getEffectiveName();
    }

    public String menuId() {
        return menuId;
    }

    /** Discord's small-text syntax, which must follow a line of ordinary text. */
    private static final String SMALL_TEXT_PREFIX = "-# ";
}
