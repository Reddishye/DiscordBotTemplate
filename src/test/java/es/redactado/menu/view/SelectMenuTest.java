package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Validator;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.Preset;
import java.util.List;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks what a select puts on the wire and what it refuses to build.
 *
 * <p>The custom id is the contract with the router, so it is decoded here exactly as the
 * router decodes it rather than compared as a string. The limits are checked at the call
 * that breaks them, so each test names the mistake rather than the render that found it.
 */
class SelectMenuTest {

    /** A select with three choices, the shape most tests start from. */
    private static SelectMenu sample() {
        return SelectMenu.of("assign", "Pick a role")
                .option("owner", "Owner", "Can edit everything")
                .option("member", "Member")
                .option("guest", "Guest");
    }

    @Nested
    @DisplayName("the action and placeholder")
    class Declaration {

        @Test
        @DisplayName("the id encodes the menu, the action and the params")
        void idEncodesTheAction() {
            ComponentId id =
                    ComponentId.decode(render(sample().params("42")).getCustomId()).orElseThrow();

            assertThat(id.menuId()).isEqualTo("m");
            assertThat(id.action()).isEqualTo("assign");
            assertThat(id.params()).containsExactly("42");
        }

        @Test
        @DisplayName("an action carrying a colon is refused rather than corrupting the id")
        void colonInActionIsRefused() {
            assertThatThrownBy(() -> SelectMenu.of("as:sign", "Pick"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("as:sign");
        }

        @Test
        @DisplayName("an empty action or placeholder is refused")
        void emptyDeclarationIsRefused() {
            assertThatThrownBy(() -> SelectMenu.of("", "Pick"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("action");
            assertThatThrownBy(() -> SelectMenu.of("assign", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("placeholder");
        }

        @Test
        @DisplayName("a placeholder one character over the limit is refused")
        void placeholderLengthIsChecked() {
            String limit = "p".repeat(Limits.MAX_SELECT_PLACEHOLDER_LENGTH);

            assertThat(render(SelectMenu.of("assign", limit).option("a", "A")).getPlaceholder())
                    .isEqualTo(limit);
            assertThatThrownBy(() -> SelectMenu.of("assign", limit + "p"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(Limits.MAX_SELECT_PLACEHOLDER_LENGTH));
        }

        @Test
        @DisplayName("a select with no options cannot render")
        void noOptionsCannotRender() {
            SelectMenu empty = SelectMenu.of("assign", "Pick");

            assertThatThrownBy(() -> render(empty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("assign");
        }
    }

    @Nested
    @DisplayName("options")
    class Options {

        @Test
        @DisplayName("the value is what comes back and the label is what is read")
        void valueAndLabelKeepTheirPlaces() {
            List<SelectOption> options = render(sample()).getOptions();

            assertThat(options)
                    .extracting(SelectOption::getValue)
                    .containsExactly("owner", "member", "guest");
            assertThat(options)
                    .extracting(SelectOption::getLabel)
                    .containsExactly("Owner", "Member", "Guest");
            assertThat(options.get(0).getDescription()).isEqualTo("Can edit everything");
            assertThat(options.get(1).getDescription()).as("optional description").isNull();
        }

        @Test
        @DisplayName("a repeated value is refused, naming the value")
        void duplicateValueIsRefused() {
            assertThatThrownBy(() -> sample().option("member", "Another name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("member");
        }

        @Test
        @DisplayName("an empty value or label is refused")
        void emptyOptionIsRefused() {
            assertThatThrownBy(() -> sample().option("", "Name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("value");
            assertThatThrownBy(() -> sample().option("v", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("label");
        }

        @Test
        @DisplayName("a value, label or description one character over the limit is refused")
        void optionLengthsAreChecked() {
            assertThatThrownBy(
                            () ->
                                    sample().option(
                                                    longText(Limits.MAX_SELECT_VALUE_LENGTH + 1),
                                                    "Name"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("option value");
            assertThatThrownBy(
                            () ->
                                    sample().option(
                                                    "v",
                                                    longText(Limits.MAX_SELECT_LABEL_LENGTH + 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("option label");
            assertThatThrownBy(
                            () ->
                                    sample().option(
                                                    "v",
                                                    "Name",
                                                    longText(
                                                            Limits.MAX_SELECT_DESCRIPTION_LENGTH
                                                                    + 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("option description");
        }

        @Test
        @DisplayName("exactly the maximum number of options is allowed, one more is not")
        void optionCountIsChecked() {
            SelectMenu full = fill(Limits.MAX_SELECT_OPTIONS);

            assertThat(render(full).getOptions()).hasSize(Limits.MAX_SELECT_OPTIONS);
            assertThatThrownBy(() -> full.option("one-too-many", "One too many"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(Limits.MAX_SELECT_OPTIONS));
        }
    }

    @Nested
    @DisplayName("defaults")
    class Defaults {

        @Test
        @DisplayName("a chosen value arrives marked as the default")
        void defaultsMarkTheirOptions() {
            List<SelectOption> options =
                    render(sample().range(1, 2).selected("member")).getOptions();

            assertThat(options.stream().filter(SelectOption::isDefault))
                    .extracting(SelectOption::getValue)
                    .containsExactly("member");
        }

        @Test
        @DisplayName("a default that is not an option is refused, naming the value")
        void unknownDefaultIsRefused() {
            assertThatThrownBy(() -> sample().selected("owner", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("admin");
        }

        @Test
        @DisplayName("no defaults at all is the same as none requested")
        void noDefaultsMarksNothing() {
            assertThat(render(sample()).getOptions()).noneMatch(SelectOption::isDefault);
        }

        @Test
        @DisplayName("more defaults than the range allows fails when the select is built")
        void defaultsMustFitTheRange() {
            // Not checked when selected() is called, because a select is immutable and
            // range() may come after it; a check there would depend on the order the
            // author wrote. Discord enforces the pairing, and it is enforced here at
            // build time rather than being silently ignored.
            SelectMenu tooManyDefaults = sample().range(1, 1).selected("owner", "member");

            assertThatThrownBy(() -> render(tooManyDefaults))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("default values");
        }
    }

    @Nested
    @DisplayName("the range")
    class Range {

        @Test
        @DisplayName("the range is rendered, and defaults to exactly one choice")
        void rangeIsRendered() {
            assertThat(render(sample()).getMinValues()).isOne();
            assertThat(render(sample()).getMaxValues()).isOne();

            StringSelectMenu open = render(sample().range(0, 3));
            assertThat(open.getMinValues()).isZero();
            assertThat(open.getMaxValues()).isEqualTo(3);
        }

        @Test
        @DisplayName("a range that is negative, inverted or too long is refused")
        void invalidRangeIsRefused() {
            assertThatThrownBy(() -> sample().range(2, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("[2, 1]");
            assertThatThrownBy(() -> sample().range(-1, 1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> sample().range(0, Limits.MAX_SELECT_OPTIONS + 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(Limits.MAX_SELECT_OPTIONS));
        }
    }

    @Nested
    @DisplayName("inside a row")
    class InsideARow {

        @Test
        @DisplayName("a select on its own renders as the row's only item")
        void aloneInARow() {
            Container container = MenuBuilder.create("m").add(Row.of(sample())).build(context());

            assertThat(selectOf(container).getCustomId()).isEqualTo("menu:m:assign");
        }

        @Test
        @DisplayName("a select beside a button is refused")
        void besideAButtonIsRefused() {
            assertThatThrownBy(() -> Row.of(sample(), ActionButton.primary("save", "Save")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only item");
            assertThatThrownBy(() -> Row.of(ActionButton.primary("save", "Save"), sample()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only item");
        }

        @Test
        @DisplayName("two selects cannot share a row either")
        void twoSelectsAreRefused() {
            assertThatThrownBy(
                            () -> Row.of(sample(), SelectMenu.of("other", "Pick").option("v", "V")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("only item");
        }

        @Test
        @DisplayName("buttons still share a row freely")
        void buttonsStillShareARow() {
            Row row = Row.of(ActionButton.primary("a", "A"), ActionButton.secondary("b", "B"));

            assertThat(row.render(context())).hasSize(1);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName(
            "a select renders identically under every preset, because Discord gives it no style")
    void renderingIsPresetIndependent(String name, Preset preset) {
        Container container =
                MenuBuilder.create("m")
                        .add(Row.of(sample().range(1, 2).selected("member")))
                        .build(context(preset));

        assertThat(selectOf(container).getOptions())
                .as("%s must not change what a select offers", name)
                .extracting(SelectOption::getValue)
                .containsExactly("owner", "member", "guest");
        assertThat(Validator.validate(container).isValid())
                .as("%s must render a select within Discord's limits", name)
                .isTrue();
    }

    static Stream<Arguments> presets() {
        return BuiltinPresets.all().stream().map(preset -> Arguments.of(preset.name(), preset));
    }

    @Test
    @DisplayName("a disabled select renders disabled")
    void disabledIsRendered() {
        assertThat(render(sample().disabled(true)).isDisabled()).isTrue();
        assertThat(render(sample()).isDisabled()).isFalse();
    }

    // ------------------------------------------------------------- helpers

    private static String longText(int length) {
        return "x".repeat(length);
    }

    private static SelectMenu fill(int count) {
        SelectMenu select = SelectMenu.of("assign", "Pick");
        for (int i = 0; i < count; i++) {
            select = select.option("v" + i, "Option " + i);
        }
        return select;
    }

    private static StringSelectMenu render(SelectMenu select) {
        return (StringSelectMenu) select.render(context());
    }

    private static MenuContext context() {
        return ViewContexts.english();
    }

    private static MenuContext context(Preset preset) {
        return ViewContexts.forPreset(preset);
    }

    /** The select inside a rendered container, which always sits in an action row. */
    private static StringSelectMenu selectOf(Container container) {
        for (var child : container.getComponents()) {
            if (child instanceof ActionRow row) {
                return row.getComponents().stream()
                        .filter(StringSelectMenu.class::isInstance)
                        .map(StringSelectMenu.class::cast)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("the container holds no select"));
            }
        }
        throw new AssertionError("the container holds no action row");
    }
}
