package es.redactado.menu.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Looks up user-facing text for a locale.
 *
 * <p>A resolved language is flattened into an immutable map once and cached, so the
 * hot path is a {@link ConcurrentHashMap} read and a {@link Map#get} rather than a
 * {@code ResourceBundle} walk. A menu can resolve a dozen texts per render and a busy
 * bot does that thousands of times a minute; re-resolving bundles there would be
 * wasted work that would also re-read and re-parse the same properties file over and
 * over.
 *
 * <p><strong>The JVM default locale cannot leak in.</strong> A bot whose host is set to
 * German must not silently start answering in German, and it must not answer in English
 * just because the host is German either. The no-fallback control makes the requested
 * locale the only one consulted, and the chain is explicit: the exact tag, then the
 * bare language, then English.
 *
 * <p>Arguments are substituted by hand with a {@link StringBuilder} rather than by
 * {@code MessageFormat}, for two reasons. {@code MessageFormat} treats a single quote
 * as an escape character, so a translation containing an apostrophe renders wrong
 * unless its author knows to double it, and apostrophes are common in the languages
 * this would be used for. It also parses patterns, which is a great deal of machinery
 * for {@code {0}}.
 */
public final class Messages {

    /** Where the bundles live, on the classpath. */
    public static final String BASE_NAME = "menu.messages";

    private static final Logger LOG = LoggerFactory.getLogger(Messages.class);

    /** Cache key for the English bundle, which doubles as the fallback. */
    private static final String ENGLISH_TAG = "en";

    /**
     * Stops the platform locale being consulted, so only what was asked for is used.
     */
    private static final ResourceBundle.Control CONTROL =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    /**
     * Keys already reported as missing, so the warning is logged once per key rather
     * than once per render.
     */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Messages() {}

    /**
     * The bundles shipped with the template.
     *
     * @return a messages instance reading English and Spanish
     */
    public static Messages standard() {
        return new Messages();
    }

    /**
     * Resolves a key for a locale.
     *
     * @param locale the locale to answer in; {@link Locale#ENGLISH} when unknown or null
     * @param key a key declared in {@link MessageKeys}
     * @param args values for the {@code {n}} placeholders; may be empty
     * @return the translated text, or the key itself when no bundle defines it
     */
    public String get(Locale locale, String key, Object... args) {
        String value = resolve(locale).get(key);
        if (value == null) {
            // A key may be added after a translation exists, so English is the last
            // resort before giving up.
            value = english().get(key);
        }
        if (value == null) {
            warnOnce(key);
            return key;
        }
        if (args == null || args.length == 0) {
            return value;
        }
        return substitute(value, args);
    }

    /** The flattened map for a language, resolved once and cached. */
    private static Map<String, String> resolve(Locale locale) {
        Locale requested = locale == null ? Locale.ENGLISH : locale;
        return CACHE.computeIfAbsent(requested.toLanguageTag(), key -> load(requested));
    }

    /** Walks exact tag, then bare language, then English. */
    private static Map<String, String> load(Locale locale) {
        for (Locale candidate : chain(locale)) {
            ResourceBundle bundle = bundle(candidate);
            if (bundle != null) {
                return flatten(bundle);
            }
        }
        LOG.warn("No bundle for locale {}, answering in English", locale);
        return Map.of();
    }

    private static List<Locale> chain(Locale locale) {
        if (locale.getLanguage().isEmpty() || Locale.ENGLISH.equals(locale)) {
            return List.of(Locale.ENGLISH);
        }
        return List.of(locale, Locale.of(locale.getLanguage()), Locale.ENGLISH);
    }

    private static Map<String, String> english() {
        return CACHE.computeIfAbsent(ENGLISH_TAG, Messages::loadEnglish);
    }

    private static Map<String, String> loadEnglish(String tag) {
        ResourceBundle bundle = bundle(Locale.ENGLISH);
        return bundle == null ? Map.of() : flatten(bundle);
    }

    private static ResourceBundle bundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(BASE_NAME, locale, CONTROL);
        } catch (MissingResourceException e) {
            return null;
        }
    }

    private static Map<String, String> flatten(ResourceBundle bundle) {
        Map<String, String> flat = new HashMap<>();
        for (String key : bundle.keySet()) {
            flat.put(key, bundle.getString(key));
        }
        return Collections.unmodifiableMap(flat);
    }

    /**
     * Replaces {@code {n}} with the n-th argument.
     *
     * <p>An index with no matching argument is left exactly as it is rather than
     * rendered as something like {@code {2}}, because a user seeing a number in the
     * output has no way to tell it from real content. Arguments past the last
     * placeholder are ignored, which lets one call site pass a shared argument list.
     *
     * <p>Package-private so the substitution rules can be tested directly against a
     * two-placeholder template. No shipped message has two placeholders, and inventing
     * one purely to be tested would put text in front of users that exists only for a
     * test.
     */
    static String substitute(String template, Object... args) {
        StringBuilder out = new StringBuilder(template.length() + 16);
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int close = template.indexOf('}', i + 1);
                if (close > i) {
                    int index = index(template.substring(i + 1, close));
                    if (index >= 0 && index < args.length) {
                        out.append(args[index]);
                        i = close + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** The placeholder index, or -1 when the braces hold anything but a plain number. */
    private static int index(String body) {
        if (body.isEmpty() || body.length() > 9) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private static void warnOnce(String key) {
        if (WARNED.add(key)) {
            LOG.warn("No message for key '{}'; showing the key instead", key);
        }
    }
}
