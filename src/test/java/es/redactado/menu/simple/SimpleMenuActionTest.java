package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.Msg;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a declared action turns into, and which names it refuses.
 *
 * <p>An action name is a segment of every component id that uses it and the key the router
 * dispatches on, so one name can only mean one thing in one menu. Every rule here is about
 * that: a duplicate would silently replace the first declaration, and a reserved one would
 * replace the framework's own navigation.
 */
class SimpleMenuActionTest {

    private static final ClickHandler NOOP = click -> click.done();
    private static final PickHandler NOOP_PICK = pick -> pick.done();

    @Test
    @DisplayName("every declared action reaches the table, next to the built-in ones")
    void actionsReachTheTable() {
        Menu menu =
                Menus.simple("demo")
                        .home(
                                v ->
                                        v.row(
                                                r ->
                                                        r.primary("save", Msg.key("a"), NOOP)
                                                                .and()
                                                                .secondary(
                                                                        "undo",
                                                                        Msg.key("b"),
                                                                        NOOP)))
                        .view(
                                "detail",
                                v ->
                                        v.select(
                                                "pick",
                                                Msg.key("c"),
                                                o -> o.option("x", "X"),
                                                NOOP_PICK))
                        .onSubmit("form", submit -> submit.done())
                        .build();

        ActionTable table = tableOf(menu);

        assertThat(table.button("save")).isPresent();
        assertThat(table.button("undo")).isPresent();
        assertThat(table.select("pick")).isPresent();
        assertThat(table.modal("form")).isPresent();
        assertThat(table.button("nav"))
                .as("a menu declared in four lines still navigates")
                .isPresent();
        assertThat(table.button("page")).isPresent();
    }

    @Test
    @DisplayName("a button defers an edit, and one that opens a modal does not")
    void acksAreTheDocumentedOnes() {
        Menu menu =
                Menus.simple("demo")
                        .home(
                                v ->
                                        v.row(
                                                r ->
                                                        r.primary("save", Msg.key("a"), NOOP)
                                                                .and()
                                                                .secondary(
                                                                        "ask", Msg.key("b"), NOOP)
                                                                .opensModal()))
                        .build();

        ActionTable table = tableOf(menu);

        assertThat(table.button("save").orElseThrow().ack())
                .as("a button that redraws must acknowledge before the handler runs")
                .isEqualTo(Ack.DEFER_EDIT);
        assertThat(table.button("ask").orElseThrow().ack())
                .as("a modal has to be the only answer, so nothing may be deferred first")
                .isEqualTo(Ack.MODAL);
    }

    @Test
    @DisplayName("a select and a submitted form both defer an edit")
    void selectAndSubmitAck() {
        Menu menu =
                Menus.simple("demo")
                        .home(
                                v ->
                                        v.select(
                                                "pick",
                                                Msg.key("c"),
                                                o -> o.option("x", "X"),
                                                NOOP_PICK))
                        .onSubmit("form", submit -> submit.done())
                        .build();

        ActionTable table = tableOf(menu);

        assertThat(table.select("pick").orElseThrow().ack()).isEqualTo(Ack.DEFER_EDIT);
        assertThat(table.modal("form").orElseThrow().ack()).isEqualTo(Ack.DEFER_EDIT);
    }

