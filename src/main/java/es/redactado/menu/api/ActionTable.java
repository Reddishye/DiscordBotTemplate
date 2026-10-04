package es.redactado.menu.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The immutable set of actions a menu handles.
 *
 * <p>Built once at registration and looked up by name on every interaction, so
 * dispatch is a single map access rather than a string switch. Button actions and
 * modal actions live in separate namespaces, so the same name may exist in both.
 */
public final class ActionTable {

    private final Map<String, ButtonAction> buttons;
    private final Map<String, ModalAction> modals;
    private final Map<String, SelectAction> selects;

    private ActionTable(
            Map<String, ButtonAction> buttons,
            Map<String, ModalAction> modals,
            Map<String, SelectAction> selects) {
        this.buttons = Map.copyOf(buttons);
        this.modals = Map.copyOf(modals);
        this.selects = Map.copyOf(selects);
    }

    /**
     * Starts a new action table.
     *
     * @return an empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Looks up a button action.
     *
     * @param name the action name from the component id
     * @return the action, or empty when the menu declares no such button
     */
    public Optional<ButtonAction> button(String name) {
        return Optional.ofNullable(buttons.get(name));
    }

    /**
     * Looks up a modal action.
     *
     * @param name the action name from the modal id
     * @return the action, or empty when the menu declares no such modal
     */
    public Optional<ModalAction> modal(String name) {
        return Optional.ofNullable(modals.get(name));
    }

    /**
     * The select action declared under a name.
     *
     * <p>Namespaces are separate: {@code "save"} can be a button and a select at once,
     * because they arrive through different component types and never collide in a
     * custom id.
     *
     * @param name the action name
     * @return the action, or empty when none is declared
     */
    public Optional<SelectAction> select(String name) {
        return Optional.ofNullable(selects.get(name));
    }

    /**
     * Number of declared button actions.
     *
     * @return the button action count
     */
    public int buttonCount() {
        return buttons.size();
    }

    /**
     * Number of declared modal actions.
     *
     * @return the modal action count
     */
    public int modalCount() {
        return modals.size();
    }

    /**
     * How many select actions are declared.
     *
     * @return the count
     */
    public int selectCount() {
        return selects.size();
    }

    /** Collects actions and rejects ill-formed or duplicated ones eagerly. */
    public static final class Builder {

        private final Map<String, ButtonAction> buttons = new HashMap<>();
        private final Map<String, ModalAction> modals = new HashMap<>();
        private final Map<String, SelectAction> selects = new HashMap<>();

        /**
         * Declares a button action.
         *
         * @param name the action name encoded into the component id
         * @param ack how the router should acknowledge the interaction
         * @param handler the work to run
         * @return this builder
         * @throws IllegalArgumentException if the name is empty, contains a
         *     colon, is already declared as a button action, or the ack or handler
         *     is null
         */
        public Builder button(String name, Ack ack, ButtonHandler handler) {
            requireName(name, "button");
            Objects.requireNonNull(ack, "ack of button action '" + name + "'");
            Objects.requireNonNull(handler, "handler of button action '" + name + "'");
            if (buttons.putIfAbsent(name, new ButtonAction(ack, handler)) != null) {
                throw new IllegalArgumentException("Duplicate button action '" + name + "'");
            }
            return this;
        }

        /**
         * Declares a modal action.
         *
         * @param name the action name encoded into the modal id
         * @param ack how the router should acknowledge the submission; must not be
         *     {@link Ack#MODAL}, because a submission cannot open a modal
         * @param handler the work to run
         * @return this builder
         * @throws IllegalArgumentException if the name is empty, contains a
         *     colon, is already declared as a modal action, the ack is
         *     {@link Ack#MODAL}, or the ack or handler is null
         */
        public Builder modal(String name, Ack ack, ModalHandler handler) {
            requireName(name, "modal");
            Objects.requireNonNull(ack, "ack of modal action '" + name + "'");
            Objects.requireNonNull(handler, "handler of modal action '" + name + "'");
            if (ack == Ack.MODAL) {
                throw new IllegalArgumentException(
                        "Modal action '"
                                + name
                                + "' cannot use Ack.MODAL, a submission cannot open a modal");
            }
            if (modals.putIfAbsent(name, new ModalAction(ack, handler)) != null) {
                throw new IllegalArgumentException("Duplicate modal action '" + name + "'");
            }
            return this;
        }

        /**
         * Declares a string select action.
         *
         * <p>{@link Ack#MODAL} is allowed here, unlike for a modal action: a select can
         * open a follow-up modal, which is a normal flow such as choosing an item and
         * then confirming it with a reason.
         *
         * @param name the action name, unique among selects
         * @param ack how to acknowledge the interaction
         * @param handler the work
         * @return this builder
         * @throws IllegalArgumentException if the name is empty, contains a colon, or is
         *     already declared
         */
        public Builder select(String name, Ack ack, SelectHandler handler) {
            requireName(name, "select");
            Objects.requireNonNull(ack, "ack of select action '" + name + "'");
            Objects.requireNonNull(handler, "handler of select action '" + name + "'");
            if (selects.putIfAbsent(name, new SelectAction(ack, handler)) != null) {
                throw new IllegalArgumentException("Duplicate select action '" + name + "'");
            }
            return this;
        }

        private void requireName(String name, String kind) {
            Objects.requireNonNull(name, kind + " action name");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("A " + kind + " action name must not be empty");
            }
            if (name.indexOf(':') >= 0) {
                throw new IllegalArgumentException(
                        "The " + kind + " action name must not contain ':': " + name);
            }
        }

        /**
         * Builds the immutable table.
         *
         * @return the action table
         */
        public ActionTable build() {
            return new ActionTable(buttons, modals, selects);
        }
    }
}
