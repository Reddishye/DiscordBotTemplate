package es.redactado.menu.view;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates a Container against Discord component limits before sending. */
public class Validator {
    private static final Logger LOG = LoggerFactory.getLogger(Validator.class);

    private Validator() {}

    /** Validate a built Container. Logs warnings, throws on hard limits. */
    public static ValidationResult validate(Container container) {
        var result = ValidationResult.builder();
        var children = container.getComponents();

        int childCount = children.size();
        if (childCount > Limits.MAX_CONTAINER_CHILDREN) {
            result.error(
                    "Container has %d children, max is %d"
                            .formatted(childCount, Limits.MAX_CONTAINER_CHILDREN));
        } else if (childCount > Limits.WARN_CONTAINER_CHILDREN) {
            result.warn(
                    "Container has %d children (limit %d)"
                            .formatted(childCount, Limits.MAX_CONTAINER_CHILDREN));
        }

        // Count special types that consume more visual space
        int sectionCount = 0;
        int actionRowCount = 0;
        for (var child : children) {
            if (child instanceof Section) sectionCount++;
            if (child instanceof ActionRow) actionRowCount++;
        }

        String detail =
                "%d children (%d sections, %d actionRows)"
                        .formatted(childCount, sectionCount, actionRowCount);
        LOG.debug("Container validation: {}", detail);

        return result.build();
    }

    /** Convenience: validate and warn/throw. Returns container for chaining. */
    public static Container verify(Container container) {
        var result = validate(container);
        result.warnings().forEach(w -> LOG.warn("Menu validation warning: {}", w));
        result.throwIfInvalid();
        return container;
    }
}
