package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.core.PageAction;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

/**
 * One page of a long list, with the controls to move between pages.
 *
 * <p>The page lives in the session under {@code pager:<id>}, so two pagers in the same
 * view keep their own places and the same pager reached by two different views keeps its
 * place. The id is what makes that possible, and it is restricted to characters that
 * survive the custom-id encoding unescaped.
 *
 * <p><strong>The stored page is never trusted.</strong> A session outlives the render
 * that wrote it: the list can shrink, or the message can be reopened after the bot
 * restarted, so the stored value is clamped to the range that actually exists rather
 * than assumed to be right. A pager that rendered page 9 of 3 items would otherwise
 * show nothing at all, with no error anywhere.
 *
 * <p>Rendering is {@code O(pageSize)}. The current page is a {@code subList} view, not
 * a copy, so a list of ten thousand items costs the same as a list of twenty.
 */
public final class Pager<T> implements MenuComponent {

    /** A pager id safe to embed in a custom id without escaping. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,20}");

    /** The action every page button uses, reserved like {@code nav}. */
    public static final String PAGE_ACTION =
            PageAction.class
                    .getSimpleName()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replace("action", "");

    /** The key this pager's current page is stored under, within its session. */
    public static String stateKey(String id) {
        return PageAction.stateKey(id);
    }

    private final String id;
    private final List<T> items;
    private final int pageSize;
    private final Function<T, MenuComponent> renderer;
    private final boolean separated;
    private final String emptyTextKey;

    private Pager(
            String id,
            List<T> items,
            int pageSize,
            Function<T, MenuComponent> renderer,
            boolean separated,
            String emptyTextKey) {
        this.id = id;
        this.items = items;
        this.pageSize = pageSize;
        this.renderer = renderer;
        this.separated = separated;
        this.emptyTextKey = emptyTextKey;
    }

