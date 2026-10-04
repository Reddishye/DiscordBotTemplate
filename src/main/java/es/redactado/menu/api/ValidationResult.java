package es.redactado.menu.api;

import java.util.Collections;
import java.util.List;

/**
 * What {@link Validator} found in a rendered container.
 *
 * <p>Two lists and a verdict, so it is a record-shaped value rather than something to build
 * up: a caller accumulates warnings and errors in lists of its own and constructs this once.
 * An earlier version carried a builder for two fields, which was a class to maintain rather
 * than a convenience.
 *
 * <p>A warning is advice: the container is legal but close to a limit. An error is not legal,
 * and {@link #throwIfInvalid()} turns one into an exception.
 */
public final class ValidationResult {

    private final List<String> warnings;
    private final List<String> errors;

    /**
     * Creates a result.
     *
     * @param warnings the advice, may be empty
     * @param errors the failures, may be empty
     */
    ValidationResult(List<String> warnings, List<String> errors) {
        this.warnings = Collections.unmodifiableList(warnings);
        this.errors = Collections.unmodifiableList(errors);
    }

    /**
     * Whether the container is legal.
     *
     * @return true when nothing failed
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * The advice, for a caller that wants to log or count it.
     *
     * @return the warnings, possibly empty
     */
    public List<String> warnings() {
        return warnings;
    }

    /**
     * The failures.
     *
     * @return the errors, possibly empty
     */
    public List<String> errors() {
        return errors;
    }

    /**
     * Throws if anything failed.
     *
     * @throws ComponentLimitException carrying every error as one message
     */
    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new ComponentLimitException(errors.size(), 0, String.join("; ", errors));
        }
    }
}
