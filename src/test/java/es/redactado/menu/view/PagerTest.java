package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.core.AbstractMenu;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.core.PageAction;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.IconKey;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers how a pager renders and what its buttons encode.
 *
 * <p>Two behaviours matter more than the rest and get the most attention here: the stored
 * page is clamped rather than trusted, and the button id carries enough to re-render the
 * view the user was looking at. Everything else follows from those.
 */
class PagerTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("the id must be a short lowercase token")
        void idIsRestricted() {
            for (String bad : List.of("", "UPPER", "has space", "has:colon", "a".repeat(21))) {
                assertThatThrownBy(() -> Pager.of(bad, List.of("x"), 5, item -> Text.of("x")))
                        .as("id '%s'", bad)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Pager id must match");
            }
        }

        @Test
        @DisplayName("the page size must be between 1 and 20")
        void pageSizeIsRestricted() {
            assertThatThrownBy(() -> Pager.of("p", List.of("x"), 0, item -> Text.of("x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 20");
            assertThatThrownBy(() -> Pager.of("p", List.of("x"), 21, item -> Text.of("x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 1 and 20");
            assertThat(Pager.of("p", List.of("x"), 1, item -> Text.of("x"))).isNotNull();
            assertThat(Pager.of("p", List.of("x"), 20, item -> Text.of("x"))).isNotNull();
        }

        @Test
        @DisplayName("a renderer is required")
        void rendererIsRequired() {
            assertThatThrownBy(() -> Pager.of("p", List.of("x"), 5, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("needs a renderer");
        }
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        @DisplayName("no items renders the empty text and no controls")
        void emptyRendersTextOnly() {
            List<ContainerChildComponent> children =
                    Pager.of("p", List.of(), 5, item -> Text.of("x")).render(context());

            assertThat(children).hasSize(1);
            assertThat(firstText(children)).isEqualTo("*No items.*");
            assertThat(buttons(children)).isEmpty();
        }

        @Test
        @DisplayName("the empty text is localized")
        void emptyTextIsLocalized() {
            MenuContext spanish =
                    ViewContexts.forPreset(
                            BuiltinPresets.DEFAULT, java.util.Locale.forLanguageTag("es-ES"));

            List<ContainerChildComponent> children =
                    Pager.of("p", List.of(), 5, item -> Text.of("x")).render(spanish);

            assertThat(firstText(children)).isEqualTo("*No hay elementos.*");
        }

        @Test
        @DisplayName("a custom empty text key is used")
        void customEmptyText() {
            List<ContainerChildComponent> children =
                    Pager.of("p", List.of(), 5, item -> Text.of("x"))
                            .emptyText(MessageKeys.FIELD_NOT_SET)
                            .render(context());

            assertThat(firstText(children)).isEqualTo("*Not set*");
        }

        @Test
        @DisplayName("one item renders that item and no controls")
        void singleItemHasNoControls() {
            List<ContainerChildComponent> children = render(items(1), 5, 0);

            assertThat(firstText(children)).isEqualTo("item 0");
            assertThat(buttons(children)).as("one page needs no arrows").isEmpty();
        }

        @Test
        @DisplayName("exactly one page of items has no controls")
        void exactlyOnePageHasNoControls() {
            List<ContainerChildComponent> children = render(items(5), 5, 0);

            assertThat(texts(children)).as("five items on one page").hasSize(5);
            assertThat(buttons(children)).isEmpty();
        }

        @Test
        @DisplayName("one more than a page shows the first page plus controls")
        void oneOverPageSize() {
            List<ContainerChildComponent> children = render(items(6), 5, 0);

            assertThat(texts(children).getFirst()).isEqualTo("item 0");
            assertThat(texts(children).get(4)).isEqualTo("item 4");
            assertThat(buttons(children)).hasSize(3);
            assertThat(indicator(children)).isEqualTo("1/2");
        }

        @Test
        @DisplayName("many pages show the requested slice")
        void manyPages() {
            List<ContainerChildComponent> second = render(items(25), 5, 1);

            assertThat(texts(second).getFirst()).isEqualTo("item 5");
            assertThat(indicator(second)).isEqualTo("2/5");
        }

        @Test
        @DisplayName("a stored page beyond the end is clamped to the last")
        void clampsHighPage() {
            List<ContainerChildComponent> children = render(items(6), 5, 99);

            assertThat(texts(children).getFirst()).isEqualTo("item 5");
            assertThat(indicator(children)).isEqualTo("2/2");
        }

        @Test
        @DisplayName("a negative stored page is clamped to the first")
        void clampsNegativePage() {
            assertThat(firstText(render(items(6), 5, -4))).isEqualTo("item 0");
        }

        @Test
        @DisplayName("previous is disabled on the first page and next on the last")
        void arrowsDisabledAtTheEnds() {
            List<ContainerChildComponent> first = render(items(25), 5, 0);
            List<Button> firstButtons = buttons(first);
            assertThat(firstButtons.get(0).isDisabled()).as("previous on page 1").isTrue();
            assertThat(firstButtons.get(2).isDisabled()).as("next on page 1").isFalse();

            List<ContainerChildComponent> last = render(items(25), 5, 4);
            List<Button> lastButtons = buttons(last);
            assertThat(lastButtons.get(0).isDisabled()).as("previous on the last page").isFalse();
            assertThat(lastButtons.get(2).isDisabled()).as("next on the last page").isTrue();
        }

        @Test
        @DisplayName("the arrows follow the preset's icons and style")
        void arrowsFollowThePreset() {
            for (var preset : BuiltinPresets.all()) {
                List<Button> found = buttons(render(items(6), 5, 1, preset));

                Button previous = found.get(0);
                Button next = found.get(2);
                if (preset.icons().formatted(IconKey.PREVIOUS).isEmpty()) {
                    assertThat(previous.getEmoji()).as("%s previous", preset.name()).isNull();
                } else {
                    assertThat(previous.getEmoji().getFormatted())
                            .isEqualTo(preset.icons().formatted(IconKey.PREVIOUS));
                }
                if (preset.icons().formatted(IconKey.NEXT).isEmpty()) {
                    assertThat(next.getEmoji()).as("%s next", preset.name()).isNull();
                } else {
                    assertThat(next.getEmoji().getFormatted())
                            .isEqualTo(preset.icons().formatted(IconKey.NEXT));
                }
                assertThat(previous.getStyle())
                        .isEqualTo(
                                preset.buttons().of(es.redactado.menu.preset.ButtonRole.SECONDARY));
            }
        }

        @Test
        @DisplayName("separated inserts a divider between items but not before the first")
        void separatedAddsDividers() {
            List<ContainerChildComponent> children =
                    Pager.of("p", items(3), 5, item -> Text.of("x"))
                            .separated(true)
                            .render(context());

            long dividers =
                    children.stream()
                            .filter(c -> c.getClass().getSimpleName().contains("Separator"))
                            .count();
            assertThat(dividers).as("three items means two dividers").isEqualTo(2);
        }

        @Test
        @DisplayName("the indicator is disabled and carries no icon")
        void indicatorIsInert() {
            Button indicator = buttons(render(items(6), 5, 1)).get(1);

            assertThat(indicator.isDisabled()).isTrue();
            assertThat(indicator.getEmoji()).isNull();
        }

        @Test
        @DisplayName("two pagers in one view keep their own pages")
        void twoPagersAreIndependent() {
            MenuContext ctx = context();
            Session session = ctx.session();

            Pager<String> first = Pager.of("first", items(10), 5, Text::of);
            Pager<String> second = Pager.of("second", items(10), 5, Text::of);
            session.putState(Pager.stateKey("first"), 1);

            assertThat(firstText(first.render(ctx))).isEqualTo("item 5");
            assertThat(firstText(second.render(ctx))).isEqualTo("item 0");
        }

        @Test
        @DisplayName("a missing session means the first page")
        void missingSessionIsFirstPage() {
            assertThat(firstText(render(items(25), 5, 0))).isEqualTo("item 0");
        }
    }

    @Nested
    @DisplayName("button ids")
    class ButtonIds {

        @Test
        @DisplayName("ids round trip through the decoder")
        void idsRoundTrip() {
            MenuContext ctx = context();
            List<Button> found = buttons(render(items(6), 5, 0, BuiltinPresets.DEFAULT, ctx));

            ComponentId previous = ComponentId.decode(found.get(0).getCustomId()).orElseThrow();
            ComponentId next = ComponentId.decode(found.get(2).getCustomId()).orElseThrow();

            assertThat(previous.menuId()).isEqualTo("m");
            assertThat(previous.action()).isEqualTo("page");
            assertThat(previous.params()).containsExactly("prev", "p", "home");
            assertThat(next.params()).containsExactly("next", "p", "home");
        }

        @Test
        @DisplayName("the view's own action and params travel in the id")
        void viewContextTravels() {
            MenuContext ctx = contextFor("list", "view", List.of("alpha", "beta"));
            List<Button> found = buttons(render(items(6), 5, 0, BuiltinPresets.DEFAULT, ctx));

            ComponentId next = ComponentId.decode(found.get(2).getCustomId()).orElseThrow();

            assertThat(next.params()).containsExactly("next", "p", "view", "alpha", "beta");
        }

        @Test
        @DisplayName("an over-long id fails loudly rather than silently truncating")
        void overLongIdFailsLoudly() {
            MenuContext ctx = contextFor("menu", "view", List.of("x".repeat(80)));

            assertThatThrownBy(() -> render(items(6), 5, 0, BuiltinPresets.DEFAULT, ctx))
                    .as("a truncated id would page the wrong view with no error")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("the limit is");
        }
    }

    @Nested
    @DisplayName("reserved names")
    class ReservedNames {

        /**
         * A bare table may declare {@code nav} or {@code page} once: there is nothing to
         * collide with. The protection is that {@code AbstractMenu} registers both before
         * a subclass declares anything, so a subclass's own declaration is a duplicate.
         */
        @Test
        @DisplayName("a bare table accepts either name once")
        void bareTableAcceptsThem() {
            assertThat(
                            ActionTable.builder()
                                    .button("page", Ack.DEFER_EDIT, (ctx, e) -> null)
                                    .build())
                    .isNotNull();
        }

        @Test
        @DisplayName("a subclass cannot redeclare page")
        void pageIsReserved() {
            assertThatThrownBy(() -> redeclaring("page").actions(tableFor("page")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate button action 'page'");
        }

        @Test
        @DisplayName("a subclass cannot redeclare nav")
        void navIsReserved() {
            assertThatThrownBy(() -> redeclaring("nav").actions(tableFor("nav")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate button action 'nav'");
        }

        @Test
        @DisplayName("a subclass that declares neither is fine, and both built-ins are present")
        void builtInsAreRegistered() {
            AbstractMenu menu = redeclaring("other");
            ActionTable.Builder builder = ActionTable.builder();
            menu.actions(builder);
            ActionTable table = builder.build();

            assertThat(table.button("nav")).isPresent();
            assertThat(table.button("page")).isPresent();
            assertThat(table.button("other")).isPresent();
        }

        private ActionTable.Builder tableFor(String name) {
            return ActionTable.builder().button(name, Ack.DEFER_EDIT, (ctx, e) -> null);
        }
    }

    /** A menu that declares one action of its own, which is how a subclass misbehaves. */
    private static AbstractMenu redeclaring(String name) {
        return new AbstractMenu("m") {
            @Override
            public CompletableFuture<net.dv8tion.jda.api.components.container.Container> render(
                    MenuContext ctx) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }

            @Override
            protected void declare(ActionTable.Builder table) {
                table.button(
                        name, Ack.DEFER_EDIT, (ctx, e) -> CompletableFuture.completedFuture(null));
            }
        };
    }

    @Test
    @DisplayName("the page action decodes a direction, an id and a view")
    void pageActionDecodes() {
        MenuContext ctx = mockContext("list", "page", List.of("next", "results", "view", "a", "b"));

        PageAction page = PageAction.fromContext(ctx);

        assertThat(page.direction()).isEqualTo(PageAction.Direction.NEXT);
        assertThat(page.direction().delta()).isEqualTo(1);
        assertThat(page.pagerId()).isEqualTo("results");
        assertThat(page.viewAction()).isEqualTo("view");
        assertThat(page.viewParams()).containsExactly("a", "b");
        assertThat(PageAction.stateKey("results")).isEqualTo("pager:results");
    }

    @Test
    @DisplayName("prev moves backwards")
    void prevMovesBackwards() {
        assertThat(
                        PageAction.fromContext(
                                        mockContext("l", "page", List.of("prev", "p", "home")))
                                .direction()
                                .delta())
                .isEqualTo(-1);
    }

    @Test
    @DisplayName("a malformed page id is a user-facing failure, not a generic error")
    void malformedIdIsUserFacing() {
        MenuContext ctx = mockContext("l", "page", List.of("sideways", "p", "home"));

        assertThatThrownBy(() -> PageAction.fromContext(ctx))
                .isInstanceOf(es.redactado.menu.api.UserFacingException.class)
                .hasMessage(MessageKeys.ERROR_BAD_PARAM);
    }

    // ------------------------------------------------------------- helpers

    private static List<String> items(int count) {
        return IntStream.range(0, count).mapToObj(i -> "item " + i).toList();
    }

    private static List<ContainerChildComponent> render(
            List<String> items, int pageSize, int page) {
        return render(items, pageSize, page, BuiltinPresets.DEFAULT);
    }

    private static List<ContainerChildComponent> render(
            List<String> items, int pageSize, int page, es.redactado.menu.preset.Preset preset) {
        return render(items, pageSize, page, preset, ViewContexts.forPreset(preset));
    }

    private static List<ContainerChildComponent> render(
            List<String> items,
            int pageSize,
            int page,
            es.redactado.menu.preset.Preset preset,
            MenuContext ctx) {
        if (page != 0) {
            ctx.session().putState(Pager.stateKey("p"), page);
        }
        return Pager.of("p", items, pageSize, item -> Text.of(item)).render(ctx);
    }

    private static MenuContext context() {
        return ViewContexts.forPreset(BuiltinPresets.DEFAULT);
    }

    /** A context with a chosen action and params, for the id tests. */
    /** A context reporting a chosen menu id, action and params. */
    private static MenuContext contextFor(String menuId, String action, List<String> params) {
        MenuContext ctx = ViewContexts.forPreset(BuiltinPresets.DEFAULT);
        when(ctx.menuId()).thenReturn(menuId);
        when(ctx.action()).thenReturn(action);
        when(ctx.params()).thenReturn(params);
        return ctx;
    }

    /** A context whose params are the ones a decoded id would carry. */
    private static MenuContext mockContext(String menuId, String action, List<String> params) {
        MenuContext ctx = ViewContexts.forPreset(BuiltinPresets.DEFAULT);
        when(ctx.menuId()).thenReturn(menuId);
        when(ctx.action()).thenReturn(action);
        when(ctx.params()).thenReturn(params);
        when(ctx.requireString(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(call -> params.get(call.<Integer>getArgument(0)));
        when(ctx.param(org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(
                        call -> {
                            int i = call.<Integer>getArgument(0);
                            return i < params.size()
                                    ? java.util.Optional.of(params.get(i))
                                    : java.util.Optional.<String>empty();
                        });
        return ctx;
    }

    private static String firstText(List<ContainerChildComponent> children) {
        List<String> found = new java.util.ArrayList<>();
        for (ContainerChildComponent child : children) {
            if (child instanceof TextDisplay display) {
                found.add(display.getContent());
            }
        }
        return found.isEmpty() ? null : found.getFirst();
    }

    private static List<String> texts(List<ContainerChildComponent> children) {
        List<String> found = new java.util.ArrayList<>();
        for (ContainerChildComponent child : children) {
            if (child instanceof TextDisplay display) {
                found.add(display.getContent());
            }
        }
        return found;
    }

    private static String indicator(List<ContainerChildComponent> children) {
        return buttons(children).get(1).getLabel();
    }

    /** The buttons, including the ones nested in an action row. */
    private static List<Button> buttons(List<ContainerChildComponent> children) {
        List<Button> found = new java.util.ArrayList<>();
        for (ContainerChildComponent child : children) {
            if (child instanceof ActionRow row) {
                row.getComponents().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .forEach(found::add);
            } else if (child instanceof Button button) {
                found.add(button);
            }
        }
        return found;
    }
}
