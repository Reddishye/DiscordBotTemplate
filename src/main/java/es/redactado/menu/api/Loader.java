package es.redactado.menu.api;

import java.util.concurrent.CompletableFuture;

/**
 * Fetches the data a view needs.
 *
 * <p>Separate from {@link Renderer} so that loading, which may touch a database or
 * a remote service, is visibly distinct from rendering, which must not.
 *
 * <p>An implementation may block internally, but it must never block a JDA event
 * thread; see {@link es.redactado.menu.core.MenuExecutor} for the sanctioned way
 * to call a blocking service.
 *
 * @param <M> the model type handed to the renderer
 */
@FunctionalInterface
public interface Loader<M> {

    /**
     * Loads the data for a view.
     *
     * @param ctx the context of the current interaction
     * @return a future for the model; never {@code null}
     */
    CompletableFuture<M> load(MenuContext ctx);
}
