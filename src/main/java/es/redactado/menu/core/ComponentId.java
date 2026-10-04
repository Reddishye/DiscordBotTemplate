package es.redactado.menu.core;

import es.redactado.menu.api.Limits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses and encodes menu component custom IDs.
 *
 * <p>Format is {@code menu:<menuId>:<action>[:<param>...]}, so
 * {@code menu:entry:edit_birth:42} decodes to menu id {@code entry}, action
 * {@code edit_birth}, and params {@code ["42"]}.
 *
 * <p>Decoding uses only {@code indexOf} and {@code substring}. It deliberately
 * avoids {@link String#split}, because that compiles a regular expression and
 * allocates a substring array on every interaction.
 */
public record ComponentId(String menuId, String action, List<String> params) {

    private static final String PREFIX = "menu:";
    private static final char SEPARATOR = ':';

    public ComponentId {
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(params, "params");
        params = List.copyOf(params);
    }

    /**
     * Decodes a custom ID.
     *
     * <p>Returns empty for null, for an empty string, for a foreign prefix, for
     * fewer than three segments, and for an empty menu id or action.
     *
     * @param customId the raw component id
     * @return the parsed id, or empty when the string is not a menu id
     */
    public static Optional<ComponentId> decode(String customId) {
        if (customId == null || customId.isEmpty() || !customId.startsWith(PREFIX)) {
            return Optional.empty();
        }

        int menuStart = PREFIX.length();
        int menuEnd = customId.indexOf(SEPARATOR, menuStart);
        if (menuEnd < 0) {
            return Optional.empty();
        }

        int actionEnd = customId.indexOf(SEPARATOR, menuEnd + 1);
        if (actionEnd < 0) {
            actionEnd = customId.length();
        }

        String menuId = customId.substring(menuStart, menuEnd);
        String action = customId.substring(menuEnd + 1, actionEnd);
        if (menuId.isEmpty() || action.isEmpty()) {
            return Optional.empty();
        }
        if (actionEnd == customId.length()) {
            return Optional.of(new ComponentId(menuId, action, List.of()));
        }
        return Optional.of(new ComponentId(menuId, action, readParams(customId, actionEnd + 1)));
    }

    private static List<String> readParams(String customId, int from) {
        List<String> params = new ArrayList<>();
        int start = from;
        while (start <= customId.length()) {
            int end = customId.indexOf(SEPARATOR, start);
            if (end < 0) {
                end = customId.length();
            }
            params.add(customId.substring(start, end));
            start = end + 1;
        }
        return params;
    }

    /**
     * Encodes a custom ID.
     *
     * <p>The menu id and action must be non-empty and must not contain a colon.
     * A colon in any segment would corrupt parsing, so it is rejected here rather
     * than producing an id that decodes differently than intended.
     *
     * @param menuId the menu identifier
     * @param action the action name
     * @param params the parameters appended after the action
     * @return the encoded component id
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if the menu id or action is empty, if any
     *     segment contains a colon, or if the result exceeds
     *     {@link Limits#MAX_CUSTOM_ID_LENGTH}
     */
    public static String encode(String menuId, String action, String... params) {
        Objects.requireNonNull(menuId, "menuId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(params, "params");
        requireSegment("menuId", menuId);
        requireSegment("action", action);

        int size = PREFIX.length() + menuId.length() + 1 + action.length();
        for (int i = 0; i < params.length; i++) {
            size += 1 + requireParam(i, params[i]);
        }

        StringBuilder id =
                new StringBuilder(size)
                        .append(PREFIX)
                        .append(menuId)
                        .append(SEPARATOR)
                        .append(action);
        for (String param : params) {
            id.append(SEPARATOR).append(param);
        }

        String encoded = id.toString();
        if (encoded.length() > Limits.MAX_CUSTOM_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Component id is %d characters, the limit is %d; shorten the menu id, the action, or the params"
                            .formatted(encoded.length(), Limits.MAX_CUSTOM_ID_LENGTH));
        }
        return encoded;
    }

    /**
     * Encodes a custom ID from a list of parameters.
     *
     * @param menuId the menu identifier
     * @param action the action name
     * @param params the parameters appended after the action
     * @return the encoded component id
     * @throws NullPointerException if any argument, or any element, is null
     * @throws IllegalArgumentException under the same conditions as
     *     {@link #encode(String, String, String...)}
     */
    public static String encode(String menuId, String action, List<String> params) {
        Objects.requireNonNull(params, "params");
        return encode(menuId, action, params.toArray(new String[0]));
    }

    private static void requireSegment(String name, String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("The " + name + " segment must not be empty");
        }
        if (value.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException(
                    "The " + name + " segment must not contain '" + SEPARATOR + "': " + value);
        }
    }

    private static int requireParam(int index, String param) {
        Objects.requireNonNull(param, "params[" + index + "]");
        if (param.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException(
                    "The param at index %d must not contain '%s': %s"
                            .formatted(index, SEPARATOR, param));
        }
        return param.length();
    }

    /**
     * Reports whether a component id belongs to the menu system.
     *
     * @param customId the raw component id, possibly null
     * @return {@code true} when the id carries the menu prefix
     */
    public static boolean isMenuId(String customId) {
        return customId != null && customId.startsWith(PREFIX);
    }

    /**
     * Number of parameters carried by this id.
     *
     * @return the parameter count
     */
    public int paramCount() {
        return params.size();
    }

    /**
     * Parameter at the given index.
     *
     * @param index zero-based position
     * @return the parameter, or empty when the index is out of range
     */
    public Optional<String> param(int index) {
        return index >= 0 && index < params.size()
                ? Optional.of(params.get(index))
                : Optional.empty();
    }

    /**
     * Parameter at the given index, which must exist.
     *
     * @param index zero-based position
     * @return the parameter
     * @throws IllegalArgumentException when the index is out of range
     */
    public String require(int index) {
        return param(index)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Missing param at index %d of %d"
                                                .formatted(index, params.size())));
    }
}
