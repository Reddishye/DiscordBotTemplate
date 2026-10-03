package es.redactado.menu.core;

import es.redactado.menu.api.Menu;
import es.redactado.menu.preset.InMemoryPresetPreferences;
import es.redactado.menu.preset.PresetRegistry;

/**
 * Builds routers for tests, with fakes where a test does not care and real components
 * where it does.
 *
 * <p>Every router a test creates is owned by the builder, so {@link #close(MenuRouter)}
 * really does release the executor and the session store. That matters because the
 * router's ownership rule is part of what is being tested: a router given components it
 * did not create must not close them, and a test cannot check that if every router is
 * wired with shared statics.
 *
 * <p>Each call creates its own store, so tests stay independent of execution order.
 */
final class TestRouters {

    private TestRouters() {}

    /** A router over the real defaults. */
    static MenuRouter create() {
        return MenuRouter.builder().build();
    }

    /** A router that runs handlers on the supplied executor. */
    static MenuRouter withExecutor(MenuExecutor executor) {
        return MenuRouter.builder().executor(executor).build();
    }

    /** A router that keeps navigation history in the supplied store. */
    static MenuRouter withSessions(SessionStore sessions) {
        return MenuRouter.builder().sessions(sessions).build();
    }

    /**
     * A resolver over a fresh registry with no persisted preferences.
     *
     * <p>Tests that do not care about presets use this so they see the built-in default
     * rather than having to know what it is.
     */
    static PresetResolver resolver() {
        return new PresetResolver(new PresetRegistry(), new InMemoryPresetPreferences(), false);
    }

    /** A router that resolves presets through the supplied resolver. */
    static MenuRouter withPresets(PresetResolver presets) {
        return MenuRouter.builder().presets(presets).build();
    }

    /** A router over the real defaults with one menu registered. */
    static MenuRouter with(Menu menu) {
        MenuRouter router = create();
        router.register(menu.id(), menu);
        return router;
    }

    /** A router that runs handlers on a supplied executor, with one menu registered. */
    static MenuRouter with(MenuExecutor executor, Menu menu) {
        MenuRouter router = withExecutor(executor);
        router.register(menu.id(), menu);
        return router;
    }

    /**
     * Releases a router's own components.
     *
     * <p>Safe to call on a router that was given shared components, because the router
     * knows which of them it owns and closes only those.
     */
    static void close(MenuRouter router) {
        router.close();
    }
}
