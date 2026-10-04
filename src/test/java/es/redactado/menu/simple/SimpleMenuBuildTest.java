package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.Menu;
import es.redactado.menu.preset.Tone;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a simple menu refuses to build.
 *
 * <p>Every rule here is one the framework could otherwise only discover at the first render
 * in front of a user, which is the worst possible moment: after deploy, on a message someone
 * is looking at. Refusing at authoring time costs nothing and names the view and the element.
 */
class SimpleMenuBuildTest {

    @Test
    @DisplayName("a menu with no home view is refused")
    void missingHomeIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo").view("detail", v -> v.text("x")).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no home view");
    }

    @Test
    @DisplayName("a second home view is refused rather than replacing the first")
    void secondHomeIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.text("first"))
                                        .home(v -> v.text("second")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a home view");
    }

    @Test
    @DisplayName("two views of one name are refused")
    void duplicateViewNameIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.text("x"))
                                        .view("detail", v -> v.text("a"))
                                        .view("detail", v -> v.text("b")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("detail")
                .hasMessageContaining("already has a view");
    }

    @Test
    @DisplayName("a view with nothing in it is refused, naming the view")
    void emptyViewIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo").home(v -> {}).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("home")
                .hasMessageContaining("no elements");
    }

    @Test
    @DisplayName("the names the framework already uses are refused")
    void reservedViewNamesAreRefused() {
        for (String reserved : List.of("nav", "page", "home")) {
            assertThatThrownBy(
                            () ->
                                    Menus.simple("demo")
                                            .home(v -> v.text("x"))
                                            .view(reserved, v -> v.text("y")))
                    .as("view name '%s' is reserved", reserved)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reserved");
        }
    }

    @Test
    @DisplayName("a name with a colon is refused, because a colon separates id segments")
    void colonIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo:one").home(v -> v.text("x")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menu id must not contain");
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(v -> v.text("x"))
                                        .view("a:b", v -> v.text("y")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("view name must not contain");
    }

    @Test
    @DisplayName("an empty id is refused")
    void emptyIdIsRefused() {
        assertThatThrownBy(() -> Menus.simple("").home(v -> v.text("x")).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    @DisplayName("a view whose id could never fit a component id is refused")
    void oversizedIdIsRefused() {
        String long1 = "a".repeat(60);
        String long2 = "b".repeat(50);

        assertThatThrownBy(
                        () ->
                                Menus.simple(long1)
                                        .home(v -> v.text("x"))
                                        .view(long2, v -> v.text("y")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot fit in a component id")
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("a list id the pager would refuse is refused where it is declared")
    void badListIdIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.list(
                                                                "Bad-Id",
                                                                scope -> List.of("x"),
                                                                5,
                                                                String::toString)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[a-z0-9_]{1,20}");
    }

    @Test
    @DisplayName("two lists with one id are refused, because the paging state is stored under it")
    void duplicateListIdIsRefused() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.list(
                                                                        "items",
                                                                        scope -> List.of("x"),
                                                                        5,
                                                                        String::toString)
                                                                .list(
                                                                        "items",
                                                                        scope -> List.of("y"),
                                                                        5,
                                                                        String::toString)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared twice");
    }

    @Test
    @DisplayName("the same list id in two views of one menu is still one id")
    void listIdIsUniquePerMenu() {
        assertThatThrownBy(
                        () ->
                                Menus.simple("demo")
                                        .home(
                                                v ->
                                                        v.list(
                                                                "items",
                                                                scope -> List.of("x"),
                                                                5,
                                                                String::toString))
                                        .view(
                                                "detail",
                                                v ->
                                                        v.list(
                                                                "items",
                                                                scope -> List.of("y"),
                                                                5,
                                                                String::toString)))
                .as("the session stores one page number per id, so two lists would page together")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("declared twice");
    }

    @Test
    @DisplayName("a null loader is refused rather than becoming a menu with no data")
    void nullLoaderIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a blank preset name is refused, because it would never resolve")
    void blankPresetIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo").preset(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preset name");
    }

    @Test
    @DisplayName("a null tone is refused")
    void nullToneIsRefused() {
        assertThatThrownBy(() -> Menus.simple("demo").tone(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a menu that breaks no rule builds, and the ids it will use are knowable")
    void aGoodMenuBuilds() {
        assertThatCode(
                        () ->
                                Menus.simple("demo")
                                        .tone(Tone.INFO)
                                        .preset("midnight")
                                        .shared()
                                        .loadTimeout(java.time.Duration.ofSeconds(3))
                                        .home(
                                                v ->
                                                        v.text("x")
                                                                .list(
                                                                        "a",
                                                                        scope -> List.of("y"),
                                                                        5,
                                                                        String::toString))
                                        .view("detail", v -> v.text("y"))
                                        .build())
                .doesNotThrowAnyException();

        Menu menu = Menus.simple("demo").home(v -> v.text("x")).build();
        assertThat(menu.id()).isEqualTo("demo");

        ActionTable.Builder table = ActionTable.builder();
        menu.actions(table);
        ActionTable built = table.build();
        assertThat(built.button("nav")).isPresent();
        assertThat(built.button("page"))
                .as(
                        "every menu keeps the built-in navigation and paging, even one declared"
                                + " in four lines")
                .isPresent();
    }

    @Test
    @DisplayName("the built menu is the framework's own type, not a second kind of menu")
    void builtMenuIsAnOrdinaryMenu() {
        Menu menu = Menus.simple("demo").home(v -> v.text("x")).build();

        assertThat(menu).isInstanceOf(Menu.class);
        assertThat(menu)
                .as(
                        "extending the framework's base class is what brings the loader timeout,"
                                + " the modal path and the edit path with it")
                .isInstanceOf(es.redactado.menu.core.AbstractMenu.class);
    }
}
