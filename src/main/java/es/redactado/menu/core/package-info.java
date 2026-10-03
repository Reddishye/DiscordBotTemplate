/**
 * Dispatch internals: the router, the component id codec, the navigation
 * implementation, the session store, and the executor that keeps handlers off the
 * JDA event thread.
 *
 * <p>Types here are wired by the template's dependency injection and are not meant
 * to be called directly from command code. This package depends on
 * {@code api} only; validation lives in {@code view} and is reached through the
 * view layer rather than from here.
 */
package es.redactado.menu.core;
