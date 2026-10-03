package es.redactado.menu.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of validating a container against Discord limits. */
public class ValidationResult {
    private final List<String> warnings;
    private final List<String> errors;
    private final boolean valid;

    private ValidationResult(List<String> warnings, List<String> errors, boolean valid) {
        this.warnings = Collections.unmodifiableList(warnings);
        this.errors = Collections.unmodifiableList(errors);
        this.valid = valid;
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of(), List.of(), true);
    }

    public static ValidationResult warn(String msg) {
        return new ValidationResult(List.of(msg), List.of(), true);
    }

    public static ValidationResult error(String msg) {
        return new ValidationResult(List.of(), List.of(msg), false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isValid() {
        return valid;
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> errors() {
        return errors;
    }

    public void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new es.redactado.menu.api.ComponentLimitException(
                    errors.size(), 0, String.join("; ", errors));
        }
    }

    public static class Builder {
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public Builder warn(String msg) {
            warnings.add(msg);
            return this;
        }

        public Builder error(String msg) {
            errors.add(msg);
            return this;
        }

        public ValidationResult build() {
            return new ValidationResult(warnings, errors, errors.isEmpty());
        }
    }
}
