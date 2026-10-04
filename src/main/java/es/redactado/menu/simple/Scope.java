package es.redactado.menu.simple;

import es.redactado.menu.api.MenuContext;

/**
 * What a view function is handed when a simple menu renders: the interaction, and the model
 * the loader produced for it.
 *
 * <p>One record, because every element of a view that depends on data takes the same pair
 * and there is nothing else it could need.
 *
 * @param <M> the model type
 * @param ctx the context of the current interaction
 * @param data what the loader returned, or null when the menu declared no loader
 */
public record Scope<M>(MenuContext ctx, M data) {}
