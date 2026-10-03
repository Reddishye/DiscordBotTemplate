package es.redactado.menu.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * Parses and encodes component custom IDs.
 * Format: {@code menu:<menuId>:<action>[:<param1>[:<param2>...]]}
 *
 * <p>Example: {@code menu:entry:edit_birth:42} gives menuId="entry", action="edit_birth", params=["42"]
 */
public record ComponentId(String menuId, String action, List<String> params) {

    /** Decode a custom ID string. Returns null if invalid format. */
    public static ComponentId decode(String customId) {
        if (customId == null || customId.isEmpty()) return null;
        String[] parts = customId.split(":", -1);
        if (parts.length < 3 || !"menu".equals(parts[0])) return null;

        String menuId = parts[1];
        String action = parts[2];
        List<String> params = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) {
            params.add(parts[i]);
        }
        return new ComponentId(menuId, action, Collections.unmodifiableList(params));
    }

    /** Build a custom ID string: menu:menuId:action:param1:param2 */
    public static String encode(String menuId, String action, String... params) {
        var sj = new StringJoiner(":");
        sj.add("menu").add(menuId).add(action);
        for (var p : params) sj.add(p);
        String id = sj.toString();
        if (id.length() > 100) {
            throw new IllegalArgumentException(
                    "Custom ID too long (%d chars, max 100): %s".formatted(id.length(), id));
        }
        return id;
    }

    /** Build a custom ID for an action with list params. */
    public static String encode(String menuId, String action, List<String> params) {
        return encode(menuId, action, params.toArray(String[]::new));
    }

    /** Quick check if a custom ID belongs to the menu system. */
    public static boolean isMenuId(String customId) {
        return customId != null && customId.startsWith("menu:");
    }

    public int paramCount() {
        return params.size();
    }

    /** Get param at index, or null. */
    public String param(int index) {
        return index < params.size() ? params.get(index) : null;
    }

    /** Get param at index, or throw. */
    public String require(int index) {
        String p = param(index);
        if (p == null)
            throw new IllegalArgumentException(
                    "Missing required param at index %d in %s".formatted(index, this));
        return p;
    }
}
