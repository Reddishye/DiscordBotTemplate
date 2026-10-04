package es.redactado.menu.simple;

import es.redactado.menu.preset.IconKey;
import java.util.List;

/**
 * One button, on its way to being finished.
 *
 * <p>Returned by every {@link RowBuilder} button method so that the details which only some
 * buttons have can be set without the author writing a placeholder first:
 *
 * <pre>{@code
 * r.primary("save", Msg.key("common.save"), click -> click.done())
 *  .icon(IconKey.OK)
 *  .disabled(true)
 *  .and()
 *  .secondary("cancel", Msg.key("common.cancel"), click -> click.back())
 * }</pre>
 *
 * <p>{@link #and()} returns to the row, because a row is what is being built and a button is
 * one of several things in it. The alternative, a builder that lets every button method be
 * called on every other, is a wider surface for the same result.
 *
 * <p>Mutable and not thread-safe; part of the one pass through the DSL.
 */
public final class ButtonSpec {

    private final RowBuilder<?> row;
    private final String action;
    private final es.redactado.menu.preset.ButtonRole role;
    private final es.redactado.menu.api.Msg label;
    private final ClickHandler handler;
    private IconKey icon;
    private boolean disabled;
    private boolean opensModal;
    private String[] params = new String[0];

    ButtonSpec(
            RowBuilder<?> row,
            String action,
            es.redactado.menu.preset.ButtonRole role,
            es.redactado.menu.api.Msg label,
            ClickHandler handler) {
        this.row = row;
        this.action = action;
        this.role = role;
        this.label = label;
        this.handler = handler;
    }

    /**
     * Gives the button an icon, resolved from the preset.
     *
     * <p>Semantic rather than literal: the author names what the button means and the preset
     * decides which glyph that is, so one bot can be restyled without a code change.
     *
     * @param icon the meaning
     * @return this button
     */
    public ButtonSpec icon(IconKey icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Appends parameters to the button's component id.
     *
     * <p>What the handler reads back from {@link Click#ctx()}. Checked against the id limit
     * here, where the parameters are known, rather than at the first click.
     *
     * @param params the parameters
     * @return this button
     */
    public ButtonSpec params(String... params) {
        this.params = params == null ? new String[0] : params.clone();
        return this;
    }

    /**
     * Shows the button greyed out, still doing nothing when pressed.
     *
     * @param disabled whether it is unavailable
     * @return this button
     */
    public ButtonSpec disabled(boolean disabled) {
        this.disabled = disabled;
        return this;
    }

    /**
     * Declares that pressing this button opens a modal.
     *
     * <p>Which tells the router to acknowledge with {@code MODAL} rather than deferring an
     * edit, because a modal has to be the only answer and deferring would spend the
     * interaction. It also lets the handler call {@link Click#modal}; without this the call
     * is refused, since the interaction would already have been acknowledged.
     *
     * @return this button
     */
    public ButtonSpec opensModal() {
        this.opensModal = true;
        return this;
    }

    /**
     * Goes back to the row, for the next button.
     *
     * @return the row being built
     */
    public RowBuilder<?> and() {
        return row;
    }

    String action() {
        return action;
    }

    es.redactado.menu.preset.ButtonRole role() {
        return role;
    }

    es.redactado.menu.api.Msg label() {
        return label;
    }

    ClickHandler handler() {
        return handler;
    }

    IconKey icon() {
        return icon;
    }

    boolean disabled() {
        return disabled;
    }

    /** Whether this button was declared as opening a modal. */
    boolean modal() {
        return opensModal;
    }

    String[] params() {
        return params;
    }

    /** Builds the row item this button renders to. */
    es.redactado.menu.view.RowItem render(es.redactado.menu.api.MenuContext ctx) {
        es.redactado.menu.view.ActionButton button =
                switch (role) {
                    case PRIMARY ->
                            es.redactado.menu.view.ActionButton.primary(action, label.get(ctx));
                    case SECONDARY ->
                            es.redactado.menu.view.ActionButton.secondary(action, label.get(ctx));
                    case SUCCESS ->
                            es.redactado.menu.view.ActionButton.success(action, label.get(ctx));
                    case DANGER ->
                            es.redactado.menu.view.ActionButton.danger(action, label.get(ctx));
                };
        if (icon != null) {
            button = button.icon(icon);
        }
        return button.disabled(disabled).params(params);
    }

    /** The parameters as an immutable list, for the declaration to check. */
    List<String> paramList() {
        return List.of(params);
    }
}