    /**
     * Reports whether a string is a valid pager id.
     *
     * <p>Exposed so that a caller declaring many pagers can check them where it declares
     * them, rather than discovering one bad id at the first render that reaches it. The
     * pattern lives here so there is one rule and not two that can disagree.
     *
     * @param id the candidate id
     * @return whether {@link #of} would accept it
     */
    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    /**
     * Builds a pager.
     *
     * @param id identifies this pager within the view; two pagers in one view must differ
     * @param items the whole list, copied so a later change to the caller's list cannot
     *     change what a menu renders
     * @param pageSize how many items one page shows, from 1 to 20
     * @param renderer turns an item into a component
     * @param <T> the item type
     * @return the component
     * @throws IllegalArgumentException if the id or page size is out of range
     */
    public static <T> Pager<T> of(
            String id, List<T> items, int pageSize, Function<T, MenuComponent> renderer) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Pager id must match %s, got '%s'".formatted(ID.pattern(), id));
        }
        if (pageSize < 1 || pageSize > 20) {
            throw new IllegalArgumentException(
                    "Pager page size must be between 1 and 20, got " + pageSize);
        }
        return new Pager(
                id,
                List.copyOf(items),
                pageSize,
                requireRenderer(renderer),
                false,
                MessageKeys.LIST_EMPTY);
    }

    /**
     * Draws a divider between items.
     *
     * @param separated whether to separate the items on a page
     * @return a copy of this pager
     */
    public Pager<T> separated(boolean separated) {
        return new Pager(id, items, pageSize, renderer, separated, emptyTextKey);
    }

    /**
     * Replaces the text shown when there is nothing to show.
     *
     * @param key a message key, from {@link MessageKeys} or the menu's own bundle
     * @return a copy of this pager
     */
    public Pager<T> emptyText(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A pager empty-text key must not be blank");
        }
        return new Pager(id, items, pageSize, renderer, separated, key);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        if (items.isEmpty()) {
            return List.of(TextDisplay.of(ctx.t(emptyTextKey)));
        }

        int lastPage = lastPage();
        int page = clamp(currentPage(ctx), lastPage);
        int from = page * pageSize;
        int to = Math.min(from + pageSize, items.size());

        List<ContainerChildComponent> out = new java.util.ArrayList<>();
        List<T> window = items.subList(from, to);
        for (int i = 0; i < window.size(); i++) {
            if (separated && i > 0) {
                out.addAll(Divider.line().render(ctx));
            }
            out.addAll(renderer.apply(window.get(i)).render(ctx));
        }

        if (lastPage > 0) {
            out.addAll(controls(ctx, page, lastPage).render(ctx));
        }
        return out;
    }

    /** The page count, so a caller can tell "one page" from "many". */
    private int lastPage() {
        return Math.max(0, (items.size() - 1) / pageSize);
    }

    private int currentPage(MenuContext ctx) {
        // Read without creating: a view holding a pager would otherwise leave a session
        // behind for every message that merely displayed it.
        return ctx.sessionStateOr(stateKey(id), Integer.class, 0);
    }

    /**
     * Brings a page into range.
     *
     * <p>Deliberately tolerant in both directions: a value left over from a longer list
     * must land on the last page rather than showing nothing, and a negative value must
     * land on the first.
     */
    static int clamp(int page, int lastPage) {
        if (page < 0) {
            return 0;
        }
        return Math.min(page, lastPage);
    }

    private Row controls(MenuContext ctx, int page, int lastPage) {
        Button previous = arrow(ctx, "prev", IconKey.PREVIOUS, MessageKeys.PAGER_PREVIOUS);
        Button next = arrow(ctx, "next", IconKey.NEXT, MessageKeys.PAGER_NEXT);
        return Row.of(
                page > 0 ? c -> previous : c -> previous.asDisabled(),
                c -> indicator(ctx, page, lastPage),
                page < lastPage ? c -> next : c -> next.asDisabled());
    }

    /**
     * One arrow button.
     *
     * <p>Both arrows always exist so the row keeps a stable width as the user moves
     * through it; a row that loses a button shifts the other one under the cursor, which
     * is how a mis-click happens.
     *
     * <p>The icon is the label when the preset has one. When it does not, the button needs
     * text instead, because Discord rejects a button with neither: a preset such as
     * {@code minimal} defines no icons at all and would otherwise fail to render. The
     * fallback is a localized word, never a glyph invented here.
     */
    private Button arrow(MenuContext ctx, String direction, IconKey icon, String labelKey) {
        var emoji = Looks.icon(ctx.preset(), icon);
        return Button.of(
                Looks.style(ctx.preset(), ButtonRole.SECONDARY),
                buttonId(ctx, direction),
                emoji.isPresent() ? "" : ctx.t(labelKey),
                emoji.orElse(null));
    }

    /**
     * The {@code x/y} marker.
     *
     * <p>Disabled and icon-free, so it cannot be pressed and does not pretend to be an
     * action. Its label is a number pair rather than a translatable phrase, which is why
     * it needs no key.
     */
    private Button indicator(MenuContext ctx, int page, int lastPage) {
        return Button.of(
                        Looks.style(ctx.preset(), ButtonRole.SECONDARY),
                        "pager-indicator",
                        "%d/%d".formatted(page + 1, lastPage + 1),
                        null)
                .asDisabled();
    }

    /**
     * The id of a page button.
     *
     * <p>Carries the direction, this pager's id, and the view being rendered, so the
     * handler can re-render exactly what the user was looking at without the menu having
     * to keep a stack of its own. A view's own params travel along for the same reason.
     */
    String buttonId(MenuContext ctx, String direction) {
        // Three fixed segments (direction, pager id, view action) then the view's own
        // params. Allocating two would overflow on the view action itself.
        String[] params = new String[3 + ctx.params().size()];
        params[0] = direction;
        params[1] = id;
        params[2] = ctx.action();
        for (int i = 0; i < ctx.params().size(); i++) {
            params[3 + i] = ctx.params().get(i);
        }
        return ComponentId.encode(ctx.menuId(), PAGE_ACTION, params);
    }

    private static <T> Function<T, MenuComponent> requireRenderer(
            Function<T, MenuComponent> renderer) {
        if (renderer == null) {
            throw new IllegalArgumentException("A pager needs a renderer");
        }
        return renderer;
    }
}
