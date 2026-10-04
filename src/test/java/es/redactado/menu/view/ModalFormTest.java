package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.preset.BuiltinPresets;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.label.LabelChildComponentUnion;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Checks the modal a form builds and the answers it reads back.
 *
 * <p>Both directions matter and neither is observable from the other. A modal that is
 * built wrongly shows the user the wrong fields, and a read that is wrong silently hands a
 * handler an empty answer, which is the failure mode that reaches production unnoticed.
 */
class ModalFormTest {

    private static MenuContext context() {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        when(ctx.menuId()).thenReturn("entry");
        when(ctx.preset()).thenReturn(BuiltinPresets.DEFAULT);
        return ctx;
    }

    @Nested
    @DisplayName("the built modal")
    class Built {

        @Test
        @DisplayName("the id encodes the menu and the action, so the router routes it")
        void idEncodesTheAction() {
            ModalForm form = form();
            form.shortField("a", "A");

            assertThat(form.build().getId()).isEqualTo("menu:entry:apply");
            assertThat(form.build().getTitle()).isEqualTo("Apply");
        }

        @Test
        @DisplayName("params travel with the id")
        void paramsTravelWithTheId() {
            ModalForm form = ModalForm.create(context(), "apply", "Apply", "42");
            form.shortField("a", "A");

            assertThat(form.build().getId()).isEqualTo("menu:entry:apply:42");
        }

        @Test
        @DisplayName("every input is wrapped in a label, which is what Discord requires")
        void everyInputIsLabelled() {
            ModalForm form = form();
            form.shortField("name", "Name");
            form.paragraph("why", "Why");

            assertThat(labelsOf(form.build()))
                    .extracting(Label::getLabel)
                    .containsExactly("Name", "Why");
            assertThat(inputsOf(form.build()))
                    .extracting(TextInput::getCustomId)
                    .containsExactly("name", "why");
        }

        @Test
        @DisplayName("the style follows the factory the input came from")
        void styleFollowsTheFactory() {
            ModalForm form = form();
            form.shortField("name", "Name");
            form.paragraph("why", "Why");

            assertThat(inputsOf(form.build()))
                    .extracting(TextInput::getStyle)
                    .containsExactly(TextInputStyle.SHORT, TextInputStyle.PARAGRAPH);
        }

        @Test
        @DisplayName("an input is required until it says otherwise")
        void requiredDefaultsToTrue() {
            ModalForm form = form();
            form.shortField("a", "A");
            form.shortField("b", "B").required(false);

            assertThat(inputsOf(form.build()))
                    .extracting(TextInput::isRequired)
                    .containsExactly(true, false);
        }

        @Test
        @DisplayName("placeholder, value and length reach the input")
        void settingsReachTheInput() {
            ModalForm form = form();
            form.paragraph("why", "Why")
                    .placeholder("A few sentences")
                    .value("Because")
                    .length(10, 400);

            TextInput input = inputWithId(form.build(), "why");

            assertThat(input.getPlaceHolder()).isEqualTo("A few sentences");
            assertThat(input.getValue()).isEqualTo("Because");
            assertThat(input.getMinLength()).isEqualTo(10);
            assertThat(input.getMaxLength()).isEqualTo(400);
        }

        @Test
        @DisplayName("an input with no length set has none, rather than a length of zero")
        void noLengthIsUnset() {
            ModalForm form = form();
            form.shortField("a", "A");

            TextInput input = inputWithId(form.build(), "a");

            assertThat(input.getMinLength()).isEqualTo(-1);
            assertThat(input.getMaxLength()).isEqualTo(-1);
            assertThat(input.getPlaceHolder()).as("no placeholder was asked for").isNull();
            assertThat(input.getValue()).as("no value was asked for").isNull();
        }
    }

    @Nested
    @DisplayName("limits")
    class Boundaries {

