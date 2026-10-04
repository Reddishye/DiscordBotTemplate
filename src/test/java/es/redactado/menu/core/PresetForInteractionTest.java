package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.InMemoryPresetPreferences;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetRegistry;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the preset the router resolves and hands to a handler.
 *
 * <p>The unit tests for {@link PresetResolver} prove the precedence rules in isolation.
 * What matters here is the wiring: that the router calls it before the handler, that the
 * context the handler receives carries the result, and that navigation re-resolves for
 * the menu it is moving to rather than reusing the one it came from.
 */
class PresetForInteractionTest {

    private static final long GUILD = 555L;
    private static final long USER = 42L;
    private static final int AWAIT_MS = 10;

    private final PresetRegistry registry = new PresetRegistry();
    private final InMemoryPresetPreferences preferences = new InMemoryPresetPreferences();

    @Test
    @DisplayName("the context handed to a handler carries the resolved preset")
    void handlerSeesResolvedPreset() throws InterruptedException {
        AtomicReference<MenuContext> seen = new AtomicReference<>();
        CountDownLatch handled = new CountDownLatch(1);

        try (MenuRouter router = router(recordingMenu(null, seen, handled))) {
            assertThat(router.dispatchButton(click("menu:m:go"))).isTrue();

            assertThat(handled.await(AWAIT_MS, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get().preset()).isEqualTo(BuiltinPresets.DEFAULT);
        }
    }

    @Test
    @DisplayName("a menu that forces a preset wins over guild and user preferences")
    void menuPresetNameWins() throws InterruptedException {
        preferences.setGuild(GUILD, "minimal");
        preferences.setUser(USER, "midnight");
        AtomicReference<MenuContext> seen = new AtomicReference<>();
        CountDownLatch handled = new CountDownLatch(1);

        try (MenuRouter router = router(recordingMenu("vibrant", seen, handled))) {
            assertThat(router.dispatchButton(click("menu:m:go"))).isTrue();

            assertThat(handled.await(AWAIT_MS, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get().preset().name()).isEqualTo("vibrant");
        }
    }

    @Test
    @DisplayName("a guild preference is honoured when the menu does not force one")
    void guildPreferenceApplies() throws InterruptedException {
        preferences.setGuild(GUILD, "minimal");
        AtomicReference<MenuContext> seen = new AtomicReference<>();
        CountDownLatch handled = new CountDownLatch(1);

        try (MenuRouter router = router(recordingMenu(null, seen, handled))) {
            assertThat(router.dispatchButton(click("menu:m:go"))).isTrue();

            assertThat(handled.await(AWAIT_MS, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get().preset().name()).isEqualTo("minimal");
        }
    }

    @Test
    @DisplayName("a direct message resolves without touching the guild level")
    void directMessageSkipsGuild() throws InterruptedException {
        preferences.setGuild(GUILD, "minimal");
        preferences.setUser(USER, "midnight");
        AtomicReference<MenuContext> seen = new AtomicReference<>();
        CountDownLatch handled = new CountDownLatch(1);

        // User preferences enabled so the user level can answer once the guild is skipped.
        // A DM reporting guild 0 is the entire point of the test.
        try (MenuRouter router =
                MenuRouter.builder()
                        .presets(new PresetResolver(registry, preferences, true))
                        .build()) {
            router.register("m", recordingMenu(null, seen, handled));

            ButtonInteractionEvent event = click("menu:m:go");
            when(event.getGuild()).thenReturn(null);

            assertThat(router.dispatchButton(event)).isTrue();
            assertThat(handled.await(AWAIT_MS, TimeUnit.SECONDS)).isTrue();
            assertThat(seen.get().preset().name()).isEqualTo("midnight");
        }
    }

    @Test
    @DisplayName("withPreset changes the preset and nothing else")
    void withPresetSharesEverythingElse() throws InterruptedException {
        AtomicReference<MenuContext> seen = new AtomicReference<>();
        CountDownLatch handled = new CountDownLatch(1);

        try (MenuRouter router = router(recordingMenu(null, seen, handled))) {
            assertThat(router.dispatchButton(click("menu:m:go"))).isTrue();
            assertThat(handled.await(AWAIT_MS, TimeUnit.SECONDS)).isTrue();
            MenuContext original = seen.get();

            MenuContext swapped = original.withPreset(BuiltinPresets.MIDNIGHT);

            assertThat(swapped.preset()).isEqualTo(BuiltinPresets.MIDNIGHT);
            assertThat(original.preset())
                    .as("the original is untouched")
                    .isEqualTo(BuiltinPresets.DEFAULT);
            assertThat(swapped.menuId()).isEqualTo(original.menuId());
            assertThat(swapped.action()).isEqualTo(original.action());
            assertThat(swapped.params()).isEqualTo(original.params());
            assertThat(swapped.userId()).isEqualTo(original.userId());
            assertThat(swapped.guildId()).isEqualTo(original.guildId());
            assertThat(swapped.locale()).isEqualTo(original.locale());
            assertThat(swapped.event()).isSameAs(original.event());
            assertThat(swapped.session()).isSameAs(original.session());
        }
    }

    @Test
    @DisplayName("navigating to a menu renders it with that menu's own preset, and back again")
    void navigationResolvesTheTargetMenu() throws InterruptedException {
        // Going back re-renders the source menu, not the target, so every render is
        // recorded rather than only the last one per menu.
        Renders source = new Renders();
        Renders target = new Renders();
        CountDownLatch back = new CountDownLatch(1);
        CountDownLatch forward = new CountDownLatch(1);

        try (MenuRouter router =
                MenuRouter.builder()
                        .presets(new PresetResolver(registry, preferences, false))
                        .build()) {
            router.register("a", navigatingMenu(renderingMenu("a", "vibrant", source, forward)));
            router.register("b", navigatingMenu(renderingMenu("b", "monochrome", target, back)));

            assertThat(router.dispatchButton(click("menu:a:nav:push:b", 7L))).isTrue();
            assertThat(back.await(AWAIT_MS, TimeUnit.SECONDS))
                    .as("pushing to b renders b")
                    .isTrue();
            // The message stays claimed until the first interaction finishes its edit.
            // Clicking sooner is refused by the re-entrancy guard, which is correct
            // behaviour and would make this test flaky rather than informative.
            awaitIdle(router);

            assertThat(router.dispatchButton(click("menu:b:nav:back", 7L))).isTrue();
            assertThat(forward.await(AWAIT_MS, TimeUnit.SECONDS))
                    .as("going back renders a again")
                    .isTrue();
        }

        assertThat(target.names())
                .as("the target declares its own preset, so it wins over the source's")
                .containsExactly("monochrome");
        assertThat(source.names())
                .as("going back renders the source with the source's own preset, not b's")
                .containsExactly("vibrant");
    }

    /** Every preset a menu was rendered with, in order. */
    private static final class Renders {
        private final java.util.List<String> names =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        void record(Preset preset) {
            names.add(preset.name());
        }

        java.util.List<String> names() {
            return java.util.List.copyOf(names);
        }
    }

    /** Waits for the router to finish every interaction it has claimed. */
    private static void awaitIdle(MenuRouter router) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_MS);
        while (router.inFlight() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(router.inFlight()).as("no interaction is still claimed").isZero();
    }

    private MenuRouter router(Menu menu) {
        MenuRouter router =
                MenuRouter.builder()
                        .presets(new PresetResolver(registry, preferences, false))
                        .build();
        router.register(menu.id(), menu);
        return router;
    }

    private static ButtonInteractionEvent click(String componentId) {
        return click(componentId, 7L);
    }

    private static ButtonInteractionEvent click(String componentId, long messageId) {
        ButtonInteractionEvent event =
                JdaMocks.button(componentId, true, messageId, JdaMocks.NO_OWNER);
        when(event.getUser().getIdLong()).thenReturn(USER);
        when(event.getGuild().getIdLong()).thenReturn(GUILD);
        when(event.getUserLocale()).thenReturn(DiscordLocale.UNKNOWN);
        when(event.getGuildLocale()).thenReturn(DiscordLocale.UNKNOWN);
        return event;
    }

    /** Records the context a handler was given, then completes. */
    private static Menu recordingMenu(
            String presetName, AtomicReference<MenuContext> seen, CountDownLatch handled) {
        return new Menu() {
            @Override
            public String id() {
                return "m";
            }

            @Override
            public Optional<String> presetName() {
                return Optional.ofNullable(presetName);
            }

            @Override
            public CompletableFuture<Container> render(MenuContext ctx) {
                throw new UnsupportedOperationException("render is not used by this test");
            }

            @Override
            public void actions(ActionTable.Builder table) {
                table.button(
                        "go",
                        Ack.DEFER_EDIT,
                        (ctx, event) -> {
                            seen.set(ctx);
                            handled.countDown();
                            return CompletableFuture.completedFuture(null);
                        });
            }
        };
    }

    /** Records the preset its render was given. */
    private static Menu renderingMenu(
            String id, String presetName, Renders render, CountDownLatch done) {
        return new Menu() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Optional<String> presetName() {
                return Optional.of(presetName);
            }

            @Override
            public CompletableFuture<Container> render(MenuContext ctx) {
                render.record(ctx.preset());
                done.countDown();
                // A real container, not null: a navigation records the history only after
                // the edit succeeds, and an edit of nothing fails.
                return CompletableFuture.completedFuture(
                        Container.of(TextDisplay.of(ctx.preset().name())));
            }

            @Override
            public void actions(ActionTable.Builder table) {}
        };
    }

    /** Adds the nav action the router needs, delegating everything else. */
    private static Menu navigatingMenu(Menu delegate) {
        return new Menu() {
            @Override
            public String id() {
                return delegate.id();
            }

            @Override
            public Optional<String> presetName() {
                return delegate.presetName();
            }

            @Override
            public CompletableFuture<Container> render(MenuContext ctx) {
                return delegate.render(ctx);
            }

            @Override
            public void actions(ActionTable.Builder table) {
                table.button(
                        "nav",
                        Ack.DEFER_EDIT,
                        (ctx, event) -> {
                            NavigationMode mode =
                                    NavigationMode.valueOf(
                                            ctx.requireString(0).toUpperCase(Locale.ROOT));
                            return ctx.navigate(mode, ctx.param(1).orElse(""));
                        });
            }
        };
    }
}
