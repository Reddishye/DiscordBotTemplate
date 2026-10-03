package es.redactado.menu.view;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.modals.Modal;

/**
 * A modal form, built from labelled inputs.
 *
 * <pre>{@code
 * // Inside a handler declared with Ack.MODAL, which is the only mode a modal may use.
 * ModalForm form = ModalForm.create(ctx, "apply", "Tell us about yourself");
 * form.shortField("name", "Name").placeholder("Ada Lovelace").required(true).length(1, 40);
 * form.paragraph("why", "Why do you want to join?").required(false);
 * showModal(ctx, form.build());
 *
 * // The declared modal action reads the answers by the ids it chose.
 * table.modal("apply", Ack.DEFER_REPLY, (ctx, event) -> {
 *     Map<String, String> answers = ModalForm.read(event);
 *     return executor.supply(() -> service.apply(answers.get("name"), answers.get("why")));
 * });
 * }</pre>
 *
 * <p><strong>This is a mutable builder, unlike every other component here.</strong> A form
 * is built inside the handler that opens it and thrown away, so nothing shares it and
 * nothing observes the mutation. Copy-on-write would mean every field being added copied
 * the whole form, for no benefit. It is therefore not thread-safe and must not be held in
 * a field, reused across clicks, or stored in a session.
 *
 * <p><strong>A field is added by naming it.</strong> {@link #shortField} and
 * {@link #paragraph} register the field and return it, so the per-field settings that
 * follow configure the field that was just added. There is no separate add step, because
 * there is no ordering in which a field could be configured before it existed. The
 * per-field calls return the {@link Input} rather than the form, so the example keeps the
 * form in a local and calls {@link #build()} on that.
 *
 * <p><strong>The id belongs to the menu, not the modal.</strong> It is encoded from the
 * context and the action, so a submission routes to the action the menu declared and a
 * button on the same view cannot open a form for a different one.
 *
 * <p><strong>A modal must be the first and only response.</strong> Opening one
 * acknowledges the interaction, so a form belongs behind {@code Ack.MODAL} or
 * {@code Ack.NONE} and never behind a deferred edit.
 */
public final class ModalForm {

    private final String id;
    private final String title;
    private final List<Input> inputs = new ArrayList<>(Limits.MAX_MODAL_FIELDS);

    private ModalForm(String id, String title) {
        this.id = id;
        this.title = title;
    }

