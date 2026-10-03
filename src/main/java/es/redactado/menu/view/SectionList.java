package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * Paginated list of items with page navigation.
 * Each item is rendered by a renderer function.
 */
public class SectionList<T> implements MenuComponent {

    @FunctionalInterface
    public interface ItemRenderer<T> {
        Section render(T item, MenuContext ctx);
    }

    private final List<T> items;
    private final ItemRenderer<T> renderer;
    private final int pageSize;
    private final String stateKey;

    public SectionList(List<T> items, ItemRenderer<T> renderer, int pageSize, String stateKey) {
        this.items = items;
        this.renderer = renderer;
        this.pageSize = pageSize;
        this.stateKey = stateKey;
    }

    public SectionList(List<T> items, ItemRenderer<T> renderer) {
        this(items, renderer, 5, "sectionlist_page");
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        var result = new ArrayList<ContainerChildComponent>();
        if (items.isEmpty()) {
            result.add(TextDisplay.of("*No items.*"));
            return result;
        }

        int totalPages = (items.size() + pageSize - 1) / pageSize;
        Integer rawPage = ctx.state("page_" + stateKey);
        int page = rawPage != null ? Math.min(rawPage, totalPages - 1) : 0;
        int from = page * pageSize;
        int to = Math.min(from + pageSize, items.size());

        for (int i = from; i < to; i++) {
            result.add(renderer.render(items.get(i), ctx));
        }

        if (totalPages > 1) {
            String pageText = "%d/%d".formatted(page + 1, totalPages);
            var navBtns = new ArrayList<Button>();
            if (page > 0) {
                navBtns.add(
                        Button.of(
                                ButtonStyle.SECONDARY,
                                ComponentId.encode(
                                        ctx.menuId(),
                                        "sectionlist_page",
                                        stateKey,
                                        String.valueOf(page - 1)),
                                Emoji.fromUnicode("◀")));
            }
            navBtns.add(Button.of(ButtonStyle.SECONDARY, "noop", pageText));
            if (page < totalPages - 1) {
                navBtns.add(
                        Button.of(
                                ButtonStyle.SECONDARY,
                                ComponentId.encode(
                                        ctx.menuId(),
                                        "sectionlist_page",
                                        stateKey,
                                        String.valueOf(page + 1)),
                                Emoji.fromUnicode("▶")));
            }
            result.add(
                    (ContainerChildComponent)
                            net.dv8tion.jda.api.components.actionrow.ActionRow.of(navBtns));
        }

        return result;
    }
}
