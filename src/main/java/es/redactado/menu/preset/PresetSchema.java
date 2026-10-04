package es.redactado.menu.preset;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The JSON shape a preset file has to have, and the checks that say so.
 *
 * <p>Kept apart from {@link PresetLoader} because it is a different job with a different
 * answer: the loader reports what it found, this reports what is wrong with it. A preset
 * file is user-editable, so these checks are the boundary between a typo and a bot that
 * cannot render.
 *
 * <p>Every method returns the first problem it finds as a message, or null when the node is
 * acceptable, because one clear sentence naming the offending key is worth more than a list
 * of everything that is also wrong.
 */
final class PresetSchema {

    private PresetSchema() {}

    static final Set<String> ROOT_KEYS =
            Set.of(
                    "name",
                    "extends",
                    "description",
                    "palette",
                    "icons",
                    "density",
                    "divider",
                    "header",
                    "buttons",
                    "footer");

    static final Set<String> PALETTE_KEYS =
            Set.of("accent", "success", "warning", "danger", "info", "neutral");

    static final Set<String> DIVIDER_KEYS = Set.of("visible", "gap");
    static final Set<String> HEADER_KEYS = Set.of("level", "subtitle");
    static final Set<String> BUTTON_KEYS = Set.of("primary", "secondary", "success", "danger");

