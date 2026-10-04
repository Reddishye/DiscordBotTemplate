package es.redactado.menu.simple;

import java.util.Map;

/**
 * A submitted modal form.
 *
 * <p>Modal submissions are menu-wide rather than per view, because a form is opened by a
 * button on one view and submitted while the message may well be showing another.
 *
 * <p>The values are keyed by field id and trimmed, so a handler reads an answer without
 * knowing which client typed it or whether the user left a space at the end.
 */
public final class Submit extends Trigger {

    private final Map<String, String> values;

    Submit(Support support, Map<String, String> values) {
        super(support);
        this.values = Map.copyOf(values);
    }

    /**
     * The submitted answers.
     *
     * <p>A field the user left empty maps to an empty string rather than being absent. A
     * field Discord did not send is absent, which is the honest answer: nothing can invent
     * a value for it.
     *
     * @return the answers, never null
     */
    public Map<String, String> values() {
        return values;
    }
}
