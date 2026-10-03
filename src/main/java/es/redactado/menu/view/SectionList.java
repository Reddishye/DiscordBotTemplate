package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.preset.IconKey;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

/**
 * Paginated list of items with page navigation.
 * Each item is rendered by a renderer function.
 */
public class SectionList<T> implements MenuComponent {

    private static final String PAGE_ACTION = "sectionlist_page";
    private static final String NO_ACTION = "noop";

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
            result.add(TextDisplay.of(ctx.t(es.redactado.menu.core.MessageKeys.LIST_EMPTY)));
            return result;
        }

        int totalPages = (items.size() + pageSize - 1) / pageSize;
        Integer rawPage = ctx.session().state("page_" + stateKey, Integer.class).orElse(null);
        int page = rawPage != null ? Math.min(rawPage, totalPages - 1) : 0;
        int from = page * pageSize;
        int to = Math.min(from + pageSize, items.size());

        for (int i = from; i < to; i++) {
            result.add(renderer.render(items.get(i), ctx));
        }

        if (totalPages > 1) {
            result.addAll(Row.of(navigation(page, totalPages)).render(ctx));
        }

        return result;
    }

    private RowItem[] navigation(int page, int totalPages) {
        String pageText = "%d/%d".formatted(page + 1, totalPages);
        List<RowItem> nav = new ArrayList<>();
        if (page > 0) {
            nav.add(c -> pageButton(c, page - 1, IconKey.PREVIOUS));
        }
        nav.add(c -> Button.of(ButtonStyle.SECONDARY, NO_ACTION, pageText));
        if (page < totalPages - 1) {
            nav.add(c -> pageButton(c, page + 1, IconKey.NEXT));
        }
        return nav.toArray(new RowItem[0]);
    }

    private Button pageButton(MenuContext ctx, int targetPage, IconKey direction) {
        return Button.of(
                ButtonStyle.SECONDARY,
                ComponentId.encode(ctx.menuId(), PAGE_ACTION, stateKey, String.valueOf(targetPage)),
                Looks.icon(ctx.preset(), direction).orElse(null));
    }
}
