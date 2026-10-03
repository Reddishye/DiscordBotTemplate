package es.redactado.menu.view;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

/**
 * A string select menu: one row of choices, of which the user picks one or more.
 *
 * <pre>{@code
 * // Declared once, next to the handler it feeds.
 * Row.of(SelectMenu.of("assign", "Pick a role")
 *         .option("owner", "Owner", "Can edit everything")
 *         .option("member", "Member")
 *         .range(1, 1)
 *         .selected("member"))
 * }</pre>
 *
 * <p><strong>The value comes first, the label second.</strong> JDA's own
 * {@code addOption} takes them the other way round, which is an easy mistake to carry
 * into a menu and impossible to see in a rendered message, because a swapped pair still
 * renders. Here the first argument is what a handler receives and the second is what a
 * user reads, so the order matches the direction the data travels.
 *
 * <p><strong>A select fills its row alone.</strong> Discord gives it the whole width, and
 * {@link Row} rejects a row that mixes one with buttons rather than letting Discord
 * reject the message.
 *
 * <p><strong>Presets have nothing to say about a select.</strong> Discord gives a select
 * no colour and no icon, so there is nothing for a theme to change. That is why this
 * component reads no preset value, and it is why adding one later would be a mistake
 * rather than an improvement.
 *
 * <p><strong>Two rules are checked here that JDA does not check.</strong> A duplicate
 * value silently marks two options as selected by one default, and a default that matches
 * no option is dropped without a word. Both are silent, both are typos, and both are
 * caught at the call that made them.
 */
public final class SelectMenu implements RowItem {

    private final String action;
    private final String placeholder;
    private final List<Option> options;
    private final List<String> selected;
    private final int minValues;
    private final int maxValues;
    private final boolean disabled;
    private final String[] params;

    /** One choice. Value first because that is the order a handler receives them in. */
    private record Option(String value, String label, String description) {}

    private SelectMenu(
            String action,
            String placeholder,
            List<Option> options,
            List<String> selected,
            int minValues,
            int maxValues,
            boolean disabled,
            String[] params) {
        this.action = action;
        this.placeholder = placeholder;
        this.options = options;
        this.selected = selected;
        this.minValues = minValues;
        this.maxValues = maxValues;
        this.disabled = disabled;
        this.params = params.clone();
    }

    /**
     * A select with no options yet.
     *
     * <p>At least one {@link #option(String, String)} is required before the component
     * renders, because Discord rejects a select with none.
     *
     * @param action the select action, declared with {@code ActionTable.Builder#select}
     * @param placeholder the text shown while nothing is chosen, already localized
     * @return the component
     * @throws IllegalArgumentException if the action or placeholder is empty, if the
     *     action contains a colon, or if the placeholder is too long
     */
    public static SelectMenu of(String action, String placeholder) {
        requireText(action, "action");
        if (action.indexOf(':') >= 0) {
            throw new IllegalArgumentException("The select action must not contain ':': " + action);
        }
        requireText(placeholder, "placeholder");
        requireLength(placeholder, Limits.MAX_SELECT_PLACEHOLDER_LENGTH, "placeholder");
        return new SelectMenu(
                action, placeholder, List.of(), List.of(), 1, 1, false, new String[0]);
    }

    /**
     * Adds a choice.
     *
     * @param value what a handler receives when the user picks it
     * @param label what the user reads
     * @return a copy of this select with the choice added
     * @throws IllegalArgumentException if the value or label is empty, if either is
     *     too long, or if the value is already an option
     */
    public SelectMenu option(String value, String label) {
        return option(value, label, null);
    }

    /**
     * Adds a choice with a description under it.
     *
     * @param value what a handler receives when the user picks it
     * @param label what the user reads
     * @param description the longer explanation, already localized
     * @return a copy of this select with the choice added
     * @throws IllegalArgumentException if the value or label is empty, if any of the
     *     three is too long, or if the value is already an option
     */
    public SelectMenu option(String value, String label, String description) {
        requireText(value, "option value");
        requireText(label, "option label");
        requireLength(value, Limits.MAX_SELECT_VALUE_LENGTH, "option value");
        requireLength(label, Limits.MAX_SELECT_LABEL_LENGTH, "option label");
        if (description != null) {
            requireText(description, "option description");
            requireLength(description, Limits.MAX_SELECT_DESCRIPTION_LENGTH, "option description");
        }
        if (has(options, value)) {
            throw new IllegalArgumentException("Duplicate option value: " + value);
        }
        if (options.size() >= Limits.MAX_SELECT_OPTIONS) {
            throw new IllegalArgumentException(
                    "A select menu accepts at most %d options, adding '%s' would exceed it"
                            .formatted(Limits.MAX_SELECT_OPTIONS, value));
        }
        List<Option> extended = new ArrayList<>(options);
        extended.add(new Option(value, label, description));
        return new SelectMenu(
                action, placeholder, extended, selected, minValues, maxValues, disabled, params);
    }

