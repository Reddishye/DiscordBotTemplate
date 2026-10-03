package es.redactado.menu.preset;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An immutable description of how every menu in a bot looks.
 *
 * <p>A preset is data. Menus read it at render time and never mutate it, so one
 * instance is shared by every interaction without synchronisation. Swapping the
 * whole look of a bot means swapping which preset a menu resolves, not editing
 * component code.
 *
 * <p>Every field is validated on construction, so an invalid preset cannot exist.
 * That matters most for the name and footer: both end up in Discord-facing output
 * and in file names, and a value that breaks either would fail much later and
 * further from its cause.
 *
 * <p>Use {@link #builder(String)} to construct one incrementally, or
 * {@link #toBuilder()} to vary an existing preset.
 *
 * @param name the identifier, unique within a registry
 * @param description one short sentence describing the look
 * @param palette the colours
 * @param icons the icon set
 * @param density how much space between sections
 * @param divider how section breaks are drawn
 * @param header how the menu titles itself
 * @param buttons how each button role is rendered
 * @param footer an optional line beneath the menu, or an empty string for none
 */
public record Preset(
        String name,
        String description,
        Palette palette,
        Icons icons,
        Density density,
        DividerStyle divider,
        HeaderStyle header,
        ButtonStyles buttons,
        String footer) {

    /** Lowercase letters, digits, underscore, and dash; must start with a letter or digit. */
    public static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    /** Longest description accepted. */
    public static final int MAX_DESCRIPTION_LENGTH = 120;

    /** Longest footer accepted. */
    public static final int MAX_FOOTER_LENGTH = 200;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");

    public Preset {
        name = validateName(name);
        description = validateDescription(name, description);
        Objects.requireNonNull(palette, name + ": palette must not be null");
        Objects.requireNonNull(icons, name + ": icons must not be null");
        Objects.requireNonNull(density, name + ": density must not be null");
        Objects.requireNonNull(divider, name + ": divider must not be null");
        Objects.requireNonNull(header, name + ": header must not be null");
        Objects.requireNonNull(buttons, name + ": buttons must not be null");
        footer = validateFooter(name, footer);
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "preset name '%s' must match %s".formatted(name, NAME_PATTERN.pattern()));
        }
        return name;
    }

    private static String validateDescription(String name, String description) {
        Objects.requireNonNull(description, name + ": description must not be null");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "preset '%s': description is %d characters, the limit is %d"
                            .formatted(name, description.length(), MAX_DESCRIPTION_LENGTH));
        }
        return description;
    }

    private static String validateFooter(String name, String footer) {
        Objects.requireNonNull(footer, name + ": footer must not be null");
        if (footer.length() > MAX_FOOTER_LENGTH) {
            throw new IllegalArgumentException(
                    "preset '%s': footer is %d characters, the limit is %d"
                            .formatted(name, footer.length(), MAX_FOOTER_LENGTH));
        }
        Matcher matcher = PLACEHOLDER.matcher(footer);
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!key.equals("user") && !key.equals("menu")) {
                throw new IllegalArgumentException(
                        "preset '%s': footer placeholder '%s' is not supported, only {user} and {menu} are"
                                .formatted(name, "{" + key + "}"));
            }
        }
        return footer;
    }

    /**
     * Starts a preset with the same defaults as the built-in {@code default} preset.
     *
     * @param name the identifier for the new preset
     * @return a mutable builder
     */
    public static Builder builder(String name) {
        return new Builder(
                name,
                DefaultLook.DESCRIPTION,
                DefaultLook.PALETTE,
                DefaultLook.ICONS,
                DefaultLook.DENSITY,
                DefaultLook.DIVIDER,
                DefaultLook.HEADER,
                DefaultLook.BUTTONS,
                DefaultLook.FOOTER);
    }

    /**
     * A builder positioned to change this preset.
     *
     * @return a mutable builder pre-filled with this preset's values
     */
    public Builder toBuilder() {
        return new Builder(
                name, description, palette, icons, density, divider, header, buttons, footer);
    }

    /**
     * Builds a {@link Preset} one field at a time.
     *
     * <p>Mutable and not thread-safe, which is the normal contract for a builder: it
     * is filled on one thread, typically at construction, and then discarded. The
     * {@link Preset} it produces is immutable and safe to share.
     */
    public static final class Builder {

        private String name;
        private String description;
        private Palette palette;
        private Icons icons;
        private Density density;
        private DividerStyle divider;
        private HeaderStyle header;
        private ButtonStyles buttons;
        private String footer;

        private Builder(
                String name,
                String description,
                Palette palette,
                Icons icons,
                Density density,
                DividerStyle divider,
                HeaderStyle header,
                ButtonStyles buttons,
                String footer) {
            this.name = name;
            this.description = description;
            this.palette = palette;
            this.icons = icons;
            this.density = density;
            this.divider = divider;
            this.header = header;
            this.buttons = buttons;
            this.footer = footer;
        }

        /**
         * Sets the identifier.
         *
         * @param name the identifier
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description.
         *
         * @param description one short sentence
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the colours.
         *
         * @param palette the colours
         * @return this builder
         */
        public Builder palette(Palette palette) {
            this.palette = palette;
            return this;
        }

        /**
         * Sets the icon set.
         *
         * @param icons the icons
         * @return this builder
         */
        public Builder icons(Icons icons) {
            this.icons = icons;
            return this;
        }

        /**
         * Sets the spacing.
         *
         * @param density the density
         * @return this builder
         */
        public Builder density(Density density) {
            this.density = density;
            return this;
        }

        /**
         * Sets the divider treatment.
         *
         * @param divider the divider style
         * @return this builder
         */
        public Builder divider(DividerStyle divider) {
            this.divider = divider;
            return this;
        }

        /**
         * Sets the header treatment.
         *
         * @param header the header style
         * @return this builder
         */
        public Builder header(HeaderStyle header) {
            this.header = header;
            return this;
        }

        /**
         * Sets the button mapping.
         *
         * @param buttons the button styles
         * @return this builder
         */
        public Builder buttons(ButtonStyles buttons) {
            this.buttons = buttons;
            return this;
        }

        /**
         * Sets the footer line.
         *
         * @param footer the footer, or an empty string for none
         * @return this builder
         */
        public Builder footer(String footer) {
            this.footer = footer;
            return this;
        }

        /**
         * Builds the preset.
         *
         * @return the immutable preset
         * @throws IllegalArgumentException if any field is invalid
         */
        public Preset build() {
            return new Preset(
                    name, description, palette, icons, density, divider, header, buttons, footer);
        }
    }
}