    /**
     * Starts a form for an action.
     *
     * @param ctx the context of the interaction that will open it, used for the menu id
     * @param action the modal action to declare with {@code ActionTable.Builder#modal}
     * @param title the modal's title, at most {@link Limits#MAX_MODAL_TITLE_LENGTH}
     *     characters, already localized
     * @param params parameters appended to the modal id
     * @return a mutable form, to be discarded after {@link #build()}
     * @throws IllegalArgumentException if the action contains a colon, or the title is
     *     empty or too long, or the encoded modal id would be too long
     */
    public static ModalForm create(MenuContext ctx, String action, String title, String... params) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(action, "action");
        if (action.isEmpty()) {
            throw new IllegalArgumentException("A modal action must not be empty");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("A modal title must not be blank");
        }
        if (title.length() > Limits.MAX_MODAL_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "The modal title is %d characters, the limit is %d"
                            .formatted(title.length(), Limits.MAX_MODAL_TITLE_LENGTH));
        }
        return new ModalForm(ComponentId.encode(ctx.menuId(), action, params), title);
    }

    /**
     * Adds a single-line input.
     *
     * @param id the id the answers come back under
     * @param label the text above the input, already localized
     * @return the new input, to configure or ignore
     * @throws IllegalArgumentException if the form is already full
     */
    public Input shortField(String id, String label) {
        return add(id, label, TextInputStyle.SHORT);
    }

    /**
     * Adds a multi-line input.
     *
     * @param id the id the answers come back under
     * @param label the text above the input, already localized
     * @return the new input, to configure or ignore
     * @throws IllegalArgumentException if the form is already full
     */
    public Input paragraph(String id, String label) {
        return add(id, label, TextInputStyle.PARAGRAPH);
    }

    /**
     * Builds the modal.
     *
     * @return a modal carrying every input added, in the order they were added
     * @throws IllegalArgumentException if no input was added, or more were added than
     *     {@link Limits#MAX_MODAL_FIELDS}
     */
    public Modal build() {
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("A modal needs at least one field");
        }
        if (inputs.size() > Limits.MAX_MODAL_FIELDS) {
            throw new IllegalArgumentException(
                    "A modal accepts at most %d fields, got %d"
                            .formatted(Limits.MAX_MODAL_FIELDS, inputs.size()));
        }
        Modal.Builder builder = Modal.create(id, title);
        for (Input input : inputs) {
            builder.addComponents(input.component());
        }
        return builder.build();
    }

    /**
     * The answers of a submitted modal, keyed by field id and trimmed.
     *
     * <p>A field the user left empty maps to an empty string rather than being absent, so
     * a handler can read an answer without checking whether the field came back. A field
     * Discord did not send at all is absent from the map, which is the honest answer:
     * nothing can invent a value for it.
     *
     * <p>Declared static because reading needs no form; the submitted modal carries its
     * own ids.
     *
     * @param event the submission
     * @return an immutable map of field id to trimmed value
     * @throws NullPointerException if the event is null
     */
    public static Map<String, String> read(ModalInteractionEvent event) {
        Objects.requireNonNull(event, "event");
        Map<String, String> answers = new LinkedHashMap<>();
        for (ModalMapping mapping : event.getValues()) {
            String value = mapping.getAsString();
            answers.put(mapping.getCustomId(), value == null ? "" : value.trim());
        }
        return Map.copyOf(answers);
    }

    private Input add(String id, String label, TextInputStyle style) {
        if (inputs.size() >= Limits.MAX_MODAL_FIELDS) {
            throw new IllegalArgumentException(
                    "A modal accepts at most %d fields, cannot add '%s'"
                            .formatted(Limits.MAX_MODAL_FIELDS, id));
        }
        Input input = new Input(id, label, style);
        inputs.add(input);
        return input;
    }

    /**
     * One labelled input of a modal.
     *
     * <p>Mutable, like the form that owns it, and configured by the calls that follow the
     * {@code shortField} or {@code paragraph} that created it.
     */
    public static final class Input {

        private final String id;
        private final String label;
        private final TextInputStyle style;
        private String placeholder;
        private String value;
        private int minLength = -1;
        private int maxLength = -1;
        private boolean required = true;

        private Input(String id, String label, TextInputStyle style) {
            this.id = check(id, Limits.MAX_MODAL_FIELD_ID_LENGTH, "field id", null);
            this.label = check(label, Limits.MAX_MODAL_LABEL_LENGTH, "label", id);
            this.style = style;
        }

        /**
         * Whether the user must fill this in.
         *
         * <p>Default is true, which is also Discord's default for a modal input.
         *
         * @param required whether the input must be filled in
         * @return this input
         */
        public Input required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * The greyed hint inside the empty input.
         *
         * @param placeholder the hint, already localized
         * @return this input
         * @throws IllegalArgumentException if the hint is blank or longer than
         *     {@link Limits#MAX_MODAL_PLACEHOLDER_LENGTH}
         */
        public Input placeholder(String placeholder) {
            this.placeholder =
                    check(placeholder, Limits.MAX_MODAL_PLACEHOLDER_LENGTH, "placeholder", id);
            return this;
        }

        /**
         * Pre-fills the input.
         *
         * @param value the starting text
         * @return this input
         * @throws IllegalArgumentException if the value is longer than
         *     {@link Limits#MAX_MODAL_VALUE_LENGTH}
         */
        public Input value(String value) {
            this.value = check(value, Limits.MAX_MODAL_VALUE_LENGTH, "value", id);
            return this;
        }

        /**
         * How much text the input accepts.
         *
         * @param min the fewest characters, from 0
         * @param max the most, up to {@link Limits#MAX_MODAL_VALUE_LENGTH}
         * @return this input
         * @throws IllegalArgumentException if the range is negative, inverted, or longer
         *     than Discord allows
         */
        public Input length(int min, int max) {
            if (min < 0 || max < 1 || min > max || max > Limits.MAX_MODAL_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "The length of field '%s' must satisfy 0 <= min <= max <= %d, got [%d, %d]"
                                .formatted(id, Limits.MAX_MODAL_VALUE_LENGTH, min, max));
            }
            this.minLength = min;
            this.maxLength = max;
            return this;
        }

        /**
         * Wraps this input in the label Discord requires around a modal field.
         *
         * <p>Not called {@code build} on purpose: the terminal call on a chain of
         * {@code shortField} is the form's own {@link ModalForm#build()}, and naming this
         * one the same would let a chain that forgot to keep the form compile into a
         * {@code Label}.
         *
         * @return the label and input pair a modal is built from
         */
        Label component() {
            TextInput.Builder input = TextInput.create(id, style).setRequired(required);
            if (placeholder != null) {
                input.setPlaceholder(placeholder);
            }
            if (value != null) {
                input.setValue(value);
            }
            if (minLength >= 0) {
                input.setRequiredRange(minLength, maxLength);
            }
            return Label.of(label, input.build());
        }

        /**
         * Rejects a blank or over-long value, naming the field it belongs to.
         *
         * <p>The id is in every message because a form holds up to five fields built from
         * the same three calls, so "the label is too long" without the id leaves the
         * author counting which of the five they were looking at.
         *
         * @param fieldId the field the value belongs to, or null while that id is still
         *     being checked
         */
        private static String check(String value, int limit, String what, String fieldId) {
            String where = fieldId == null ? what : what + " of field '" + fieldId + "'";
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("A modal " + where + " must not be blank");
            }
            if (value.length() > limit) {
                throw new IllegalArgumentException(
                        "The modal %s is %d characters, the limit is %d"
                                .formatted(where, value.length(), limit));
            }
            return value;
        }
    }
}
