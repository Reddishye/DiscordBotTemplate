package es.redactado.menu.simple;

import es.redactado.menu.api.Limits;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects the options of a select, and what starts out chosen.
 *
 * <p>Used as the middle argument of {@link ViewBuilder#select}, which is why it exists rather
 * than being four more parameters:
 *
 * <pre>{@code
 * .select("section", Msg.key("help.pick"), o -> o
 *     .option("rules", Msg.key("help.rules"))
 *     .option("faq", Msg.key("help.faq"), "The questions people ask most"),
 *     pick -> pick.go(pick.values().getFirst()))
 * }</pre>
 *
 * <p>Mutable and not thread-safe; part of the one pass through the DSL.
 */
public final class SelectSpec {

    /** One option, kept until the whole select is built. */
    record Option(String value, String label, String description) {}

    private final List<Option> options = new ArrayList<>();
    private List<String> selected = List.of();
    private int min;
    private int max = 0;

    /**
     * Adds an option.
     *
     * @param value what a handler reads back from {@link Pick#values()}
     * @param label the text shown
     * @return this spec
     */
    public SelectSpec option(String value, String label) {
        return option(value, label, null);
    }

    /**
     * Adds an option with a line under it.
     *
     * @param value what a handler reads back
     * @param label the text shown
     * @param description the line under the label, or null for none
     * @return this spec
     */
    public SelectSpec option(String value, String label, String description) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("A select option needs a value");
        }
        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException("Select option '" + value + "' needs a label");
        }
        options.add(new Option(value, label, description));
        return this;
    }

    /**
     * What is chosen when the view opens.
     *
     * <p>Values that no option declares are dropped by the component rather than sent to
     * Discord, which rejects a selected value outside the option list.
     *
     * @param values the values to start with
     * @return this spec
     */
    public SelectSpec selected(String... values) {
        this.selected = values == null ? List.of() : List.of(values);
        return this;
    }

    /**
     * How many options may be chosen at once, for a multiple select.
     *
     * <p>A range of one is a single choice, which is what most selects want, and is why the
     * component needs to be told rather than infer it.
     *
     * @param min the fewest
     * @param max the most
     * @return this spec
     * @throws IllegalArgumentException if the range is empty or out of order
     */
    public SelectSpec range(int min, int max) {
        if (min < 0 || max < min) {
            throw new IllegalArgumentException(
                    ("A select range must be zero or more and not inverted, got %d to %d")
                            .formatted(min, max));
        }
        this.min = min;
        this.max = max;
        return this;
    }

    /**
     * The checks that need every option in front of it.
     *
     * <p>Done where the select is declared rather than at render, because each of these is a
     * message the user would otherwise only see as a rejected component.
     *
     * @param where a description of the select, for the message
     * @throws IllegalArgumentException if the select could not render
     */
    void check(String where) {
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Select " + where + " has no options");
        }
        if (options.size() > Limits.MAX_SELECT_OPTIONS) {
            throw new IllegalArgumentException(
                    ("Select %s declares %d options, Discord accepts at most %d")
                            .formatted(where, options.size(), Limits.MAX_SELECT_OPTIONS));
        }
        for (String value : selected) {
            boolean declared = false;
            for (Option option : options) {
                if (option.value().equals(value)) {
                    declared = true;
                    break;
                }
            }
            if (!declared) {
                throw new IllegalArgumentException(
                        ("Select %s starts on value '%s', which no option declares; Discord"
                                        + " rejects a selected value outside the option list")
                                .formatted(where, value));
            }
        }
    }

    /** The options, in declaration order. */
    List<Option> options() {
        return List.copyOf(options);
    }

    /** The values to start with. */
    List<String> selected() {
        return selected;
    }

    /** The fewest that may be chosen. */
    int min() {
        return min;
    }

    /** The most that may be chosen, or zero for no maximum. */
    int max() {
        return max;
    }
}
