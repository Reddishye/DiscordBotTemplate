package es.redactado.menu.simple;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Collects the elements of one view.
 *
 * <p>Every method returns the same builder, so a view reads as one expression:
 *
 * <pre>{@code
 * .home(v -> v
 *     .header(Msg.key("help.title"), Msg.key("help.subtitle"))
 *     .text("Pick a topic.")
 *     .row(r -> r.primary("faq", "FAQ", click -> click.go("faq"))))
 * }</pre>
 *
 * <p>Nothing is rendered here. The elements are collected, checked when the menu is built,
 * and only resolved when someone opens the menu, which is what lets the same declared view
 * render in two languages and keep its text out of the building thread.
 *
 * <p>Mutable and not thread-safe: one of these is used while a menu is being declared and
 * then thrown away.
 *
 * @param <M> the model type of the menu, or {@link Void} when it declares no loader
 */
public final class ViewBuilder<M> {

    private final String viewName;
    private final List<Element<M>> elements = new ArrayList<>();
    private final List<String> ownedActions = new ArrayList<>();
    private final Declarations<M> declarations;

    ViewBuilder(String viewName, Declarations<M> declarations) {
        this.viewName = viewName;
        this.declarations = declarations;
    }

    /**
     * A title, with no line under it.
     *
     * @param title the title, localized
     * @return this builder
     */
    public ViewBuilder<M> header(Msg title) {
        return header(title, null);
    }

    /**
     * A title and a line under it.
     *
     * <p>The subtitle is dropped, not shown small, by a preset whose header does not want
     * one. That is the difference between a menu that offers a subtitle and one that has
     * it.
     *
     * @param title the title, localized
     * @param subtitle the line under the title, localized, or null for none
     * @return this builder
     */
    public ViewBuilder<M> header(Msg title, Msg subtitle) {
        elements.add(new Elements.HeaderElement<>(title, subtitle));
        return this;
    }

    /**
     * A title from a key or a literal.
     *
     * <p>Shorthand for a single-argument {@link #header(Msg)}, for the title that never
     * changes.
     *
     * @param title a message key, or literal text
     * @return this builder
     */
    public ViewBuilder<M> header(String title) {
        return header(Msg.key(title));
    }

    /**
     * A line of text.
     *
     * @param text the text
     * @return this builder
     */
    public ViewBuilder<M> text(String text) {
        elements.add(new Elements.TextElement<>(scope -> text));
        return this;
    }

    /**
     * A line of text, localized.
     *
     * <p>Named {@code message} rather than {@code text} because {@code text} also takes a
     * function of the scope, and {@link Msg} being a functional interface would leave every
     * {@code text(s -> ...)} ambiguous between the two. A rule that only works when the
     * author casts is not a rule.
     *
     * @param text the text
     * @return this builder
     */
    public ViewBuilder<M> message(Msg text) {
        elements.add(new Elements.TextElement<>(scope -> text.get(scope.ctx())));
        return this;
    }

    /**
     * A line of text built from the loaded model.
     *
     * <p>Called once per render with the context and whatever the loader returned, so the
     * same declared view can show something different for each user or each moment.
     *
     * @param text builds the line from the current scope
     * @return this builder
     */
    public ViewBuilder<M> text(Function<Scope<M>, String> text) {
        elements.add(new Elements.TextElement<>(text));
        return this;
    }

    /**
     * A labelled value, laid out beside any other field.
     *
     * @param label the label, localized
     * @param value builds the value from the current scope
     * @return this builder
     */
    public ViewBuilder<M> field(Msg label, Function<Scope<M>, String> value) {
        elements.add(new Elements.FieldElement<>(label, value));
        return this;
    }

    /**
     * A horizontal rule, or whatever the preset uses for one.
     *
     * @return this builder
     */
    public ViewBuilder<M> divider() {
        elements.add(new Elements.DividerElement<>(true));
        return this;
    }

