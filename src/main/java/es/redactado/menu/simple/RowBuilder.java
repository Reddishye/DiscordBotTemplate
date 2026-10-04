package es.redactado.menu.simple;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Msg;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.view.LinkButton;
import es.redactado.menu.view.Nav;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.RowItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Collects the items of one row.
 *
 * <p>A row holds buttons, or one select, and never both: that is a rule of the component
 * rather than of this DSL, and a select gets a row of its own so the rule holds by
 * construction.
 *
 * <p>Every button method returns a {@link ButtonSpec} for the icon, params, disabled state
 * and modal flag that only some buttons need; {@link ButtonSpec#and()} comes back here for
 * the next one.
 *
 * <pre>{@code
 * row(r -> r
 *     .primary("save", Msg.key("common.save"), click -> click.refresh()).icon(IconKey.OK).and()
 *     .link(Msg.key("help.docs"), "https://example.com")
 *     .back())
 * }</pre>
 *
 * <p>Mutable and not thread-safe; part of the one pass through the DSL.
 *
 * @param <M> the model type of the menu
 */
public final class RowBuilder<M> {

    private final String viewName;
    private final List<Function<MenuContext, RowItem>> items = new ArrayList<>();
    private final List<ButtonSpec> buttons = new ArrayList<>();

    RowBuilder(String viewName) {
        this.viewName = viewName;
    }

    /**
     * The menu's main action.
     *
     * @param action the action name; menu-wide, and one view may own it
     * @param label the button's text, from a key
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec primary(String action, Msg label, ClickHandler handler) {
        return button(action, ButtonRole.PRIMARY, label, handler);
    }

    /**
     * A secondary action.
     *
     * @param action the action name
     * @param label the button's text, from a key
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec secondary(String action, Msg label, ClickHandler handler) {
        return button(action, ButtonRole.SECONDARY, label, handler);
    }

    /**
     * An action that worked.
     *
     * @param action the action name
     * @param label the button's text, from a key
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec success(String action, Msg label, ClickHandler handler) {
        return button(action, ButtonRole.SUCCESS, label, handler);
    }

    /**
     * An action that destroys something.
     *
     * @param action the action name
     * @param label the button's text, from a key
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec danger(String action, Msg label, ClickHandler handler) {
        return button(action, ButtonRole.DANGER, label, handler);
    }

    /**
     * The main action, with literal text.
     *
     * <p>For a label that is content rather than chrome, such as a name from the data.
     * Anything a user reads in more than one language wants {@link Msg#key}.
     *
     * @param action the action name
     * @param label the button's text
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec primary(String action, String label, ClickHandler handler) {
        return primary(action, Msg.literal(label), handler);
    }

    /**
     * A secondary action, with literal text.
     *
     * @param action the action name
     * @param label the button's text
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec secondary(String action, String label, ClickHandler handler) {
        return secondary(action, Msg.literal(label), handler);
    }

    /**
     * An action that worked, with literal text.
     *
     * @param action the action name
     * @param label the button's text
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec success(String action, String label, ClickHandler handler) {
        return success(action, Msg.literal(label), handler);
    }

    /**
     * A destructive action, with literal text.
     *
     * @param action the action name
     * @param label the button's text
     * @param handler what the press runs
     * @return the button, for the details only some buttons need
     */
    public ButtonSpec danger(String action, String label, ClickHandler handler) {
        return danger(action, Msg.literal(label), handler);
    }

    private ButtonSpec button(String action, ButtonRole role, Msg label, ClickHandler handler) {
        if (label == null) {
            throw new IllegalArgumentException(
                    "A button label must not be null; use Msg.key for text a user reads");
        }
        if (handler == null) {
            throw new IllegalArgumentException(
                    "Button '" + action + "' in view '" + viewName + "' needs a handler");
        }
        ButtonSpec spec = new ButtonSpec(this, action, role, label, handler);
        buttons.add(spec);
        items.add(ctx -> spec.render(ctx));
        return spec;
    }

    /**
     * A button that opens a URL instead of running this menu.
     *
     * <p>Discord's own button, so the link works for everyone regardless of who can see the
     * menu, and the menu learns nothing about it.
     *
     * @param label the button's text, from a key
     * @param url the URL
     * @return this row, so a row reads as one expression
     */
    public RowBuilder<M> link(Msg label, String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A link needs a URL in view '" + viewName + "'");
        }
        items.add(ctx -> LinkButton.of(url, label.get(ctx)));
        return this;
    }

    /**
     * A link button with literal text.
     *
     * @param label the button's text
     * @param url the URL
     * @return this row, so a row reads as one expression
     */
    public RowBuilder<M> link(String label, String url) {
        return link(Msg.literal(label), url);
    }

    /**
     * Goes back to the previous view.
     *
     * <p>Legal in the home view too, where it renders that same view again: the framework
     * has no view below it, and a menu whose home screen offers Back is asking to leave
     * rather than to go back. Harmless, and refusing it would be refusing to let a menu say
     * something true.
     *
     * @return this row
     */
    public RowBuilder<M> back() {
        items.add(ctx -> Nav.back());
        return this;
    }

    /**
     * Opens a view of this menu, keeping this one in history.
     *
     * @param viewName a view declared on this menu
     * @param label the button's text
     * @return this row
     */
    public RowBuilder<M> view(String viewName, String label) {
        if (viewName == null || viewName.isEmpty()) {
            throw new IllegalArgumentException(
                    "A view button needs a view name in view '" + viewName + "'");
        }
        items.add(ctx -> Nav.view(viewName, label));
        return this;
    }

    /**
     * Anything the DSL has no word for.
     *
     * @param item the row item, rendered as it came
     * @return this row
     */
    public RowBuilder<M> item(RowItem item) {
        if (item == null) {
            throw new IllegalArgumentException("A row item must not be null");
        }
        items.add(ctx -> item);
        return this;
    }

    /**
     * Registers the row's buttons and checks the row against the component rules.
     *
     * <p>Called when the row is declared rather than when it renders, because the params a
     * button carries are set after the button itself and the checks need them.
     *
     * @param declarations the menu-wide facts to record the actions in
     * @throws IllegalArgumentException if the row has more buttons than a row can hold
     */
    void register(Declarations<M> declarations) {
        int total = items.size();
        if (total == 0) {
            throw new IllegalArgumentException(
                    "A row in view '" + viewName + "' has nothing in it");
        }
        if (total > Limits.MAX_ACTION_ROW_CHILDREN) {
            throw new IllegalArgumentException(
                    ("Row in view '%s' has %d buttons, a row holds at most %d")
                            .formatted(viewName, total, Limits.MAX_ACTION_ROW_CHILDREN));
        }
        for (ButtonSpec spec : buttons) {
            declarations.button(
                    viewName, spec.action(), spec.paramList(), spec.modal(), spec.handler());
        }
    }

    /**
     * Renders the row for one interaction.
     *
     * @param ctx the context, which the labels resolve against
     * @return the row
     */
    Row render(MenuContext ctx) {
        List<RowItem> built = new ArrayList<>(items.size());
        for (Function<MenuContext, RowItem> item : items) {
            built.add(item.apply(ctx));
        }
        return Row.of(built.toArray(new RowItem[0]));
    }
}
