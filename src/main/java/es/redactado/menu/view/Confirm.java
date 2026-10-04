package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

/**
 * A prompt with a yes and a no.
 *
 * <p><strong>The pattern.</strong> A confirmation is a view, not a flag on the view that
 * triggered it:
 *
 * <pre>{@code
 * // The delete button opens the confirmation rather than deleting.
 * ActionButton.danger("delete", labels.delete()).icon(IconKey.DELETE)
 *
 * // The confirmation view asks.
 * Confirm.of(Text.of(labels.deletePrompt()), "reallyDelete", entityId)
 *
 * // The handler does the work and returns to where the user came from.
 * private void reallyDelete(MenuContext ctx, ButtonInteractionEvent event) {
 *     executor.supply(() -> service.delete(ctx.requireString(0)));
 *     return ctx.navigate(NavigationMode.BACK, "");
 * }
 * }</pre>
 *
 * <p>Making it a view is what makes cancel free: the button is a plain
 * {@link Nav#back()}, so leaving costs nothing and there is no state to unwind. A boolean
 * parameter on the same view would need the previous view remembered somewhere else.
 *
 * <p>Cancel goes back rather than running an action of its own, so a confirmation always
 * returns to exactly where it was opened from instead of to a hardcoded place.
 *
 * <p>The confirm button asks for a {@link ButtonRole}, not a style, so a monochrome
 * preset renders it as quietly as it renders anything else.
 */
public final class Confirm implements MenuComponent {

    private final MenuComponent prompt;
    private final String yesAction;
    private final String[] yesParams;
    private final ButtonRole yesRole;
    private final String yesLabel;
    private final String noLabel;

    private Confirm(
            MenuComponent prompt,
            String yesAction,
            String[] yesParams,
            ButtonRole yesRole,
            String yesLabel,
            String noLabel) {
        this.prompt = prompt;
        this.yesAction = yesAction;
        this.yesParams = yesParams;
        this.yesRole = yesRole;
        this.yesLabel = yesLabel;
        this.noLabel = noLabel;
    }

    /**
     * A confirmation whose answer runs an action.
     *
     * @param prompt what the user is being asked to confirm
     * @param yesAction the action the confirm button runs
     * @param yesParams parameters for that action
     * @return the component
     * @throws IllegalArgumentException if the prompt or the action is missing
     */
    public static Confirm of(MenuComponent prompt, String yesAction, String... yesParams) {
        if (prompt == null) {
            throw new IllegalArgumentException("A confirmation needs a prompt");
        }
        if (yesAction == null || yesAction.isEmpty()) {
            throw new IllegalArgumentException("A confirmation needs an action to confirm");
        }
        if (yesParams == null) {
            throw new IllegalArgumentException("A confirmation's params must not be null");
        }
        return new Confirm(prompt, yesAction, yesParams.clone(), ButtonRole.SUCCESS, null, null);
    }

    /**
     * Makes the confirm button destructive.
     *
     * <p>For the common case where the thing being confirmed cannot be undone, the
     * confirmation should look like the danger it is.
     *
     * @return a copy of this confirmation
     */
    public Confirm danger() {
        return new Confirm(prompt, yesAction, yesParams, ButtonRole.DANGER, yesLabel, noLabel);
    }

    /**
     * Replaces the confirm button's text.
     *
     * @param label the text, already localized
     * @return a copy of this confirmation
     */
    public Confirm yesLabel(String label) {
        return new Confirm(prompt, yesAction, yesParams, yesRole, label, noLabel);
    }

    /**
     * Replaces the cancel button's text.
     *
     * @param label the text, already localized
     * @return a copy of this confirmation
     */
    public Confirm noLabel(String label) {
        return new Confirm(prompt, yesAction, yesParams, yesRole, yesLabel, label);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        List<ContainerChildComponent> out = new ArrayList<>(prompt.render(ctx));
        out.addAll(Row.of(confirm(ctx), cancel(ctx)).render(ctx));
        return out;
    }

    private RowItem confirm(MenuContext ctx) {
        return confirmButton(resolveYes(ctx)).params(yesParams).icon(IconKey.CONFIRM);
    }

    /**
     * The confirm button, chosen by role.
     *
     * <p>A factory per role rather than a style, because {@link ActionButton} takes the
     * role and the preset turns that into a colour.
     */
    private ActionButton confirmButton(String label) {
        return switch (yesRole) {
            case SUCCESS -> ActionButton.success(yesAction, label);
            case DANGER -> ActionButton.danger(yesAction, label);
            case PRIMARY -> ActionButton.primary(yesAction, label);
            case SECONDARY -> ActionButton.secondary(yesAction, label);
        };
    }

    private RowItem cancel(MenuContext ctx) {
        return Nav.back().icon(IconKey.CANCEL).label(resolveNo(ctx));
    }

    private String resolveYes(MenuContext ctx) {
        return yesLabel != null ? yesLabel : ctx.t(MessageKeys.CONFIRM_YES);
    }

    private String resolveNo(MenuContext ctx) {
        return noLabel != null ? noLabel : ctx.t(MessageKeys.CONFIRM_NO);
    }
}