    @Test
    @DisplayName("two views using one action name are refused, naming both")
    void duplicateActionAcrossViewsIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.row(r -> r.primary("go", Msg.key("a"), NOOP)))
                                        .view(
                                                "detail",
                                                v ->
                                                        v.row(
                                                                r ->
                                                                        r.primary(
                                                                                "go",
                                                                                Msg.key("b"),
                                                                                NOOP))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'go'")
                .hasMessageContaining("declared twice")
                .hasMessageContaining("detail");
    }

    @Test
    @DisplayName("a button and a select cannot share a name")
    void buttonAndSelectCannotShareAName() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.row(
                                                                        r ->
                                                                                r.primary(
                                                                                        "go",
                                                                                        Msg.key(
                                                                                                "a"),
                                                                                        NOOP))
                                                                .select(
                                                                        "go",
                                                                        Msg.key("c"),
                                                                        o -> o.option("x", "X"),
                                                                        NOOP_PICK)))
                .as("one id space decodes buttons and selects alike")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declared twice");
    }

    @Test
    @DisplayName("a form submission cannot reuse a button name")
    void submissionCannotReuseAName() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.row(r -> r.primary("go", Msg.key("a"), NOOP)))
                                        .onSubmit("go", submit -> submit.done()))
                .as(
                        "a form is opened by a button and submitted by its id, so the two names"
                                + " would collide in the one id space")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declared twice");
    }

    @Test
    @DisplayName("the names the framework registers are refused")
    void reservedActionNamesAreRefused() {
        for (String reserved : List.of("nav", "page", "home")) {
            assertThatThrownBy(
                            () ->
                                    Menus.simple("demo")
                                            .home(
                                                    v ->
                                                            v.row(
                                                                    r ->
                                                                            r.primary(
                                                                                    reserved,
                                                                                    Msg.key("a"),
                                                                                    NOOP))))
                    .as("action '%s' is registered for every menu", reserved)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
        }
    }

    @Test
    @DisplayName("an action name with a colon is refused")
    void colonInActionNameIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.row(
                                                                r ->
                                                                        r.primary(
                                                                                "a:b",
                                                                                Msg.key("a"),
                                                                                NOOP))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain");
    }

    @Test
    @DisplayName("an action whose id could never fit is refused, counting its params")
    void oversizedActionIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.row(
                                                                r ->
                                                                        r.primary(
                                                                                        "save",
                                                                                        Msg.key(
                                                                                                "a"),
                                                                                        NOOP)
                                                                                .params(
                                                                                        "x"
                                                                                                .repeat(
                                                                                                        120)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot fit in a component id")
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("a row with six buttons is refused, naming the view")
    void tooManyButtonsIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.row(
                                                                r ->
                                                                        r.primary(
                                                                                        "a",
                                                                                        Msg.key(
                                                                                                "1"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .secondary(
                                                                                        "b",
                                                                                        Msg.key(
                                                                                                "2"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .success(
                                                                                        "c",
                                                                                        Msg.key(
                                                                                                "3"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .danger(
                                                                                        "d",
                                                                                        Msg.key(
                                                                                                "4"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .primary(
                                                                                        "e",
                                                                                        Msg.key(
                                                                                                "5"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .secondary(
                                                                                        "f",
                                                                                        Msg.key(
                                                                                                "6"),
                                                                                        NOOP))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 5");
    }

    @Test
    @DisplayName("five buttons are fine, because five is the limit and not the first step past it")
    void fiveButtonsAreFine() {
        assertThatCode(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.row(
                                                                r ->
                                                                        r.primary(
                                                                                        "a",
                                                                                        Msg.key(
                                                                                                "1"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .secondary(
                                                                                        "b",
                                                                                        Msg.key(
                                                                                                "2"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .success(
                                                                                        "c",
                                                                                        Msg.key(
                                                                                                "3"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .danger(
                                                                                        "d",
                                                                                        Msg.key(
                                                                                                "4"),
                                                                                        NOOP)
                                                                                .and()
                                                                                .primary(
                                                                                        "e",
                                                                                        Msg.key(
                                                                                                "5"),
                                                                                        NOOP)))
                                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a select gets a row of its own, so the two rules cannot collide")
    void selectNeverSharesARowWithButtons() {
        Menu menu =
                Menus.simple("demo")
                        .home(
                                v ->
                                        v.row(r -> r.primary("a", Msg.key("1"), NOOP))
                                                .select(
                                                        "pick",
                                                        Msg.key("c"),
                                                        o -> o.option("x", "X"),
                                                        NOOP_PICK))
                        .build();

        var container =
                menu.render(
                                SimpleContexts.of(
                                        "demo",
                                        "home",
                                        BuiltinPresets.DEFAULT,
                                        java.util.Locale.ENGLISH))
                        .join();

        assertThat(container.getComponents())
                .as("one row for the button, one for the select")
                .hasSize(2);
        container.getComponents().stream()
                .filter(net.dv8tion.jda.api.components.actionrow.ActionRow.class::isInstance)
                .forEach(
                        row ->
                                assertThat(
                                                ((net.dv8tion.jda.api.components.actionrow
                                                                        .ActionRow)
                                                                row)
                                                        .getComponents())
                                        .as("a row holds either buttons or one select")
                                        .hasSize(1));
    }

    @Test
    @DisplayName("back() in the home view is allowed, and renders the home view again")
    void backInHomeIsAllowed() {
        Menu menu =
                Menus.simple("demo")
                        .home(v -> v.text("Home").row(r -> r.back()))
                        .view("detail", v -> v.text("Detail").row(r -> r.back()))
                        .build();

        var container =
                menu.render(
                                SimpleContexts.of(
                                        "demo",
                                        "home",
                                        BuiltinPresets.DEFAULT,
                                        java.util.Locale.ENGLISH))
                        .join();

        assertThat(container.getComponents())
                .as("a Back that has nowhere to go stays on the view it is on")
                .isNotEmpty();
    }

    @Test
    @DisplayName("an interaction on a known action redraws the view that owns it")
    void knownActionMapsToItsOwningView() {
        Menu menu =
                Menus.simple("demo")
                        .home(v -> v.text("Home").row(r -> r.primary("bump", Msg.key("a"), NOOP)))
                        .view("detail", v -> v.text("Detail"))
                        .build();

        var ctx =
                SimpleContexts.of("demo", "bump", BuiltinPresets.DEFAULT, java.util.Locale.ENGLISH);

        assertThat(menu.currentView(ctx))
                .as("a click names the button pressed, not the screen it was pressed on")
                .isEqualTo(new es.redactado.menu.api.NavEntry("demo", "home", List.of()));
    }

    @Test
    @DisplayName("an unknown action lands on the home view, the one view that always exists")
    void unknownActionMapsToHome() {
        Menu menu = Menus.simple("demo").home(v -> v.text("Home")).build();

        var ctx =
                SimpleContexts.of(
                        "demo", "stale", BuiltinPresets.DEFAULT, java.util.Locale.ENGLISH);

        assertThat(menu.currentView(ctx))
                .isEqualTo(new es.redactado.menu.api.NavEntry("demo", "home", List.of()));
    }

    @Test
    @DisplayName("a view name is its own current view, because navigation lands on it")
    void viewNameIsItsOwnCurrentView() {
        Menu menu =
                Menus.simple("demo")
                        .home(v -> v.text("Home"))
                        .view("detail", v -> v.text("D"))
                        .build();

        var ctx =
                SimpleContexts.of(
                        "demo", "detail", BuiltinPresets.DEFAULT, java.util.Locale.ENGLISH);

        assertThat(menu.currentView(ctx))
                .isEqualTo(new es.redactado.menu.api.NavEntry("demo", "detail", List.of()));
    }

    @Test
    @DisplayName("a button needs a label and a handler")
    void buttonNeedsBoth() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.row(r -> r.primary("a", Msg.key("x"), null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("handler");
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.row(r -> r.primary("a", (Msg) null, NOOP))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    @DisplayName("a select needs a placeholder and a handler")
    void selectNeedsBoth() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.select("pick", null, o -> {}, NOOP_PICK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder");
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.select("pick", Msg.key("c"), o -> {}, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("an option needs a value and a label, and a range must not be inverted")
    void selectSpecChecksItsInput() {
        assertThatThrownBy(() -> new SelectSpec().option("", "X"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectSpec().option("x", ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SelectSpec().range(3, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not inverted");
        assertThatThrownBy(() -> new SelectSpec().range(-1, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a click may only open a modal when it declared that it would")
    void modalIsRefusedOnAnOrdinaryButton() {
        RecordingSupport support = new RecordingSupport();
        Click ordinary = new Click(support, null, false);
        Click modalButton = new Click(support, null, true);
        // Built through the framework's own form builder rather than by hand: a modal is a
        // Label wrapping a TextInput, and this is the code that knows that.
        es.redactado.menu.view.ModalForm form =
                es.redactado.menu.view.ModalForm.create(
                        SimpleContexts.of(
                                "demo", "home", BuiltinPresets.DEFAULT, java.util.Locale.ENGLISH),
                        "form",
                        "Title");
        form.shortField("name", "Name");
        net.dv8tion.jda.api.modals.Modal modal = form.build();

        assertThatThrownBy(() -> ordinary.modal(modal))
                .as(
                        "the interaction was already acknowledged for the edit, so Discord would"
                                + " refuse the modal and the user would see nothing happen")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("opensModal");

        modalButton.modal(modal);
        assertThat(support.modal)
                .as("a button that declared opensModal() goes through the framework's modal path")
                .isSameAs(modal);
    }

    /** A trigger support that only records what it was asked to do. */
    private static final class RecordingSupport implements Trigger.Support {

        private net.dv8tion.jda.api.modals.Modal modal;

        @Override
        public es.redactado.menu.api.MenuContext ctx() {
            return null;
        }

        @Override
        public CompletableFuture<Void> refresh() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> go(String menuId, String viewName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> done() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void showModal(net.dv8tion.jda.api.modals.Modal modal) {
            this.modal = modal;
        }
    }

    @Test
    @DisplayName("a link row needs a URL")
    void linkNeedsAUrl() {
        assertThatThrownBy(() -> Menus.simple("demo").home(v -> v.row(r -> r.link("Docs", " "))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL");
    }

    private static ActionTable tableOf(Menu menu) {
        ActionTable.Builder builder = ActionTable.builder();
        menu.actions(builder);
        return builder.build();
    }
}