    static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /**
     * Jackson is created once. Reusing the factory keeps its internal buffers, and a
     * reload of two hundred files should not allocate two hundred parsers.
     */
    static <E extends Enum<E>> E parseEnum(String word, Class<E> type) {
        if (word == null) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(word)) {
                return constant;
            }
        }
        return null;
    }

    static <E extends Enum<E>> String wordList(Class<E> type) {
        List<String> words = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            words.add(constant.name().toLowerCase(Locale.ROOT));
        }
        return String.join("|", words);
    }

    static String validate(JsonNode root, String baseName, Set<String> builtinNames) {
        String unknown = firstUnknown(root, ROOT_KEYS, "");
        if (unknown != null) {
            return unknown;
        }

        JsonNode nameNode = root.get("name");
        if (nameNode == null) {
            return "name: is required";
        }
        if (!nameNode.isTextual()) {
            return "name: expected a string, got " + PresetLoader.kind(nameNode);
        }
        String name = nameNode.textValue();
        if (!Preset.NAME_PATTERN.matcher(name).matches()) {
            return "name: expected %s, got '%s'".formatted(Preset.NAME_PATTERN.pattern(), name);
        }
        if (!name.equals(baseName)) {
            return "name: is '%s' but the file is named '%s.json'".formatted(name, baseName);
        }
        if (builtinNames.contains(name)) {
            return "name: '%s' is a built-in preset".formatted(name);
        }

        JsonNode extendsNode = root.get("extends");
        if (extendsNode != null && !extendsNode.isTextual()) {
            return "extends: expected a string, got " + PresetLoader.kind(extendsNode);
        }

        JsonNode description = root.get("description");
        if (description != null) {
            if (!description.isTextual()) {
                return "description: expected a string, got " + PresetLoader.kind(description);
            }
            if (description.textValue().length() > Preset.MAX_DESCRIPTION_LENGTH) {
                return "description: is %d characters, the limit is %d"
                        .formatted(description.textValue().length(), Preset.MAX_DESCRIPTION_LENGTH);
            }
        }

        JsonNode footer = root.get("footer");
        if (footer != null) {
            if (!footer.isTextual()) {
                return "footer: expected a string, got " + PresetLoader.kind(footer);
            }
            String value = footer.textValue();
            if (value.length() > Preset.MAX_FOOTER_LENGTH) {
                return "footer: is %d characters, the limit is %d"
                        .formatted(value.length(), Preset.MAX_FOOTER_LENGTH);
            }
            String unsupported = Preset.unsupportedPlaceholder(value);
            if (unsupported != null) {
                return "footer: placeholder '%s' is not supported, only {user} and {menu} are"
                        .formatted(unsupported);
            }
        }

        String paletteProblem = validateObject(root.get("palette"), PALETTE_KEYS, "palette");
        if (paletteProblem != null) {
            return paletteProblem;
        }
        JsonNode palette = root.get("palette");
        if (palette != null) {
            var it = palette.properties().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                JsonNode value = entry.getValue();
                String path = "palette." + entry.getKey();
                if (!value.isTextual()) {
                    return "%s: expected #RRGGBB, got %s".formatted(path, PresetLoader.kind(value));
                }
                if (!COLOR.matcher(value.textValue()).matches()) {
                    return "%s: expected #RRGGBB, got '%s'".formatted(path, value.textValue());
                }
            }
        }

        String densityProblem = validateEnum(root.get("density"), Density.class, "density");
        if (densityProblem != null) {
            return densityProblem;
        }

        String dividerProblem = validateObject(root.get("divider"), DIVIDER_KEYS, "divider");
        if (dividerProblem != null) {
            return dividerProblem;
        }
        JsonNode divider = root.get("divider");
        if (divider != null) {
            JsonNode visible = divider.get("visible");
            if (visible != null && !visible.isBoolean()) {
                return "divider.visible: expected true or false, got " + PresetLoader.kind(visible);
            }
            String gapProblem = validateEnum(divider.get("gap"), Gap.class, "divider.gap");
            if (gapProblem != null) {
                return gapProblem;
            }
        }

        String headerProblem = validateObject(root.get("header"), HEADER_KEYS, "header");
        if (headerProblem != null) {
            return headerProblem;
        }
        JsonNode header = root.get("header");
        if (header != null) {
            JsonNode level = header.get("level");
            if (level != null) {
                if (!level.isInt()) {
                    return "header.level: expected a number, got " + PresetLoader.kind(level);
                }
                if (level.intValue() < HeaderStyle.MIN_LEVEL
                        || level.intValue() > HeaderStyle.MAX_LEVEL) {
                    return "header.level: expected %d..%d, got %d"
                            .formatted(
                                    HeaderStyle.MIN_LEVEL, HeaderStyle.MAX_LEVEL, level.intValue());
                }
            }
            JsonNode subtitle = header.get("subtitle");
            if (subtitle != null && !subtitle.isBoolean()) {
                return "header.subtitle: expected true or false, got "
                        + PresetLoader.kind(subtitle);
            }
        }

        String buttonsProblem = validateButtons(root.get("buttons"));
        if (buttonsProblem != null) {
            return buttonsProblem;
        }

        String iconsProblem = validateIcons(root.get("icons"));
        if (iconsProblem != null) {
            return iconsProblem;
        }
        return null;
    }

    static String validateObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return "%s: expected an object, got %s".formatted(path, PresetLoader.kind(node));
        }
        return firstUnknown(node, allowed, path + ".");
    }

    static String validateButtons(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return "buttons: expected an object, got " + PresetLoader.kind(node);
        }
        String unknown = firstUnknown(node, BUTTON_KEYS, "buttons.");
        if (unknown != null) {
            return unknown;
        }
        var it = node.properties().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            String path = "buttons." + entry.getKey();
            JsonNode value = entry.getValue();
            if (!value.isTextual()) {
                return "%s: expected %s, got %s"
                        .formatted(path, wordList(ButtonRole.class), PresetLoader.kind(value));
            }
            if (PresetDraft.buttonStyle(value.textValue()) == null) {
                return "%s: expected %s, got '%s'"
                        .formatted(path, wordList(ButtonRole.class), value.textValue());
            }
        }
        return null;
    }

    static String validateIcons(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return "icons: expected an object, got " + PresetLoader.kind(node);
        }
        var it = node.properties().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            String path = "icons." + entry.getKey();
            JsonNode value = entry.getValue();
            if (PresetDraft.iconKey(entry.getKey()) == null) {
                return "%s: unknown icon key, expected one of %s"
                        .formatted(path, wordList(IconKey.class));
            }
            if (!value.isTextual()) {
                return "%s: expected an emoji or '' to remove it, got %s"
                        .formatted(path, PresetLoader.kind(value));
            }
            String text = value.textValue();
            if (!text.isEmpty() && !EmojiText.isValid(text)) {
                return "%s: expected a Unicode emoji or a Discord custom emoji, got '%s'"
                        .formatted(path, text);
            }
        }
        return null;
    }

    static <E extends Enum<E>> String validateEnum(JsonNode node, Class<E> type, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isTextual()) {
            return "%s: expected %s, got %s"
                    .formatted(path, wordList(type), PresetLoader.kind(node));
        }
        if (parseEnum(node.textValue(), type) == null) {
            return "%s: expected %s, got '%s'".formatted(path, wordList(type), node.textValue());
        }
        return null;
    }

    /** The first key not in {@code allowed}, reported as {@code path: unknown property}. */
    static String firstUnknown(JsonNode node, Set<String> allowed, String prefix) {
        var it = node.properties().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!allowed.contains(entry.getKey())) {
                return "%s%s: unknown property".formatted(prefix, entry.getKey());
            }
        }
        return null;
    }

    /** What one file said, before inheritance is applied. */
}
