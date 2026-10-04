package es.redactado.menu.simple;

import es.redactado.menu.api.Ack;

/**
 * One action a simple menu handles: a name, how the interaction is acknowledged, and the
 * handler that runs.
 *
 * <p>Built once while the menu is declared, held in an array, and read from the menu's
 * {@code declare} call at registration. A render never looks at an action; it only draws the
 * button, and the button's name is how the router finds this again.
 *
 * @param name the action name, which is a segment of every component id using it
 * @param ack how the router should acknowledge the interaction
 * @param kind which handler interface {@code handler} satisfies
 * @param handler the handler, a {@link ClickHandler}, {@link PickHandler} or
 *     {@link SubmitHandler} according to {@code kind}
 * @param opensModal whether the button opens a modal, which decides whether
 *     {@link Click#modal} may be called
 */
record Action<M>(String name, Ack ack, Kind kind, Object handler, boolean opensModal, String view) {

    /** Which of the three handler interfaces an action holds. */
    enum Kind {
        BUTTON,
        SELECT,
        MODAL
    }
}
