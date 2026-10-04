package es.redactado.menu.preset;

import static es.redactado.menu.preset.IconKey.BACK;
import static es.redactado.menu.preset.IconKey.CANCEL;
import static es.redactado.menu.preset.IconKey.CLOSE;
import static es.redactado.menu.preset.IconKey.CONFIRM;
import static es.redactado.menu.preset.IconKey.DELETE;
import static es.redactado.menu.preset.IconKey.EDIT;
import static es.redactado.menu.preset.IconKey.ERROR;
import static es.redactado.menu.preset.IconKey.INFO;
import static es.redactado.menu.preset.IconKey.LINK;
import static es.redactado.menu.preset.IconKey.NEXT;
import static es.redactado.menu.preset.IconKey.OK;
import static es.redactado.menu.preset.IconKey.PREVIOUS;
import static es.redactado.menu.preset.IconKey.SETTINGS;
import static es.redactado.menu.preset.IconKey.USER;
import static es.redactado.menu.preset.IconKey.WARN;

import java.util.EnumMap;
import java.util.Map;

/**
 * The values every preset starts from.
 *
 * <p>They live here rather than being read back off {@link BuiltinPresets#DEFAULT}
 * because {@code BuiltinPresets} builds its presets through
 * {@link Preset#builder(String)}. Reading them back would make the two classes
 * initialise each other, and whichever lost that race would see null.
 *
 * <p>These are also the values of the {@code default} preset, which is why
 * {@link Preset#builder(String)} and that preset cannot drift apart: the
 * {@code default} preset is built from them and overrides nothing.
 *
 * <p>Emoji are written as code points so this file stays plain ASCII.
 */
final class DefaultLook {

    /** The description the {@code default} preset carries. */
    static final String DESCRIPTION = "Discord's own colours with the standard status icons.";

    static final Palette PALETTE =
            new Palette(0x5865F2, 0x57F287, 0xFEE75C, 0xED4245, 0x5865F2, 0x4F545C);
    static final Icons ICONS = icons();
    static final Density DENSITY = Density.NORMAL;
    static final DividerStyle DIVIDER = new DividerStyle(true, Gap.SMALL);
    static final HeaderStyle HEADER = new HeaderStyle(3, false);
    static final ButtonStyles BUTTONS = ButtonStyles.identity();
    static final String FOOTER = "";

    private DefaultLook() {}

    private static final String VS16 = "\uFE0F";

    private static Icons icons() {
        Map<IconKey, String> icons = new EnumMap<>(IconKey.class);
        icons.put(OK, plain(0x2705));
        icons.put(ERROR, plain(0x274C));
        icons.put(WARN, emoji(0x26A0));
        icons.put(INFO, emoji(0x2139));
        icons.put(BACK, emoji(0x25C0));
        icons.put(NEXT, emoji(0x25B6));
        icons.put(PREVIOUS, emoji(0x25C0));
        icons.put(CLOSE, emoji(0x2716));
        icons.put(CONFIRM, plain(0x2705));
        icons.put(CANCEL, plain(0x274C));
        icons.put(EDIT, emoji(0x270F));
        icons.put(DELETE, emoji(0x1F5D1));
        icons.put(LINK, plain(0x1F517));
        icons.put(USER, plain(0x1F464));
        icons.put(SETTINGS, emoji(0x2699));
        return Icons.of(icons);
    }

    private static String plain(int codePoint) {
        return Character.toString(codePoint);
    }

    private static String emoji(int codePoint) {
        return Character.toString(codePoint) + VS16;
    }
}
