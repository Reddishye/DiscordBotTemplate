package es.redactado.menu.preset;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;

/**
 * A preset file that has been parsed but not yet resolved.
 *
 * <p>The middle step between JSON and a {@link Preset}: everything the file said, with the
 * inheritance and the type conversions still to do. It exists as its own type because
 * resolving is where the decisions are, and a reader looking for "what does extends mean"
 * should not have to walk past the parser to find out.
 */
final class PresetDraft {

    private PresetDraft() {}

    record Draft(
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

    static Palette mergePalette(Preset parent, Map<String, Integer> overrides) {
        Palette base = parent == null ? DefaultLook.PALETTE : parent.palette();
        return new Palette(
                overrides.getOrDefault("accent", base.accent()),
                overrides.getOrDefault("success", base.success()),
                overrides.getOrDefault("warning", base.warning()),
                overrides.getOrDefault("danger", base.danger()),
                overrides.getOrDefault("info", base.info()),
                overrides.getOrDefault("neutral", base.neutral()));
    }

    static Icons mergeIcons(Preset parent, Map<IconKey, String> overrides) {
        Map<IconKey, String> merged = new EnumMap<>(IconKey.class);
        Icons base = parent == null ? DefaultLook.ICONS : parent.icons();
        merged.putAll(base.asMap());
        merged.putAll(overrides);
        return Icons.of(merged);
    }

    static ButtonStyles mergeButtons(Preset parent, Map<ButtonRole, ButtonStyle> overrides) {
        ButtonStyles base = parent == null ? DefaultLook.BUTTONS : parent.buttons();
        ButtonStyles merged = base;
        for (Map.Entry<ButtonRole, ButtonStyle> entry : overrides.entrySet()) {
            merged = merged.with(entry.getKey(), entry.getValue());
        }
        return merged;
    }

    static <T> T pick(T inherited, T override) {
        return override != null ? override : inherited;
    }

    static int color(String text) {
        return Integer.parseInt(text.substring(1), 16);
    }

    static String text(JsonNode node) {
        return node == null || !node.isTextual() ? null : node.textValue();
    }

    static Boolean bool(JsonNode node) {
        return node == null || !node.isBoolean() ? null : node.booleanValue();
    }

    /** The matching constant, or null when the word is not one of the choices. */
    static IconKey iconKey(String word) {
        return PresetSchema.parseEnum(word, IconKey.class);
    }

    static ButtonRole buttonRole(String word) {
        return PresetSchema.parseEnum(word, ButtonRole.class);
    }

    static ButtonStyle buttonStyle(String word) {
        return PresetSchema.parseEnum(word, ButtonStyle.class);
    }

    /** How a value's JSON type is described in an error message. */
    static Draft toDraft(JsonNode root, String name) {
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
                        ? PresetSchema.parseEnum(root.get("density").textValue(), Density.class)
                        : null,
                divider == null ? null : bool(divider.get("visible")),
                divider == null || divider.get("gap") == null
                        ? null
                        : PresetSchema.parseEnum(divider.get("gap").textValue(), Gap.class),
                header == null || header.get("level") == null
                        ? null
                        : header.get("level").intValue(),
                header == null ? null : bool(header.get("subtitle")),
                buttons,
                footer != null,
                text(footer));
    }

    /**
     * Resolves every draft onto its parent, parents first.
     *
     * <p>A depth-first walk carrying the chain of names currently being resolved.
     * Meeting a name already in the chain means a cycle, and every file on that cycle
     * is reported rather than only the one that happened to close it, because a
     * three-file cycle is one mistake with three victims and fixing only the reported
     * file leaves the bot broken.
     */

    /** Carries the per-load walk state so the recursion is one object, not six arguments. */
    static final class Resolver {

        private final Map<String, Draft> byName;
        private final Map<String, Preset> available;
        private final Set<String> unreadable;
        private final List<LoadProblem> problems;
        private final Map<String, Preset> resolved = new LinkedHashMap<>();

        /** The presets resolved so far, by name. */
        Map<String, Preset> resolved() {
            return resolved;
        }

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
    static Preset build(String name, Preset parent, Draft draft) {
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
}
