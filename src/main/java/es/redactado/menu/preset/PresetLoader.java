package es.redactado.menu.preset;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

/**
 * Reads custom presets from a directory of JSON files, one preset per file.
 *
 * <p>The format is deliberately partial: a file names a {@code extends} parent and
 * lists only the fields it wants to change, so a preset stays a few lines even when
 * the look has thirty fields. Inheritance is resolved once, here, at load time, and
 * nothing about it survives into the loaded {@link Preset}: there is no chain to walk
 * and no way for a later parent edit to change a child that already loaded.
 *
 * <p>The tree model is used rather than data binding because the two interesting
 * behaviours are not expressible with data binding: rejecting an unknown property at
 * any depth, and reporting exactly which field was wrong. Both need the raw keys and
 * the position in the document.
 *
 * <p><strong>This class is pure.</strong> It reads files and returns a result; it never
 * throws for a bad file and never mutates the presets it is given. A directory of
 * fifty files where one is malformed yields forty-nine presets and one problem.
 */
public final class PresetLoader {

    /** Largest file accepted, to bound the work one file can ask for. */
    public static final long MAX_FILE_BYTES = 64L * 1024L;

    /** Largest number of files read in one pass. */
    public static final int MAX_FILES = 200;

    private static final Set<String> ROOT_KEYS =
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

    private static final Set<String> PALETTE_KEYS =
            Set.of("accent", "success", "warning", "danger", "info", "neutral");

    private static final Set<String> DIVIDER_KEYS = Set.of("visible", "gap");
    private static final Set<String> HEADER_KEYS = Set.of("level", "subtitle");
    private static final Set<String> BUTTON_KEYS =
            Set.of("primary", "secondary", "success", "danger");

    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    /**
     * Jackson is created once. Reusing the factory keeps its internal buffers, and a
     * reload of two hundred files should not allocate two hundred parsers.
     */
    private static final ObjectMapper MAPPER =
            new ObjectMapper(
                    JsonFactory.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .build());

    private PresetLoader() {}

    /**
     * Loads every preset file in a directory.
     *
     * <p>Files are visited in sorted name order and the returned presets are sorted by
     * name, so two loads of the same directory produce equal results and a reload
     * produces no spurious change.
     *
     * @param directory the directory to read; not recursive
     * @param builtins the presets available as parents, normally
     *     {@link BuiltinPresets#all()}
     * @return the presets that loaded and the problems found
     */
    public static LoadResult load(Path directory, Collection<Preset> builtins) {
        List<LoadProblem> problems = new ArrayList<>();
        if (directory == null || !Files.isDirectory(directory)) {
            return new LoadResult(List.of(), problems);
        }

        List<Path> files = listJsonFiles(directory, problems);
        Map<String, Preset> available = new HashMap<>();
        for (Preset builtin : builtins) {
            available.put(builtin.name(), builtin);
        }

        // One entry per file, whether it parsed or not: children need to know that a
        // named parent failed rather than merely that it is unknown.
        Map<String, Draft> drafts = new LinkedHashMap<>();
        Set<String> unreadable = new HashSet<>();
        for (Path file : files) {
            String baseName = baseNameOf(file);
            Draft draft = read(file, baseName, available.keySet(), problems);
            if (draft == null) {
                unreadable.add(baseName);
            } else {
                drafts.putIfAbsent(draft.name(), draft);
            }
        }

        List<LoadProblem> inheritance = new ArrayList<>();
        Resolver resolver = new Resolver(drafts, available, unreadable, inheritance);
        for (Draft draft : drafts.values()) {
            resolver.resolve(draft.name(), new ArrayList<>());
        }
        // Inheritance problems follow the per-file ones, so the first problem a reader
        // sees for a file is always about that file's own content.
        problems.addAll(inheritance);

        List<Preset> presets =
                resolver.resolved.values().stream()
                        .sorted(Comparator.comparing(Preset::name))
                        .toList();
        return new LoadResult(presets, problems);
    }

