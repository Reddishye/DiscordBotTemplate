package es.redactado.menu.api;

/** How a navigation action moves between views. */
public enum NavigationMode {

    /**
     * Remember the current view on the session's stack, then show the target's
     * home view. This is the mode a normal "open" button uses.
     */
    PUSH,

    /** Show the target's home view without recording anything to go back to. */
    REPLACE,

    /**
     * Return to the previous view. With no previous view, shows the current
     * menu's home view.
     */
    BACK,

    /** Forget all history, then show the target's home view. */
    ROOT
}
