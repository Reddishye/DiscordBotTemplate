package es.redactado.menu.preset;

import static es.redactado.menu.preset.IconKey.DELETE;
import static es.redactado.menu.preset.IconKey.EDIT;
import static es.redactado.menu.preset.IconKey.LINK;
import static es.redactado.menu.preset.IconKey.SETTINGS;
import static es.redactado.menu.preset.IconKey.USER;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

/**
 * The presets every bot starts with.
 *
 * <p>Emoji are written as code points rather than literal characters so this file
 * stays plain ASCII. Sources that contain emoji are hard to review, easy to mangle
 * through an editor, and impossible to read in a diff.
 *
 * <p>The values shared with {@link Preset#builder(String)} live in
 * {@link DefaultLook}, which is also what stops the two classes from initialising
 * each other.
 */
public final class BuiltinPresets {

    private static final String VS16 = "\uFE0F";

    /**
     * The default look: Discord's own blurple with semantic status colours.
     *
     * <p>Built from the builder and overriding nothing, which is what makes it the
     * definition of the builder defaults rather than a copy of them.
     */
    public static final Preset DEFAULT = Preset.builder("default").build();

    /** Flat and quiet: one colour, no icons, no dividers, compact spacing. */
    public static final Preset MINIMAL =
            Preset.builder("minimal")
                    .description("A flat single-colour look with no icons and compact spacing.")
                    .palette(
                            new Palette(0x99AAB5, 0x99AAB5, 0x99AAB5, 0xED4245, 0x99AAB5, 0x99AAB5))
                    .icons(Icons.none())
                    .density(Density.COMPACT)
                    .divider(new DividerStyle(false, Gap.SMALL))
                    .header(new HeaderStyle(3, false))
                    .buttons(
                            ButtonStyles.identity()
                                    .with(ButtonRole.PRIMARY, ButtonStyle.SECONDARY)
                                    .with(ButtonRole.SUCCESS, ButtonStyle.SECONDARY))
                    .footer("")
                    .build();

    /** Darker and roomier, with a subtitle and a footer naming the menu. */
    public static final Preset MIDNIGHT =
            Preset.builder("midnight")
                    .description("A dark, spacious look with a subtitle and a named footer.")
                    .palette(
                            new Palette(0x7C83FD, 0x3DDC97, 0xF5A623, 0xFF5A5F, 0x4EA8DE, 0x3A3F4B))
                    .icons(DefaultLook.ICONS)
                    .density(Density.NORMAL)
                    .divider(new DividerStyle(true, Gap.LARGE))
                    .header(new HeaderStyle(2, true))
                    .buttons(ButtonStyles.identity())
                    .footer("{menu}")
                    .build();

    /** Loud and playful, with big headings and a full icon set. */
    public static final Preset VIBRANT =
            Preset.builder("vibrant")
                    .description("A bright, playful look with big headings and lively icons.")
                    .palette(
                            new Palette(0xFF4F9A, 0x00D26A, 0xFFC400, 0xFF3B30, 0x00B8FF, 0x6B21A8))
                    .icons(vibrantIcons())
                    .density(Density.COMFORTABLE)
                    .divider(new DividerStyle(true, Gap.LARGE))
                    .header(new HeaderStyle(1, true))
                    .buttons(ButtonStyles.identity())
                    .footer("")
                    .build();

    /** Every button and icon in one shade, so colour carries no meaning. */
    public static final Preset MONOCHROME =
            Preset.builder("monochrome")
                    .description("A greyscale look where shape carries meaning instead of colour.")
                    .palette(
                            new Palette(0xE0E0E0, 0xBDBDBD, 0x9E9E9E, 0x757575, 0xBDBDBD, 0x616161))
                    .icons(monochromeIcons())
                    .density(Density.NORMAL)
                    .divider(new DividerStyle(true, Gap.SMALL))
                    .header(new HeaderStyle(2, false))
                    .buttons(
                            ButtonStyles.identity()
                                    .with(ButtonRole.PRIMARY, ButtonStyle.SECONDARY)
                                    .with(ButtonRole.SUCCESS, ButtonStyle.SECONDARY)
                                    .with(ButtonRole.DANGER, ButtonStyle.SECONDARY))
                    .footer("")
                    .build();

    private static final List<Preset> ALL =
            List.of(DEFAULT, MINIMAL, MIDNIGHT, VIBRANT, MONOCHROME);

    private BuiltinPresets() {}

    /**
     * Every built-in preset, in a fixed order.
     *
     * @return an unmodifiable list
     */
    public static List<Preset> all() {
        return ALL;
    }

    /**
     * Finds a built-in by name.
     *
     * @param name the preset name
     * @return the preset, or empty when no built-in has that name
     */
    public static Optional<Preset> find(String name) {
        return ALL.stream().filter(preset -> preset.name().equals(name)).findFirst();
    }

    private static Icons vibrantIcons() {
        Map<IconKey, String> icons = new EnumMap<>(IconKey.class);
        icons.put(IconKey.OK, emoji(0x2728));
        icons.put(IconKey.ERROR, plain(0x1F525));
        icons.put(IconKey.WARN, emoji(0x26A1));
        icons.put(IconKey.INFO, emoji(0x1F680));
        icons.put(IconKey.BACK, plain(0x1F44D));
        icons.put(IconKey.NEXT, plain(0x1F44D));
        icons.put(IconKey.PREVIOUS, plain(0x1F44E));
        icons.put(IconKey.CLOSE, emoji(0x1F389));
        icons.put(IconKey.CONFIRM, emoji(0x1F44C));
        icons.put(IconKey.CANCEL, emoji(0x1F44E));
        icons.put(EDIT, emoji(0x1F4DD));
        icons.put(DELETE, emoji(0x1F5D1));
        icons.put(LINK, emoji(0x1F516));
        icons.put(USER, emoji(0x1F44B));
        icons.put(SETTINGS, emoji(0x1F514));
        return Icons.of(icons);
    }

    private static Icons monochromeIcons() {
        Map<IconKey, String> icons = new EnumMap<>(IconKey.class);

        // U+2B1C white large square, and U+2B1B black large square, carry the
        // difference between the two families; the smaller squares are used where
        // a lighter mark reads better.
        icons.put(IconKey.OK, plain(0x2B1C));
        icons.put(IconKey.CONFIRM, plain(0x2B1C));
        icons.put(IconKey.INFO, plain(0x25AA));

        icons.put(IconKey.ERROR, plain(0x2B1B));
        icons.put(IconKey.CANCEL, plain(0x2B1B));
        icons.put(IconKey.CLOSE, plain(0x2B1B));
        icons.put(IconKey.WARN, emoji(0x25AB));

        icons.put(IconKey.BACK, plain(0x25AA));
        icons.put(IconKey.PREVIOUS, plain(0x25AA));
        icons.put(IconKey.NEXT, plain(0x25AB));
        icons.put(IconKey.EDIT, plain(0x2B1C));
        icons.put(IconKey.DELETE, plain(0x2B1B));
        icons.put(IconKey.LINK, plain(0x2B1C));
        icons.put(IconKey.USER, plain(0x2B1B));
        icons.put(SETTINGS, plain(0x2B1C));

        return Icons.of(icons);
    }

    /** Renders a code point with no variation selector. */
    private static String plain(int codePoint) {
        return Character.toString(codePoint);
    }

    /** Renders a code point with the emoji variation selector appended. */
    private static String emoji(int codePoint) {
        return Character.toString(codePoint) + VS16;
    }
}
