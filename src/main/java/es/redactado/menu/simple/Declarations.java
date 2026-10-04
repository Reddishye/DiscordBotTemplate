package es.redactado.menu.simple;

import es.redactado.menu.api.Limits;
import es.redactado.menu.view.Pager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The facts every view of one menu shares while it is being declared.
 *
 * <p>Passed to each {@link ViewBuilder} so that a name can be checked against the whole menu
 * at the moment it is declared, instead of leaving the author to discover at build time that
 * two views wanted the same action.
 *
 * @param <M> the model type of the menu
 */
final class Declarations<M> {

    /** The prefix every menu component id starts with, as {@code ComponentId} encodes it. */
    private static final int PREFIX_LENGTH = 5;

    private final String menuId;
    private final List<Action<M>> actions = new ArrayList<>();
    private final Map<String, String> actionToView = new HashMap<>();
    private final Set<String> listIds = new HashSet<>();

    Declarations(String menuId) {
        this.menuId = menuId;
    }

    /**
     * Records a list id, refusing one the pager would refuse later or that another list
     * already took.
     *
     * <p>Uniqueness is per menu rather than per view because the paging state is stored under
     * the id alone: two lists sharing an id in one menu would page each other.
     *
     * @param id the list id
     * @throws IllegalArgumentException if the id is not valid for a pager, or is taken
     */
    void list(String id) {
        if (!Pager.isValidId(id)) {
            throw new IllegalArgumentException(
                    ("List id '%s' in menu '%s' must match %s, because it is the paging"
                                    + " state's key and the id of its own buttons")
                            .formatted(id, menuId, "[a-z0-9_]{1,20}"));
        }
        if (!listIds.add(id)) {
            throw new IllegalArgumentException(
                    ("List id '%s' is declared twice in menu '%s'; paging state is stored"
                                    + " under the id alone, so two lists cannot share one")
                            .formatted(id, menuId));
        }
    }

    /**
     * Records a button and the view that owns it.
     *
     * @param view the view declaring it
     * @param action the action name
     * @param params static parameters the button's id will carry
     * @param opensModal whether the button opens a modal
     * @param handler what the press runs
     */
    void button(
            String view,
            String action,
            List<String> params,
            boolean opensModal,
            ClickHandler handler) {
        requireAction(action, view, params);
        remember(
                new Action<>(
                        action,
                        opensModal
                                ? es.redactado.menu.api.Ack.MODAL
                                : es.redactado.menu.api.Ack.DEFER_EDIT,
                        Action.Kind.BUTTON,
                        handler,
                        opensModal,
                        view));
    }

    /**
     * Records a select and the view that owns it.
     *
     * @param view the view declaring it
     * @param action the action name
     * @param handler what the choice runs
     */
    void select(String view, String action, PickHandler handler) {
        requireAction(action, view, List.of());
        remember(
                new Action<>(
                        action,
                        es.redactado.menu.api.Ack.DEFER_EDIT,
                        Action.Kind.SELECT,
                        handler,
                        false,
                        view));
    }

    /**
     * Records a modal submission.
     *
     * <p>Menu-wide rather than per view, because a form is opened by a button on one view
     * and submitted while the message may be showing another; the name is still checked
     * against the button and select names, since one id space decodes all three.
     *
     * @param action the action name
     * @param handler what the submission runs
     */
    void submit(String action, SubmitHandler handler) {
        requireAction(action, null, List.of());
        remember(
                new Action<>(
                        action,
                        es.redactado.menu.api.Ack.DEFER_EDIT,
                        Action.Kind.MODAL,
                        handler,
                        false,
                        null));
    }

    private void remember(Action<M> action) {
        for (Action<M> declared : actions) {
            if (declared.name().equals(action.name())) {
                throw new IllegalStateException(
                        ("Action '%s' is declared twice in menu '%s'%s; one name can only mean"
                                        + " one thing, or the second declaration would silently"
                                        + " replace the first")
                                .formatted(
                                        action.name(),
                                        menuId,
                                        action.view() == null
                                                ? ""
                                                : " (in view '" + action.view() + "')"));
            }
        }
        actions.add(action);
        if (action.view() != null) {
            actionToView.put(action.name(), action.view());
        }
    }

    /** Checks the name and the id it will produce. */
    private void requireAction(String action, String view, List<String> params) {
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException(
                    "An action name must not be empty in menu '%s'".formatted(menuId));
        }
        if (Menus.RESERVED.contains(action)) {
            throw new IllegalArgumentException(
                    ("Action name '%s' in menu '%s' is reserved; 'nav' and 'page' are"
                                    + " registered for every menu and 'home' is the view a menu"
                                    + " opens on, so none of them can mean a button")
                            .formatted(action, menuId));
        }
        if (action.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    ("Action name '%s' in menu '%s' must not contain ':', which separates id"
                                    + " segments")
                            .formatted(action, menuId));
        }
        int used = PREFIX_LENGTH + menuId.length() + 1 + action.length();
        for (String param : params) {
            used += 1 + param.length();
        }
        if (used > Limits.MAX_CUSTOM_ID_LENGTH) {
            throw new IllegalArgumentException(
                    ("Action '%s' in menu '%s' cannot fit in a component id: it needs %d of %d"
                                    + " characters once its params are counted")
                            .formatted(action, menuId, used, Limits.MAX_CUSTOM_ID_LENGTH));
        }
    }

    /**
     * The checks that need every action in front of them.
     *
     * <p>The names were checked as they were declared, because that is where the author is;
     * this re-reads them against the menu id as a whole so that one rule runs in one place
     * for every action kind.
     */
    void validate() {
        for (Action<M> action : actions) {
            requireAction(action.name(), action.view(), List.of());
        }
    }

    /** The actions, in declaration order. */
    List<Action<M>> actions() {
        return List.copyOf(actions);
    }

    /** The action-to-view map, for the menu to hold. */
    Map<String, String> actionToView() {
        return Map.copyOf(actionToView);
    }
}
