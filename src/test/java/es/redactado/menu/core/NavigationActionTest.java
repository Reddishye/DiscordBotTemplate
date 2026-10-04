package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Limits;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The id format for navigation that targets a view, and the round trip back.
 *
 * <p>The id is the whole contract with the router, so each case is decoded the way the
 * router decodes it rather than compared as a string.
 */
class NavigationActionTest {

    @Nested
    @DisplayName("encoding")
    class Encoding {

        @Test
        @DisplayName("a menu target keeps the short form, with no view segment")
        void menuTargetIsShort() {
            assertThat(NavigationAction.buttonId("m", NavigationMode.PUSH, "other"))
                    .isEqualTo("menu:m:nav:push:other");
        }

        @Test
        @DisplayName("a view target adds the action after the menu")
        void viewTargetCarriesTheAction() {
            String id =
                    NavigationAction.buttonId(
                            "m", NavigationMode.PUSH, new NavEntry("other", "detail", List.of()));

            assertThat(id).isEqualTo("menu:m:nav:push:other:detail");
        }

        @Test
        @DisplayName("a view target carries its params after the action")
        void viewTargetCarriesParams() {
            String id =
                    NavigationAction.buttonId(
                            "m",
                            NavigationMode.REPLACE,
                            new NavEntry("other", "detail", List.of("42", "x")));

            assertThat(id).isEqualTo("menu:m:nav:replace:other:detail:42:x");
        }

        @Test
        @DisplayName("back carries no target at all")
        void backHasNoTarget() {
            assertThat(NavigationAction.buttonId("m", NavigationMode.BACK, "other"))
                    .isEqualTo("menu:m:nav:back");
            assertThat(NavigationAction.buttonId("m", NavigationMode.BACK, "ignored"))
                    .as("back ignores the target entirely, so both overloads agree")
                    .isEqualTo("menu:m:nav:back");
        }

        @Test
        @DisplayName("a target that would overflow the id limit is refused, not truncated")
        void overlongTargetIsRefused() {
            NavEntry target =
                    new NavEntry(
                            "other", "detail", List.of("p".repeat(Limits.MAX_CUSTOM_ID_LENGTH)));

            assertThatThrownBy(() -> NavigationAction.buttonId("m", NavigationMode.PUSH, target))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(Limits.MAX_CUSTOM_ID_LENGTH));
        }

