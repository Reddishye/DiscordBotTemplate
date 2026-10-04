package es.redactado.menu.preset;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

/**
 * Maps each {@link ButtonRole} to the JDA style it renders as.
 *
 * <p>Separating meaning from style is what lets a monochrome preset render a danger
 * button as a secondary one, without any menu author changing their code.
 *
 * <p>Immutable.
 */
public final class ButtonStyles {

    private static final ButtonStyles IDENTITY = new ButtonStyles(Map.of());

    private final Map<ButtonRole, ButtonStyle> styles;

    private ButtonStyles(Map<ButtonRole, ButtonStyle> styles) {
        // EnumMap's copy constructor infers the key type from the argument, which
        // throws for an empty map, so the target type is named explicitly instead.
        EnumMap<ButtonRole, ButtonStyle> copy = new EnumMap<>(ButtonRole.class);
        copy.putAll(styles);
        this.styles = Collections.unmodifiableMap(copy);
    }

    /**
     * The mapping that uses the JDA style of the same name as each role.
     *
     * @return the identity mapping
     */
    public static ButtonStyles identity() {
        return IDENTITY;
    }

    /**
     * Returns a copy with one role overridden.
     *
     * @param role the meaning
     * @param style the JDA style to render it as
     * @return a new mapping, leaving this one unchanged
     * @throws IllegalArgumentException if the style is {@code LINK} or
     *     {@code UNKNOWN}; neither is a valid action-button style, and a link
     *     button carries a URL instead of a custom id
     * @throws NullPointerException if either argument is null
     */
    public ButtonStyles with(ButtonRole role, ButtonStyle style) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(style, "style");
        requireUsable(style);
        Map<ButtonRole, ButtonStyle> copy = new EnumMap<>(ButtonRole.class);
        copy.putAll(styles);
        copy.put(role, style);
        return new ButtonStyles(copy);
    }

    /**
     * The JDA style for a role.
     *
     * @param role the meaning
     * @return the style to render it as
     * @throws NullPointerException if {@code role} is null
     */
    public ButtonStyle of(ButtonRole role) {
        Objects.requireNonNull(role, "role");
        ButtonStyle style = styles.get(role);
        return style != null ? style : identityFor(role);
    }

    private static ButtonStyle identityFor(ButtonRole role) {
        return switch (role) {
            case PRIMARY -> ButtonStyle.PRIMARY;
            case SECONDARY -> ButtonStyle.SECONDARY;
            case SUCCESS -> ButtonStyle.SUCCESS;
            case DANGER -> ButtonStyle.DANGER;
        };
    }

    private static void requireUsable(ButtonStyle style) {
        if (style == ButtonStyle.LINK) {
            throw new IllegalArgumentException(
                    "LINK is not a valid button role style; a link button opens a URL");
        }
        if (style == ButtonStyle.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN is not a usable button style");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ButtonStyles that && styles.equals(that.styles);
    }

    @Override
    public int hashCode() {
        return styles.hashCode();
    }

    @Override
    public String toString() {
        return "ButtonStyles" + styles;
    }
}
