package es.redactado.menu.preset;

/**
 * How a menu draws the line between sections.
 *
 * @param visible whether a divider is drawn at all
 * @param gap the spacing around it
 */
public record DividerStyle(boolean visible, Gap gap) {

    public DividerStyle {
        if (gap == null) {
            throw new IllegalArgumentException("divider gap must not be null");
        }
    }
}
