package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.Msg;
import es.redactado.menu.view.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The checks that run when the menu is built.
 *
 * <p>Each of these would otherwise be a message in front of a user, a rejected component, or
 * a blank menu, and each is knowable while the menu is still being written. The rule
 * throughout: name the view and the element, because an author who is told which line is
 * wrong can fix it, and one told only that the menu is broken cannot.
 */
class SimpleMenuValidationTest {

    private static final ClickHandler NOOP = click -> click.done();
    private static final PickHandler NOOP_PICK = pick -> pick.done();

    @Test
    @DisplayName("a menu with no home view is refused, naming the menu")
    void missingHomeIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo").view("other", v -> v.text("x")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo")
                .hasMessageContaining("no home view");
    }

    @Test
    @DisplayName("a view with more elements than a container holds is refused")
    void tooManyElementsIsRefused() {
        int over = Limits.MAX_CONTAINER_CHILDREN + 1;

        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v -> {
                                                    for (int i = 0; i < over; i++) {
                                                        v.text("line " + i);
                                                    }
                                                })
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("home")
                .hasMessageContaining(String.valueOf(Limits.MAX_CONTAINER_CHILDREN));
    }

    @Test
    @DisplayName("a view that exactly fills a container is allowed")
    void exactlyFullIsAllowed() {
        assertThatCode(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v -> {
                                                    for (int i = 0;
                                                            i < Limits.MAX_CONTAINER_CHILDREN;
                                                            i++) {
                                                        v.text("line " + i);
                                                    }
                                                })
                                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a row with nothing in it is refused, naming the view")
    void emptyRowIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo").home(v -> v.row(r -> {})).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has nothing in it");
    }

    @Test
    @DisplayName("a select with no options is refused")
    void emptySelectIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.select(
                                                                "pick",
                                                                Msg.key("a"),
                                                                o -> {},
                                                                NOOP_PICK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no options");
    }

    @Test
    @DisplayName("a select with more options than Discord accepts is refused")
    void tooManyOptionsIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.select(
                                                                "pick",
                                                                Msg.key("a"),
                                                                o -> {
                                                                    for (int i = 0;
                                                                            i
                                                                                    <= Limits
                                                                                            .MAX_SELECT_OPTIONS;
                                                                            i++) {
                                                                        o.option("v" + i, "L" + i);
                                                                    }
                                                                },
                                                                NOOP_PICK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(Limits.MAX_SELECT_OPTIONS));
    }

    @Test
    @DisplayName("a select may not start on a value no option declares")
    void selectedValueMustBeDeclared() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.select(
                                                                "pick",
                                                                Msg.key("a"),
                                                                o ->
                                                                        o.option("one", "One")
                                                                                .selected("two"),
                                                                NOOP_PICK)))
                .as(
                        "Discord rejects a selected value outside the option list, so the failure"
                                + " would appear as an unusable select rather than as a mistake")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'two'");
    }

    @Test
    @DisplayName("a select may start on a value one of its options declares")
    void declaredSelectedValueIsFine() {
        assertThatCode(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.select(
                                                                "pick",
                                                                Msg.key("a"),
                                                                o ->
                                                                        o.option("one", "One")
                                                                                .option(
                                                                                        "two",
                                                                                        "Two")
                                                                                .selected("two"),
                                                                NOOP_PICK))
                                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("every element kind is counted, not only the text ones")
    void allElementsAreCounted() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v -> {
                                                    for (int i = 0;
                                                            i <= Limits.MAX_CONTAINER_CHILDREN;
                                                            i++) {
                                                        v.divider();
                                                    }
                                                })
                                        .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares 26 elements");
    }

    @Test
    @DisplayName("a pager counts as one element however many items it draws")
    void pagerIsOneElement() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            many.add("item " + i);
        }

        assertThatCode(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.list(
                                                                        "items",
                                                                        scope -> many,
                                                                        20,
                                                                        String::toString)
                                                                .row(
                                                                        r ->
                                                                                r.primary(
                                                                                        "a",
                                                                                        Msg.key(
                                                                                                "x"),
                                                                                        NOOP))
                                                                .custom(Text.small("escape")))
                                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a view with elements in every supported form builds")
    void everyElementKindBuilds() {
        assertThatCode(
                        () ->
                                Menus.simple("demo")
                                        .tone(es.redactado.menu.preset.Tone.WARNING)
                                        .preset("midnight")
                                        .shared()
                                        .loadTimeout(java.time.Duration.ofSeconds(2))
                                        .home(
                                                v ->
                                                        v.header(Msg.key("t"), Msg.key("s"))
                                                                .header("key only")
                                                                .text("literal")
                                                                .message(Msg.key("m"))
                                                                .text(
                                                                        scope ->
                                                                                "model "
                                                                                        + scope
                                                                                                .data())
                                                                .field(Msg.key("l"), scope -> "v")
                                                                .divider()
                                                                .space()
                                                                .section(
                                                                        Msg.key("sec"),
                                                                        "https://example.com/a.png")
                                                                .list(
                                                                        "items",
                                                                        scope -> List.of("x"),
                                                                        5,
                                                                        String::toString)
                                                                .custom(Text.small("c"))
                                                                .row(
                                                                        r ->
                                                                                r.primary(
                                                                                                "a",
                                                                                                Msg
                                                                                                        .key(
                                                                                                                "x"),
                                                                                                NOOP)
                                                                                        .icon(
                                                                                                es
                                                                                                        .redactado
                                                                                                        .menu
                                                                                                        .preset
                                                                                                        .IconKey
                                                                                                        .OK)
                                                                                        .params("p")
                                                                                        .disabled(
                                                                                                false)
                                                                                        .and()
                                                                                        .secondary(
                                                                                                "b",
                                                                                                "Literal",
                                                                                                NOOP)
                                                                                        .and()
                                                                                        .success(
                                                                                                "c",
                                                                                                Msg
                                                                                                        .key(
                                                                                                                "x"),
                                                                                                NOOP)
                                                                                        .and()
                                                                                        .danger(
                                                                                                "d",
                                                                                                Msg
                                                                                                        .key(
                                                                                                                "x"),
                                                                                                NOOP)
                                                                                        .and()
                                                                                        .link(
                                                                                                Msg
                                                                                                        .key(
                                                                                                                "x"),
                                                                                                "https://example.com"))
                                                                .row(
                                                                        r ->
                                                                                r.link(
                                                                                                "Literal",
                                                                                                "https://example.com")
                                                                                        .back()
                                                                                        .view(
                                                                                                "detail",
                                                                                                "Detail")
                                                                                        .item(
                                                                                                new CountingRowItem())))
                                        .view("detail", v -> v.text("Detail"))
                                        .onSubmit("form", submit -> submit.done())
                                        .build())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a menu built twice from one builder is the same menu, twice")
    void buildIsRepeatable() {
        Consumer<ViewBuilder<Void>> home =
                v -> v.text("Body").row(r -> r.primary("a", Msg.key("x"), NOOP));
        SimpleMenuBuilder<Void> builder = Menus.simple("demo").home(home);

        Menu first = builder.build();
        Menu second = builder.build();

        assertThat(first.id()).isEqualTo(second.id());
        assertThat(
                        first.render(
                                        SimpleContexts.of(
                                                "demo",
                                                "home",
                                                es.redactado.menu.preset.BuiltinPresets.DEFAULT,
                                                java.util.Locale.ENGLISH))
                                .join())
                .as("a built menu holds no state from having been built")
                .isNotNull();
    }

    /**
     * A row item that renders nothing, to prove the escape hatch takes whatever the view
     * package offers rather than only what the DSL names.
     */
    private static final class CountingRowItem implements es.redactado.menu.view.RowItem {

        @Override
        public net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent render(
                es.redactado.menu.api.MenuContext ctx) {
            return net.dv8tion.jda.api.components.buttons.Button.secondary("noop", "Noop");
        }
    }
}
