package es.redactado.menu.simple;

import es.redactado.menu.api.MenuComponent;

/**
 * One thing a view is made of.
 *
 * <p>An element is a small immutable object built once while the menu is being declared and
 * re-evaluated on every render, because everything it needs is either part of it (a
 * {@link es.redactado.menu.api.Msg} to resolve, a function to apply to the model) or part of
 * the render itself (the context, the model).
 *
 * <p>Every element renders to exactly one component. A header with a subtitle is one
 * component that draws two lines, a pager is one component that draws a page of items, so
 * there is never a list to allocate here or a loop to walk.
 *
 * @param <M> the model type of the menu the view belongs to
 */
interface Element<M> {

    /**
     * Renders this element.
     *
     * @param scope the context and the model, whose data is null for a menu with no loader
     * @return the component to add to the container
     */
    MenuComponent render(Scope<M> scope);
}
