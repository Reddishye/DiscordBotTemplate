package es.redactado.menu.api;

import net.dv8tion.jda.api.components.container.Container;

/**
 * Turns a loaded model into a container.
 *
 * <p>A renderer is a pure function of its context and model. It must not perform
 * I/O, block, or mutate state shared with anything else, because it runs on the
 * menu executor and may run more than once for the same model. Anything slow
 * belongs in a {@link Loader}.
 *
 * @param <M> the model type produced by a loader
 */
@FunctionalInterface
public interface Renderer<M> {

    /**
     * Builds the container for a model.
     *
     * @param ctx the context of the current interaction
     * @param model the loaded data
     * @return the rendered container
     */
    Container render(MenuContext ctx, M model);
}
