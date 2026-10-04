package es.redactado.menu.simple;

import es.redactado.menu.view.Pager;
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

    private final String menuId;
    private final Set<String> listIds = new java.util.HashSet<>();

    Declarations(String menuId) {
        this.menuId = menuId;
    }

    /**
     * Records a list id, refusing one that the pager would refuse later or that another list
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
}
