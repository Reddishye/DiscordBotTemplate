/**
 * Dispatch internals: the router, the component id codec, the navigation
 * implementation, the session store, and the executor that keeps handlers off the
 * JDA event thread.
 *
 * <p>{@link es.redactado.menu.core.ViewEditor} is the only place a container
 * reaches Discord: it validates and sends, so no other code path can skip the
 * check. {@link es.redactado.menu.core.ViewEditorIsTheOnlyEditPathTest}'s rule is
 * enforced by a source scan.
 *
 * <p>Types here are wired by the template's dependency injection and are not meant
 * to be called directly from command code. This package depends on
 * {@code api} only.
 */
package es.redactado.menu.core;