    /** The files to read, in sorted order, capped at {@link #MAX_FILES}. */
    private static List<Path> listJsonFiles(Path directory, List<LoadProblem> problems) {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                if (path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        } catch (IOException e) {
            problems.add(
                    new LoadProblem(
                            directory.getFileName().toString(),
                            "cannot be listed: " + e.getMessage()));
            return List.of();
        }

        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        if (files.size() > MAX_FILES) {
            problems.add(
                    new LoadProblem(
                            directory.getFileName().toString(),
                            "too many files: found %d, reading the first %d"
                                    .formatted(files.size(), MAX_FILES)));
            return List.copyOf(files.subList(0, MAX_FILES));
        }
        return files;
    }

    /** The file name without its {@code .json} suffix. */
    private static String baseNameOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    /** A validated draft, or {@code null} when the file is unusable. */
    private static Draft read(
            Path file, String baseName, Set<String> builtinNames, List<LoadProblem> problems) {
        String fileName = baseName + ".json";
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            problems.add(new LoadProblem(fileName, "cannot be read: " + e.getMessage()));
            return null;
        }
        if (size > MAX_FILE_BYTES) {
            problems.add(
                    new LoadProblem(
                            fileName,
                            "file is too large: %d bytes, the limit is %d"
                                    .formatted(size, MAX_FILE_BYTES)));
            return null;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(Files.readAllBytes(file));
        } catch (IOException e) {
            problems.add(
                    new LoadProblem(fileName, "is not valid JSON: " + oneLine(e.getMessage())));
            return null;
        }

        if (root == null || root.isNull()) {
            problems.add(new LoadProblem(fileName, "is empty"));
            return null;
        }
        if (!root.isObject()) {
            problems.add(new LoadProblem(fileName, "expected an object, got " + kind(root)));
            return null;
        }

