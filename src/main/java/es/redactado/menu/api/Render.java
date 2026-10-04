package es.redactado.menu.api;

import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;

/** Holds the future to return from a renderer path that is already complete. */
public final class Render {

    /** An already-completed future carrying a container. */
    public static CompletableFuture<Container> now(Container container) {
        return CompletableFuture.completedFuture(container);
    }

    private Render() {}
}
