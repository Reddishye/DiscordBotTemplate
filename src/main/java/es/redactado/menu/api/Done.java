package es.redactado.menu.api;

import java.util.concurrent.CompletableFuture;

/** Holds the future to return from a handler that has no asynchronous work. */
public final class Done {

    /** An already-completed future, for handlers that finish synchronously. */
    public static final CompletableFuture<Void> NOW = CompletableFuture.completedFuture(null);

    private Done() {}
}
