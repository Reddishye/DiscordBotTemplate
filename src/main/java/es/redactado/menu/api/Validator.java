package es.redactado.menu.api;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates a Container against Discord component limits before sending. */
public class Validator {
    private static final Logger LOG = LoggerFactory.getLogger(Validator.class);

    private Validator() {}

    /**
     * Validates a built container against Discord's documented limits.
     *
     * @param container the container a menu rendered
     * @return the warnings and errors found; never null
     */
    public static ValidationResult validate(Container container) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        var children = container.getComponents();

        int childCount = children.size();
        if (childCount > Limits.MAX_CONTAINER_CHILDREN) {
            errors.add(
                    "Container has %d children, max is %d"
                            .formatted(childCount, Limits.MAX_CONTAINER_CHILDREN));
        } else if (childCount > Limits.WARN_CONTAINER_CHILDREN) {
            warnings.add(
                    "Container has %d children (limit %d)"
                            .formatted(childCount, Limits.MAX_CONTAINER_CHILDREN));
        }

        // Counted only to make the debug line below say something useful: a container of
        // ten sections and one of ten text lines use very different space and look identical
        // in a child count.
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

        return new ValidationResult(warnings, errors);
    }

    /**
     * Validates a container, logs its warnings and throws on its errors.
     *
     * @param container the container to check
     * @return the same container, for chaining
     * @throws ComponentLimitException if anything failed
     */
    public static Container verify(Container container) {
        var result = validate(container);
        result.warnings().forEach(w -> LOG.warn("Menu validation warning: {}", w));
        result.throwIfInvalid();
        return container;
    }
}
