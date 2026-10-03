package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/**
 * Fluent builder for constructing a JDA V2 Container from menu components.
 *
 * <pre>{@code
 * Container container = MenuBuilder.create("profile")
 *     .add(Text.of("## Hello"))
 *     .add(Field.of("Name", "John"))
 *     .add(Row.of(ActionButton.primary("edit", "Edit")))
 *     .accentColor(0x5865F2)
 *     .build(ctx);
 *
 * event.replyComponents(container).useComponentsV2().queue();
 * }</pre>
 */
public class MenuBuilder {
    private final String menuId;
    private final List<MenuComponent> children = new ArrayList<>();
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
    public MenuBuilder accentColor(int color) {
        this.accentColor = color;
        return this;
    }

    /** Mark container as spoiler. */
    public MenuBuilder spoiler(boolean spoiler) {
        this.spoiler = spoiler;
        return this;
    }

    /** Render and validate into a JDA Container. */
    public Container build(MenuContext ctx) {
        var rendered = new ArrayList<ContainerChildComponent>();
        for (var child : children) {
            rendered.addAll(child.render(ctx));
        }
        Container container = Container.of(rendered);
        Validator.verify(container);
        if (accentColor != null) {
            container = container.withAccentColor(accentColor);
        }
        if (spoiler) {
            container = container.withSpoiler(true);
        }
        return container;
    }

    public String menuId() {
        return menuId;
    }
}