    /**
     * A blank line.
     *
     * @return this builder
     */
    public ViewBuilder<M> space() {
        elements.add(new Elements.DividerElement<>(false));
        return this;
    }

    /**
     * Text with an image beside it.
     *
     * @param text the text, localized
     * @param thumbnailUrl a remote image URL
     * @return this builder
     */
    public ViewBuilder<M> section(Msg text, String thumbnailUrl) {
        elements.add(new Elements.SectionElement<>(text, thumbnailUrl));
        return this;
    }

    /**
     * A row of buttons, links or navigation.
     *
     * <p>A row holds at most five items and no select: {@link #select} gives the select a row
     * of its own, which is how the component's own rule holds by construction rather than by
     * the DSL policing it.
     *
     * @param row builds the row
     * @return this builder
     * @throws IllegalArgumentException if the row holds more items than a row can
     */
    public ViewBuilder<M> row(Consumer<RowBuilder<M>> row) {
        RowBuilder<M> builder = new RowBuilder<>(viewName);
        row.accept(builder);
        builder.register(declarations);
        elements.add(new Elements.RowElement<>(builder));
        return this;
    }

    /**
     * A select menu.
     *
     * <p>Menu-wide by action name, like every other action, and owned by this view for the
     * purpose of knowing what a redraw should render.
     *
     * @param action the action name
     * @param placeholder the text shown before anything is chosen, localized
     * @param options builds the options
     * @param handler what the choice runs
     * @return this builder
     */
    public ViewBuilder<M> select(
            String action, Msg placeholder, Consumer<SelectSpec> options, PickHandler handler) {
        if (placeholder == null) {
            throw new IllegalArgumentException(
                    "Select '" + action + "' in view '" + viewName + "' needs a placeholder");
        }
        if (handler == null) {
            throw new IllegalArgumentException(
                    "Select '" + action + "' in view '" + viewName + "' needs a handler");
        }
        SelectSpec spec = new SelectSpec();
        options.accept(spec);
        spec.check("'" + action + "' in view '" + viewName + "'");
        declarations.select(viewName, action, handler);
        ownedActions.add(action);
        elements.add(new Elements.SelectElement<>(action, placeholder, spec));
        return this;
    }

    /**
     * A paged list of text items.
     *
     * <p>The whole list is supplied on every render and the page is remembered in the
     * session, so paging survives the redraw the framework does for each click.
     *
     * @param id identifies the list; {@code [a-z0-9_]{1,20}}, unique in the menu
     * @param items the whole list, for this render
     * @param pageSize how many items one page shows, from 1 to 20
     * @param itemText turns an item into the line that shows it
     * @param <T> the item type
     * @return this builder
     */
    public <T> ViewBuilder<M> list(
            String id,
            Function<Scope<M>, List<T>> items,
            int pageSize,
            Function<T, String> itemText) {
        declarations.list(id);
        elements.add(new Elements.ListElement<>(id, items, pageSize, itemText));
        return this;
    }

    /**
     * A component the DSL has no word for.
     *
     * <p>Built once per render rather than shared, so an element that resolves something
     * from the context still does so here.
     *
     * @param component builds the component on every render
     * @return this builder
     */
    public ViewBuilder<M> custom(Function<Scope<M>, MenuComponent> component) {
        elements.add(new Elements.ScopedElement<>(component));
        return this;
    }

    /**
     * A component the DSL has no word for, the same every time.
     *
     * @param component the component
     * @return this builder
     */
    public ViewBuilder<M> custom(MenuComponent component) {
        elements.add(new Elements.CustomElement<>(() -> component));
        return this;
    }

    /** Records that this view owns an action. */
    void own(String action) {
        ownedActions.add(action);
    }

    /** The name of the view being declared. */
    String viewName() {
        return viewName;
    }

    /** The elements, still mutable, for the menu to take when the view is finished. */
    List<Element<M>> elements() {
        return elements;
    }

    /** The action names this view owns. */
    List<String> ownedActions() {
        return ownedActions;
    }
}
