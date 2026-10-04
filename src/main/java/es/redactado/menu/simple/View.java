package es.redactado.menu.simple;

import es.redactado.menu.api.Limits;
import java.util.List;

/**
 * One declared view: a name, the elements it shows, and the names of the actions it owns.
 *
 * <p>Built once and never changed, so a render can walk it without knowing it is walking
 * shared state. That is what makes one built menu safe to render from several threads at
 * once.
 *
 * @param <M> the model type of the menu the view belongs to
 */
final class View<M> {

    /** The prefix every menu component id starts with, as {@code ComponentId} encodes it. */
    private static final int PREFIX_LENGTH = 5;

    private final String name;
    private final Element<M>[] elements;
    private final String[] ownedActions;

    View(String name, List<Element<M>> elements, List<String> ownedActions) {
        this.name = name;
        this.elements = elements.toArray(new Element[0]);
        this.ownedActions = ownedActions.toArray(new String[0]);
    }

    /**
     * The view name, which is also its action: a navigation button pushes this name.
     *
     * @return the name
     */
    String name() {
        return name;
    }

    /**
     * The elements, in declaration order.
     *
     * @return the elements; the array is shared and must not be modified
     */
    Element<M>[] elements() {
        return elements;
    }

    /**
     * The action names this view owns, which is what turns a click into a redraw of the right
     * view.
     *
     * @return the owned names; the array is shared and must not be modified
     */
    String[] ownedActions() {
        return ownedActions;
    }

    /**
     * Checks this view's name against the limit on a component id.
     *
     * <p>Only the name is measured, because that is all this view contributes to the ids of
     * the buttons that open it. Its own elements' ids are counted where they are declared.
     *
     * @param idLength the length of the menu id this view belongs to
     * @throws IllegalArgumentException if the id could not be encoded
     */
    void checkNameLength(int idLength) {
        int used = PREFIX_LENGTH + idLength + 1 + name.length();
        if (used > Limits.MAX_CUSTOM_ID_LENGTH) {
            throw new IllegalArgumentException(
                    ("View '%s' cannot fit in a component id: menu id plus view name use %d"
                                    + " of %d characters")
                            .formatted(name, used, Limits.MAX_CUSTOM_ID_LENGTH));
        }
    }
}
