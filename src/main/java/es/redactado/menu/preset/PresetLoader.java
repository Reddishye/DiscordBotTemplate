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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    static final ObjectMapper MAPPER =
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
        Map<String, PresetDraft.Draft> drafts = new LinkedHashMap<>();
        Set<String> unreadable = new HashSet<>();
        for (Path file : files) {
            String baseName = baseNameOf(file);
            PresetDraft.Draft draft = read(file, baseName, available.keySet(), problems);
            if (draft == null) {
                unreadable.add(baseName);
            } else {
                drafts.putIfAbsent(draft.name(), draft);
            }
        }

        List<LoadProblem> inheritance = new ArrayList<>();
        PresetDraft.Resolver resolver =
                new PresetDraft.Resolver(drafts, available, unreadable, inheritance);
        for (PresetDraft.Draft draft : drafts.values()) {
            resolver.resolve(draft.name(), new ArrayList<>());
        }
        // Inheritance problems follow the per-file ones, so the first problem a reader
        // sees for a file is always about that file's own content.
        problems.addAll(inheritance);

        List<Preset> presets =
                resolver.resolved().values().stream()
                        .sorted(Comparator.comparing(Preset::name))
                        .toList();
        return new LoadResult(presets, problems);
    }

    /** The files to read, in sorted order, capped at {@link #MAX_FILES}. */
    static List<Path> listJsonFiles(Path directory, List<LoadProblem> problems) {
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
    static String baseNameOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }

    /** A validated draft, or {@code null} when the file is unusable. */
    private static PresetDraft.Draft read(
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

        String problem = PresetSchema.validate(root, baseName, builtinNames);
        if (problem != null) {
            problems.add(new LoadProblem(fileName, problem));
            return null;
        }
        return PresetDraft.toDraft(root, baseName);
    }

    /** Returns the first problem as a {@code field path: problem} string, or null. */
    static String kind(JsonNode node) {
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
    static String oneLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