        @Test
        @DisplayName("a back navigation refuses a target it cannot use")
        void backRefusesATarget() {
            assertThatThrownBy(
                            () ->
                                    new NavigationAction(
                                            NavigationMode.BACK,
                                            new NavEntry("m", "home", List.of())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no target");
        }

        @Test
        @DisplayName("a push or replace without a target is refused at construction")
        void pushNeedsATarget() {
            assertThatThrownBy(() -> new NavigationAction(NavigationMode.PUSH, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a target");
        }
    }

    @Nested
    @DisplayName("decoding")
    class Decoding {

        @Test
        @DisplayName("a menu target decodes to that menu's home view")
        void menuTargetMeansHome() {
            NavigationAction parsed = parse("menu:m:nav:push:other");

            assertThat(parsed.mode()).isEqualTo(NavigationMode.PUSH);
            assertThat(parsed.target())
                    .as("an omitted view segment means home, which is what the short form means")
                    .isEqualTo(new NavEntry("other", "home", List.of()));
        }

        @Test
        @DisplayName("a view target round trips with its action")
        void viewTargetRoundTrips() {
            NavEntry target = new NavEntry("other", "detail", List.of());

            assertThat(parse(id(target)).target()).isEqualTo(target);
        }

        @Test
        @DisplayName("a view target round trips with its params")
        void viewTargetRoundTripsWithParams() {
            NavEntry target = new NavEntry("other", "detail", List.of("42", "x"));

            assertThat(parse(id(target)).target()).isEqualTo(target);
        }

        @Test
        @DisplayName("every mode survives the round trip")
        void everyModeRoundTrips() {
            NavEntry target = new NavEntry("other", "detail", List.of("42"));

            for (NavigationMode mode :
                    List.of(NavigationMode.PUSH, NavigationMode.REPLACE, NavigationMode.ROOT)) {
                NavigationAction parsed = parse(id(mode, target));

                assertThat(parsed.mode()).isEqualTo(mode);
                assertThat(parsed.target()).isEqualTo(target);
            }
        }

        @Test
        @DisplayName("back decodes with no target")
        void backHasNoTargetWhenDecoded() {
            NavigationAction parsed = parse("menu:m:nav:back");

            assertThat(parsed.mode()).isEqualTo(NavigationMode.BACK);
            assertThat(parsed.target()).isNull();
        }

        @Test
        @DisplayName("a target with no menu is reported as an unknown menu")
        void missingMenuIsUnknownMenu() {
            MenuContext ctx = context("menu:m:nav:push");

            assertThatThrownBy(() -> NavigationAction.fromContext(ctx))
                    .isInstanceOf(es.redactado.menu.api.UserFacingException.class)
                    .hasMessageContaining(MessageKeys.ERROR_UNKNOWN_MENU);
        }

        @Test
        @DisplayName("an unknown mode is reported as an unknown mode")
        void unknownModeIsReported() {
            MenuContext ctx = context("menu:m:nav:teleport:other");

            assertThatThrownBy(() -> NavigationAction.fromContext(ctx))
                    .isInstanceOf(es.redactado.menu.api.UserFacingException.class)
                    .hasMessageContaining(MessageKeys.ERROR_UNKNOWN_NAV_MODE);
        }

        private MenuContext context(String componentId) {
            MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
            ComponentId parsed = ComponentId.decode(componentId).orElseThrow();
            List<String> params = parsed.params();
            when(ctx.params()).thenReturn(params);
            when(ctx.requireString(0)).thenReturn(params.get(0));
            for (int i = 1; i < params.size(); i++) {
                when(ctx.param(i)).thenReturn(java.util.Optional.of(params.get(i)));
            }
            return ctx;
        }

        private String id(NavEntry target) {
            return NavigationAction.buttonId("m", NavigationMode.PUSH, target);
        }

        private String id(NavigationMode mode, NavEntry target) {
            return NavigationAction.buttonId("m", mode, target);
        }

        private NavigationAction parse(String componentId) {
            return NavigationAction.fromContext(context(componentId));
        }
    }

    @Nested
    @DisplayName("a menu that switches on the action")
    class SwitchingMenus {

        @Test
        @DisplayName("an action it does not know produces the localized message")
        void unknownViewIsUserFacing() {
            TestingMenu menu = new TestingMenu();
            MenuContext ctx = contextFor(menu, "nope");

            assertThatThrownBy(() -> menu.render(ctx).join())
                    .hasRootCauseInstanceOf(es.redactado.menu.api.UserFacingException.class)
                    .hasRootCauseMessage(MessageKeys.ERROR_UNKNOWN_VIEW);
        }

        @Test
        @DisplayName("the known views still render")
        void knownViewsRender() {
            TestingMenu menu = new TestingMenu();

            assertThat(menu.render(contextFor(menu, "home")).join()).isNotNull();
            assertThat(menu.render(contextFor(menu, "detail")).join()).isNotNull();
        }

        @Test
        @DisplayName("it reports which view is on screen, which is what going back needs")
        void itRemembersItsView() {
            TestingMenu menu = new TestingMenu();

            assertThat(menu.currentView(contextFor(menu, "detail")).action()).isEqualTo("detail");
        }
    }

    /**
     * A menu whose render switches on the action, as the framework expects.
     *
     * <p>Extends {@link AbstractMenu} so it uses the framework's own nav action and its
     * {@code unknownView} helper rather than a private copy of either.
     */
    private static final class TestingMenu extends AbstractMenu {

        TestingMenu() {
            super("m");
        }

        @Override
        protected void declare(ActionTable.Builder table) {}

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return switch (ctx.action()) {
                case "home", "detail" ->
                        CompletableFuture.completedFuture(
                                Container.of(TextDisplay.of(ctx.action())));
                default -> unknownView(ctx);
            };
        }
    }

    private static MenuContext contextFor(Menu menu, String action) {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.menuId()).thenReturn(menu.id());
        when(ctx.action()).thenReturn(action);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        when(ctx.withPreset(any())).thenReturn(ctx);
        return ctx;
    }
}