        String problem = validate(root, baseName, builtinNames);
        if (problem != null) {
            problems.add(new LoadProblem(fileName, problem));
            return null;
        }
        return toDraft(root, baseName);
    }

    /** Returns the first problem as a {@code field path: problem} string, or null. */
    private static String validate(JsonNode root, String baseName, Set<String> builtinNames) {
        String unknown = firstUnknown(root, ROOT_KEYS, "");
        if (unknown != null) {
            return unknown;
        }

        JsonNode nameNode = root.get("name");
        if (nameNode == null) {
            return "name: is required";
        }
        if (!nameNode.isTextual()) {
            return "name: expected a string, got " + kind(nameNode);
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
            return "extends: expected a string, got " + kind(extendsNode);
        }

        JsonNode description = root.get("description");
        if (description != null) {
            if (!description.isTextual()) {
                return "description: expected a string, got " + kind(description);
            }
            if (description.textValue().length() > Preset.MAX_DESCRIPTION_LENGTH) {
                return "description: is %d characters, the limit is %d"
                        .formatted(description.textValue().length(), Preset.MAX_DESCRIPTION_LENGTH);
            }
        }

        JsonNode footer = root.get("footer");
        if (footer != null) {
            if (!footer.isTextual()) {
                return "footer: expected a string, got " + kind(footer);
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
                    return "%s: expected #RRGGBB, got %s".formatted(path, kind(value));
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
                return "divider.visible: expected true or false, got " + kind(visible);
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
                    return "header.level: expected a number, got " + kind(level);
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
                return "header.subtitle: expected true or false, got " + kind(subtitle);
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

    private static String validateObject(JsonNode node, Set<String> allowed, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return "%s: expected an object, got %s".formatted(path, kind(node));
        }
        return firstUnknown(node, allowed, path + ".");
    }

    private static String validateButtons(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return "buttons: expected an object, got " + kind(node);
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
                        .formatted(path, wordList(ButtonRole.class), kind(value));
            }
            if (buttonStyle(value.textValue()) == null) {
                return "%s: expected %s, got '%s'"
                        .formatted(path, wordList(ButtonRole.class), value.textValue());
            }
        }
        return null;
    }

    private static String validateIcons(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (!node.isObject()) {
            return "icons: expected an object, got " + kind(node);
        }
        var it = node.properties().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            String path = "icons." + entry.getKey();
            JsonNode value = entry.getValue();
            if (iconKey(entry.getKey()) == null) {
                return "%s: unknown icon key, expected one of %s"
                        .formatted(path, wordList(IconKey.class));
            }
            if (!value.isTextual()) {
                return "%s: expected an emoji or '' to remove it, got %s"
                        .formatted(path, kind(value));
            }
            String text = value.textValue();
            if (!text.isEmpty() && !EmojiText.isValid(text)) {
                return "%s: expected a Unicode emoji or a Discord custom emoji, got '%s'"
                        .formatted(path, text);
            }
        }
        return null;
    }

    private static <E extends Enum<E>> String validateEnum(
            JsonNode node, Class<E> type, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isTextual()) {
            return "%s: expected %s, got %s".formatted(path, wordList(type), kind(node));
        }
        if (parseEnum(node.textValue(), type) == null) {
            return "%s: expected %s, got '%s'".formatted(path, wordList(type), node.textValue());
        }
        return null;
    }

    /** The first key not in {@code allowed}, reported as {@code path: unknown property}. */
    private static String firstUnknown(JsonNode node, Set<String> allowed, String prefix) {
        var it = node.properties().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!allowed.contains(entry.getKey())) {
                return "%s%s: unknown property".formatted(prefix, entry.getKey());
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- drafts

    /** What one file said, before inheritance is applied. */
    private record Draft(
            String name,
            String extendsName,
            String description,
            Map<String, Integer> palette,
            Map<IconKey, String> icons,
            boolean iconsGiven,
            Density density,
            Boolean dividerVisible,
            Gap dividerGap,
            Integer headerLevel,
            Boolean headerSubtitle,
            Map<ButtonRole, ButtonStyle> buttons,
            boolean footerGiven,
            String footer) {}

    private static Draft toDraft(JsonNode root, String name) {
        Map<String, Integer> palette = null;
        JsonNode paletteNode = root.get("palette");
        if (paletteNode != null) {
            palette = new LinkedHashMap<>();
            var it = paletteNode.properties().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                palette.put(entry.getKey(), color(entry.getValue().textValue()));
            }
        }

        Map<IconKey, String> icons = null;
        JsonNode iconsNode = root.get("icons");
        if (iconsNode != null) {
            icons = new EnumMap<>(IconKey.class);
            var it = iconsNode.properties().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                icons.put(iconKey(entry.getKey()), entry.getValue().textValue());
            }
        }

        Map<ButtonRole, ButtonStyle> buttons = null;
        JsonNode buttonsNode = root.get("buttons");
        if (buttonsNode != null) {
            buttons = new EnumMap<>(ButtonRole.class);
            var it = buttonsNode.properties().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                buttons.put(buttonRole(entry.getKey()), buttonStyle(entry.getValue().textValue()));
            }
        }

        JsonNode divider = root.get("divider");
        JsonNode header = root.get("header");
        JsonNode footer = root.get("footer");

        return new Draft(
                name,
                text(root.get("extends")),
                text(root.get("description")),
                palette,
                icons,
                iconsNode != null,
                root.has("density")
                        ? parseEnum(root.get("density").textValue(), Density.class)
                        : null,
                divider == null ? null : bool(divider.get("visible")),
                divider == null || divider.get("gap") == null
                        ? null
                        : parseEnum(divider.get("gap").textValue(), Gap.class),
                header == null || header.get("level") == null
                        ? null
                        : header.get("level").intValue(),
                header == null ? null : bool(header.get("subtitle")),
                buttons,
                footer != null,
                text(footer));
    }

    // ------------------------------------------------------------- inheritance

    /**
     * Resolves every draft onto its parent, parents first.
     *
     * <p>A depth-first walk carrying the chain of names currently being resolved.
     * Meeting a name already in the chain means a cycle, and every file on that cycle
     * is reported rather than only the one that happened to close it, because a
     * three-file cycle is one mistake with three victims and fixing only the reported
     * file leaves the bot broken.
     */
    private static void resolveAll(
            List<Draft> drafts,
            Map<String, Preset> available,
            Set<String> unreadable,
            List<LoadProblem> problems) {

        Map<String, Draft> byName = new LinkedHashMap<>();
        for (Draft draft : drafts) {
            byName.put(draft.name(), draft);
        }
        Resolver resolver = new Resolver(byName, available, unreadable, problems);
        for (Draft draft : drafts) {
            resolver.resolve(draft.name(), new ArrayList<>());
        }
    }

    /** Carries the per-load walk state so the recursion is one object, not six arguments. */
    private static final class Resolver {

        private final Map<String, Draft> byName;
        private final Map<String, Preset> available;
        private final Set<String> unreadable;
        private final List<LoadProblem> problems;
        private final Map<String, Preset> resolved = new LinkedHashMap<>();
        private final Set<String> done = new HashSet<>();
        private final Set<String> inCycle = new HashSet<>();

        Resolver(
                Map<String, Draft> byName,
                Map<String, Preset> available,
                Set<String> unreadable,
                List<LoadProblem> problems) {
            this.byName = byName;
            this.available = available;
            this.unreadable = unreadable;
            this.problems = problems;
        }

        void resolve(String name, List<String> chain) {
            if (done.contains(name)) {
                return;
            }
            if (chain.contains(name)) {
                reportCycle(chain, name);
                return;
            }

            Draft draft = byName.get(name);
            if (draft == null) {
                return;
            }

            chain.add(name);
            Preset parent = null;
            boolean usable = true;

            if (draft.extendsName() != null) {
                String parentName = draft.extendsName();
                if (byName.containsKey(parentName)) {
                    resolve(parentName, chain);
                    parent = resolved.get(parentName);
                    if (parent == null) {
                        // A file already named by a cycle report needs no second message.
                        if (!inCycle.contains(name)) {
                            problems.add(
                                    new LoadProblem(
                                            name + ".json",
                                            inCycle.contains(parentName)
                                                    ? "extends: parent '%s' is part of a cycle"
                                                            .formatted(parentName)
                                                    : "extends: parent '%s' failed to load"
                                                            .formatted(parentName)));
                        }
                        usable = false;
                    }
                } else if (available.containsKey(parentName)) {
                    parent = available.get(parentName);
                } else if (unreadable.contains(parentName)) {
                    problems.add(
                            new LoadProblem(
                                    name + ".json",
                                    "extends: parent '%s' failed to load".formatted(parentName)));
                    usable = false;
                } else {
                    problems.add(
                            new LoadProblem(
                                    name + ".json",
                                    "extends: no preset named '%s'".formatted(parentName)));
                    usable = false;
                }
            }

            chain.remove(name);
            done.add(name);

            if (usable) {
                try {
                    resolved.put(name, build(name, parent, draft));
                } catch (IllegalArgumentException e) {
                    problems.add(new LoadProblem(name + ".json", e.getMessage()));
                }
            }
        }

        /** Reports each file on the cycle, naming the others, then marks them all done. */
        private void reportCycle(List<String> chain, String repeated) {
            List<String> cycle = List.copyOf(chain.subList(chain.indexOf(repeated), chain.size()));
            for (String member : cycle) {
                List<String> others =
                        cycle.stream()
                                .filter(other -> !other.equals(member))
                                .sorted()
                                .map(other -> other + ".json")
                                .toList();
                problems.add(
                        new LoadProblem(
                                member + ".json",
                                others.isEmpty()
                                        ? "extends: is a cycle with itself"
                                        : "extends: is part of a cycle with "
                                                + String.join(", ", others)));
                inCycle.add(member);
                done.add(member);
            }
        }
    }

    /** Applies a draft onto its parent, or onto the defaults when there is no parent. */
    private static Preset build(String name, Preset parent, Draft draft) {
        Preset.Builder builder =
                parent == null ? Preset.builder(name) : parent.toBuilder().name(name);

        if (draft.description() != null) {
            builder.description(draft.description());
        }

        if (draft.palette() != null) {
            builder.palette(mergePalette(parent, draft.palette()));
        }

        if (draft.iconsGiven()) {
            builder.icons(mergeIcons(parent, draft.icons()));
        }

        if (draft.density() != null) {
            builder.density(draft.density());
        }

        if (draft.dividerVisible() != null || draft.dividerGap() != null) {
            DividerStyle base = parent == null ? DefaultLook.DIVIDER : parent.divider();
            builder.divider(new DividerStyle(base.visible(), pick(base.gap(), draft.dividerGap())));
        }

        if (draft.headerLevel() != null || draft.headerSubtitle() != null) {
            HeaderStyle base = parent == null ? DefaultLook.HEADER : parent.header();
            builder.header(
                    new HeaderStyle(
                            pick(base.level(), draft.headerLevel()),
                            pick(base.subtitle(), draft.headerSubtitle())));
        }

        if (draft.buttons() != null) {
            builder.buttons(mergeButtons(parent, draft.buttons()));
        }

        if (draft.footerGiven()) {
            builder.footer(draft.footer());
        }

        return builder.build();
    }

    private static Palette mergePalette(Preset parent, Map<String, Integer> overrides) {
        Palette base = parent == null ? DefaultLook.PALETTE : parent.palette();
        return new Palette(
                overrides.getOrDefault("accent", base.accent()),
                overrides.getOrDefault("success", base.success()),
                overrides.getOrDefault("warning", base.warning()),
                overrides.getOrDefault("danger", base.danger()),
                overrides.getOrDefault("info", base.info()),
                overrides.getOrDefault("neutral", base.neutral()));
    }

    private static Icons mergeIcons(Preset parent, Map<IconKey, String> overrides) {
        Map<IconKey, String> merged = new EnumMap<>(IconKey.class);
        Icons base = parent == null ? DefaultLook.ICONS : parent.icons();
        merged.putAll(base.asMap());
        merged.putAll(overrides);
        return Icons.of(merged);
    }

    private static ButtonStyles mergeButtons(
            Preset parent, Map<ButtonRole, ButtonStyle> overrides) {
        ButtonStyles base = parent == null ? DefaultLook.BUTTONS : parent.buttons();
        ButtonStyles merged = base;
        for (Map.Entry<ButtonRole, ButtonStyle> entry : overrides.entrySet()) {
            merged = merged.with(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    private static <T> T pick(T inherited, T override) {
        return override != null ? override : inherited;
    }

    // ------------------------------------------------------------ conversions

    private static int color(String text) {
        return Integer.parseInt(text.substring(1), 16);
    }

    private static String text(JsonNode node) {
        return node == null || !node.isTextual() ? null : node.textValue();
    }

    private static Boolean bool(JsonNode node) {
        return node == null || !node.isBoolean() ? null : node.booleanValue();
    }

    /** The matching constant, or null when the word is not one of the choices. */
    private static <E extends Enum<E>> E parseEnum(String word, Class<E> type) {
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

    private static <E extends Enum<E>> String wordList(Class<E> type) {
        List<String> words = new ArrayList<>();
        for (E constant : type.getEnumConstants()) {
            words.add(constant.name().toLowerCase(Locale.ROOT));
        }
        return String.join("|", words);
    }

    private static IconKey iconKey(String word) {
        return parseEnum(word, IconKey.class);
    }

    private static ButtonRole buttonRole(String word) {
        return parseEnum(word, ButtonRole.class);
    }

    private static ButtonStyle buttonStyle(String word) {
        return parseEnum(word, ButtonStyle.class);
    }

    /** How a value's JSON type is described in an error message. */
    private static String kind(JsonNode node) {
        if (node.isTextual()) {
            return "'" + node.textValue() + "'";
        }
        if (node.isNumber()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asText();
        }
        if (node.isArray()) {
            return "an array";
        }
        if (node.isObject()) {
            return "an object";
        }
        return "null";
    }

    /** Collapses a Jackson message onto one line so a log entry stays readable. */
    private static String oneLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