    /**
     * Marks choices as already chosen when the menu is first shown.
     *
     * <p>Every value must be one of the options: a default that matches nothing is dropped
     * by Discord without a word, so a typo here would look like a menu that ignored the
     * caller.
     *
     * <p>The count is not checked against {@link #range(int, int)}, because a select is
     * immutable and either call may come first, and a check would then depend on the order
     * a menu author happened to write. Discord enforces the pairing when the select is
     * built, and the menu author will meet that in their own test before any user does.
     *
     * @param values the values to show as chosen
     * @return a copy of this select with those defaults
     * @throws NullPointerException if the array or any element is null
     * @throws IllegalArgumentException if a value is not one of the options
     */
    public SelectMenu selected(String... values) {
        Objects.requireNonNull(values, "values");
        for (String value : values) {
            Objects.requireNonNull(value, "selected value");
            if (!has(options, value)) {
                throw new IllegalArgumentException(
                        "Selected value is not one of the options: " + value);
            }
        }
        return new SelectMenu(
                action,
                placeholder,
                options,
                List.of(values),
                minValues,
                maxValues,
                disabled,
                params);
    }

    /**
     * How many values the user must choose, and may choose.
     *
     * @param min the fewest, from 0; zero means the choice is optional
     * @param max the most, up to {@link Limits#MAX_SELECT_OPTIONS}
     * @return a copy of this select with that range
     * @throws IllegalArgumentException if the range is negative, inverted, or longer
     *     than Discord allows
     */
    public SelectMenu range(int min, int max) {
        if (min < 0 || max < 1 || min > max || max > Limits.MAX_SELECT_OPTIONS) {
            throw new IllegalArgumentException(
                    "A select range must satisfy 0 <= min <= max <= %d, got [%d, %d]"
                            .formatted(Limits.MAX_SELECT_OPTIONS, min, max));
        }
        return new SelectMenu(action, placeholder, options, selected, min, max, disabled, params);
    }

    /**
     * Greys the select out so it cannot be used.
     *
     * @param disabled whether the select is disabled
     * @return a copy of this select
     */
    public SelectMenu disabled(boolean disabled) {
        return new SelectMenu(
                action, placeholder, options, selected, minValues, maxValues, disabled, params);
    }

    /**
     * Appends parameters to the encoded action id.
     *
     * @param params the parameters
     * @return a copy of this select
     * @throws NullPointerException if the array or any element is null
     */
    public SelectMenu params(String... params) {
        Objects.requireNonNull(params, "params");
        return new SelectMenu(
                action, placeholder, options, selected, minValues, maxValues, disabled, params);
    }

    @Override
    public ActionRowChildComponent render(MenuContext ctx) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException(
                    "Select '%s' has no options, and Discord rejects a select with none"
                            .formatted(action));
        }
        StringSelectMenu.Builder builder =
                StringSelectMenu.create(ComponentId.encode(ctx.menuId(), action, params))
                        .setPlaceholder(placeholder)
                        .setRequiredRange(minValues, maxValues)
                        .setDisabled(disabled);
        for (Option option : options) {
            builder.addOption(option.label(), option.value(), option.description(), null);
        }
        if (!selected.isEmpty()) {
            builder.setDefaultValues(selected);
        }
        return builder.build();
    }

    private static boolean has(List<Option> options, String value) {
        for (Option option : options) {
            if (option.value().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("The select " + name + " must not be empty");
        }
    }

    private static void requireLength(String value, int limit, String name) {
        if (value.length() > limit) {
            throw new IllegalArgumentException(
                    "The select %s is %d characters, the limit is %d"
                            .formatted(name, value.length(), limit));
        }
    }
}
