package es.redactado.menu.simple;

import java.util.List;

/**
 * A choice made in a select menu.
 *
 * <p>The values are the ones the options were declared with, in the order the user picked
 * them, which is what a handler usually wants to act on.
 */
public final class Pick extends Trigger {

    private final List<String> values;

    Pick(Support support, List<String> values) {
        super(support);
        this.values = List.copyOf(values);
    }

    /**
     * The chosen values.
     *
     * @return the values, possibly empty, never null
     */
    public List<String> values() {
        return values;
    }
}