        @Test
        @DisplayName("exactly five fields are allowed, a sixth is refused")
        void fieldCountIsChecked() {
            ModalForm full = filled(Limits.MAX_MODAL_FIELDS);

            assertThat(full.build().getComponents()).hasSize(Limits.MAX_MODAL_FIELDS);
            assertThatThrownBy(() -> full.shortField("sixth", "Sixth"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sixth")
                    .hasMessageContaining(String.valueOf(Limits.MAX_MODAL_FIELDS));
        }

        @Test
        @DisplayName("a modal with no fields is refused")
        void noFieldsIsRefused() {
            ModalForm empty = ModalForm.create(context(), "apply", "Apply");

            assertThatThrownBy(empty::build)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one field");
        }

        @Test
        @DisplayName("a title over the limit is refused, and one exactly at it is not")
        void titleLengthIsChecked() {
            String limit = "t".repeat(Limits.MAX_MODAL_TITLE_LENGTH);

            ModalForm form = ModalForm.create(context(), "apply", limit);
            form.shortField("a", "A");

            assertThat(form.build().getTitle()).isEqualTo(limit);
            assertThatThrownBy(() -> ModalForm.create(context(), "apply", limit + "t"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(Limits.MAX_MODAL_TITLE_LENGTH));
        }

        @Test
        @DisplayName("a blank title is refused")
        void blankTitleIsRefused() {
            assertThatThrownBy(() -> ModalForm.create(context(), "apply", " "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("title");
        }

        @Test
        @DisplayName("an over-long field id is refused, naming the field")
        void fieldIdLengthIsChecked() {
            assertThatThrownBy(
                            () ->
                                    form().shortField(
                                                    "i"
                                                            .repeat(
                                                                    Limits.MAX_MODAL_FIELD_ID_LENGTH
                                                                            + 1),
                                                    "A"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("field id");
        }

        @Test
        @DisplayName("an over-long label is refused, naming the field")
        void labelLengthIsChecked() {
            assertThatThrownBy(
                            () ->
                                    form().shortField(
                                                    "why",
                                                    "l".repeat(Limits.MAX_MODAL_LABEL_LENGTH + 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("why");
        }

        @Test
        @DisplayName("an over-long placeholder is refused, naming the field")
        void placeholderLengthIsChecked() {
            assertThatThrownBy(
                            () ->
                                    addedField("why", "Why")
                                            .placeholder(
                                                    "p"
                                                            .repeat(
                                                                    Limits
                                                                                    .MAX_MODAL_PLACEHOLDER_LENGTH
                                                                            + 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("why");
        }

        @Test
        @DisplayName("an over-long value is refused, naming the field")
        void valueLengthIsChecked() {
            assertThatThrownBy(
                            () ->
                                    addedField("why", "Why")
                                            .value("v".repeat(Limits.MAX_MODAL_VALUE_LENGTH + 1)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("why");
        }

        @Test
        @DisplayName("a negative, inverted or over-long range is refused, naming the field")
        void lengthRangeIsChecked() {
            assertThatThrownBy(() -> addedField("why", "Why").length(10, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("why")
                    .hasMessageContaining("[10, 5]");
            assertThatThrownBy(() -> addedField("why", "Why").length(-1, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("why");
            assertThatThrownBy(
                            () ->
                                    addedField("why", "Why")
                                            .length(0, Limits.MAX_MODAL_VALUE_LENGTH + 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("why");
        }

        @Test
        @DisplayName("the value limits are the ones JDA declares")
        void limitsComeFromJda() {
            // Read from the library rather than restated, so a JDA bump cannot leave this
            // test agreeing with a stale copy of the numbers.
            assertThat(Limits.MAX_MODAL_FIELDS)
                    .isEqualTo(net.dv8tion.jda.api.modals.Modal.MAX_COMPONENTS);
            assertThat(Limits.MAX_MODAL_TITLE_LENGTH)
                    .isEqualTo(net.dv8tion.jda.api.modals.Modal.MAX_TITLE_LENGTH);
            assertThat(Limits.MAX_MODAL_LABEL_LENGTH)
                    .isEqualTo(net.dv8tion.jda.api.components.label.Label.LABEL_MAX_LENGTH);
            assertThat(Limits.MAX_MODAL_FIELD_ID_LENGTH)
                    .isEqualTo(net.dv8tion.jda.api.components.textinput.TextInput.MAX_ID_LENGTH);
            assertThat(Limits.MAX_MODAL_PLACEHOLDER_LENGTH)
                    .isEqualTo(
                            net.dv8tion.jda.api.components.textinput.TextInput
                                    .MAX_PLACEHOLDER_LENGTH);
            assertThat(Limits.MAX_MODAL_VALUE_LENGTH)
                    .isEqualTo(net.dv8tion.jda.api.components.textinput.TextInput.MAX_VALUE_LENGTH);
        }
    }

    @Nested
    @DisplayName("reading the answers")
    class Reading {

        @Test
        @DisplayName("values are keyed by field id and trimmed")
        void valuesAreKeyedAndTrimmed() {
            ModalInteractionEvent event =
                    submission(mapping("name", "  Ada  "), mapping("why", "Because"));

            assertThat(ModalForm.read(event))
                    .containsExactlyInAnyOrderEntriesOf(Map.of("name", "Ada", "why", "Because"));
        }

        @Test
        @DisplayName("a field the user left empty reads as an empty string, not as missing")
        void emptyAnswerIsAnEmptyString() {
            ModalInteractionEvent event = submission(mapping("name", "Ada"), mapping("why", null));

            assertThat(ModalForm.read(event)).containsEntry("why", "");
        }

        @Test
        @DisplayName("the map is immutable, because a handler cannot fix it up")
        void theMapIsImmutable() {
            ModalInteractionEvent event = submission(mapping("name", "Ada"));
            Map<String, String> answers = ModalForm.read(event);

            assertThatThrownBy(() -> answers.put("role", "owner"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("a submission with no values reads as an empty map")
        void noValuesReadAsEmpty() {
            ModalInteractionEvent event = submission();

            assertThat(ModalForm.read(event)).isEmpty();
        }
    }

    // ------------------------------------------------------------- helpers

    private static ModalForm form() {
        return ModalForm.create(context(), "apply", "Apply");
    }

    /** The short field a fresh form would add, for the per-field limit checks. */
    private static ModalForm.Input addedField(String id, String label) {
        return form().shortField(id, label);
    }

    private static ModalForm filled(int count) {
        ModalForm form = form();
        for (int i = 0; i < count; i++) {
            form.shortField("f" + i, "Field " + i);
        }
        return form;
    }

    private static List<Label> labelsOf(Modal modal) {
        return modal.getComponents().stream().map(Label.class::cast).toList();
    }

    private static List<TextInput> inputsOf(Modal modal) {
        return labelsOf(modal).stream().map(ModalFormTest::inputOf).toList();
    }

    private static TextInput inputOf(Label label) {
        LabelChildComponentUnion child = label.getChild();
        assertThat(child).isInstanceOf(TextInput.class);
        return (TextInput) child;
    }

    private static TextInput inputWithId(Modal modal, String fieldId) {
        return inputsOf(modal).stream()
                .filter(input -> input.getCustomId().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the modal has no field '" + fieldId + "'"));
    }

    private static ModalMapping mapping(String customId, String value) {
        ModalMapping mapping = mock(ModalMapping.class);
        when(mapping.getCustomId()).thenReturn(customId);
        when(mapping.getAsString()).thenReturn(value);
        return mapping;
    }

    private static ModalInteractionEvent submission(ModalMapping... mappings) {
        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getValues()).thenReturn(List.of(mappings));
        return event;
    }
}
