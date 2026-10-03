package es.redactado.menu.core;

/** How a navigation action behaves. */
public enum NavigationMode {
    /** Push current context onto stack, navigate to new menu. */
    PUSH,
    /** Replace current context (no back history). */
    REPLACE,
    /** Navigate to menu; going back destroys the target. */
    SINGLE_USE,
    /** Pop back to the previous context. */
    POP
}
