package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the bundles, the constants and the code agree with each other.
 *
 * <p>The failures this catches are all silent at runtime. A key with no Spanish value
 * falls back to English and nobody notices until a Spanish speaker complains. A
 * constant with no bundle entry shows a raw key to a user. A bundle entry with no
 * constant can never be reached and only confuses the next person to add a language.
 */
class MessageKeysTest {

    private static final Map<String, String> ENGLISH = bundle(Locale.ENGLISH);
    private static final Map<String, String> SPANISH = bundle(Locale.forLanguageTag("es"));

    @Nested
    @DisplayName("bundles")
    class Bundles {

        @Test
        @DisplayName("both bundles define exactly the same keys")
        void parity() {
            assertThat(SPANISH.keySet())
                    .as("Spanish must cover every English key")
                    .isEqualTo(ENGLISH.keySet());
            assertThat(ENGLISH.keySet())
                    .as("no key may exist only in English")
                    .isEqualTo(SPANISH.keySet());
        }

        @Test
        @DisplayName("every key uses the same placeholders in both bundles")
        void placeholderParity() {
            Map<String, String> englishPlaceholders = new TreeMap<>();
            Map<String, String> spanishPlaceholders = new TreeMap<>();
            ENGLISH.forEach((key, value) -> englishPlaceholders.put(key, placeholders(value)));
            SPANISH.forEach((key, value) -> spanishPlaceholders.put(key, placeholders(value)));

            assertThat(spanishPlaceholders)
                    .as("a translator must not add or drop {n}")
                    .isEqualTo(englishPlaceholders);
        }

        @Test
        @DisplayName("no value is blank")
        void noBlankValues() {
            ENGLISH.forEach(
                    (key, value) -> assertThat(value.strip()).as("English %s", key).isNotEmpty());
            SPANISH.forEach(
                    (key, value) -> assertThat(value.strip()).as("Spanish %s", key).isNotEmpty());
        }

        @Test
        @DisplayName("the Spanish bundle really is UTF-8, accents and all")
        void spanishIsAccented() {
            // Expected values are written as \\u escapes because AsciiSourcesTest keeps
            // every Java source below 0x80. That is what makes this a real encoding
            // check rather than a tautology: the expected strings carry real accented
            // characters built from ASCII source, and they only match if the properties
            // file was decoded as UTF-8. Read as ISO-8859-1 it would produce different
            // code points and these equalities would fail.
            assertThat(SPANISH.get(MessageKeys.ERROR_NOT_OWNER))
                    .isEqualTo("Este men\u00FA no es tuyo.");
            assertThat(SPANISH.get(MessageKeys.ERROR_BAD_PARAM))
                    .isEqualTo("Par\u00E1metro inv\u00E1lido o ausente.");
            assertThat(SPANISH.get(MessageKeys.ERROR_BUSY))
                    .isEqualTo("El bot est\u00E1 ocupado. Int\u00E9ntalo de nuevo.");
            assertThat(SPANISH.get(MessageKeys.ERROR_GENERIC))
                    .isEqualTo("Algo sali\u00F3 mal (ref: {0}).");
        }

        @Test
        @DisplayName("Spanish really differs from English, so stripping accents would be caught")
        void spanishIsNotJustEnglish() {
            assertThat(SPANISH.get(MessageKeys.ERROR_NOT_OWNER))
                    .isNotEqualTo(ENGLISH.get(MessageKeys.ERROR_NOT_OWNER));
            assertThat(SPANISH.get(MessageKeys.ERROR_GENERIC))
                    .isNotEqualTo(ENGLISH.get(MessageKeys.ERROR_GENERIC));
        }
    }

    @Nested
    @DisplayName("constants")
    class Constants {

        @Test
        @DisplayName("every constant exists in the default bundle")
        void everyConstantHasAValue() {
            for (String key : constants()) {
                assertThat(ENGLISH).as("constant %s", key).containsKey(key);
            }
        }

        @Test
        @DisplayName("every bundle key has a constant, so nothing is unreachable")
        void everyKeyHasAConstant() {
            assertThat(ENGLISH.keySet())
                    .as("orphaned bundle keys")
                    .isEqualTo(new TreeSet<>(constants()));
        }
    }

    @Nested
    @DisplayName("usage")
    class Usage {

        @Test
        @DisplayName("every constant is referenced somewhere in the main sources")
        void everyConstantIsUsed() {
            // Scans for MessageKeys.NAME, not for the key's value: a constant is used by
            // naming it, and looking for the literal would only ever match MessageKeys
            // itself, which is exactly the file being excluded.
            Set<String> sources = mainSourcesExcluding(MessageKeys.class);
            assertThat(sources).as("files scanned").isNotEmpty();

            Set<String> unreferenced = new TreeSet<>();
            for (Field field : MessageKeys.class.getDeclaredFields()) {
                String reference = MessageKeys.class.getSimpleName() + "." + field.getName();
                boolean used = false;
                for (String source : sources) {
                    if (source.contains(reference)) {
                        used = true;
                        break;
                    }
                }
                if (!used) {
                    unreferenced.add(reference);
                }
            }
            assertThat(unreferenced)
                    .as("a key nothing uses is dead weight in every bundle forever")
                    .isEmpty();
        }
    }

    /** Every {@code public static final String} on {@link MessageKeys}. */
    private static Set<String> constants() {
        Set<String> keys = new TreeSet<>();
        for (Field field : MessageKeys.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    keys.add((String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read " + field, e);
                }
            }
        }
        return keys;
    }

    private static Set<String> mainSourcesExcluding(Class<?> excluded) {
        String path = "src/main/java/es/redactado/menu/";
        java.util.List<String> sources = new java.util.ArrayList<>();
        try (var stream = java.nio.file.Files.walk(java.nio.file.Path.of(path))) {
            for (java.nio.file.Path file :
                    stream.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (!file.toString().endsWith(excluded.getSimpleName() + ".java")) {
                    sources.add(java.nio.file.Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot scan the main sources", e);
        }
        return new java.util.LinkedHashSet<>(sources);
    }

    private static Map<String, String> bundle(Locale locale) {
        ResourceBundle bundle =
                ResourceBundle.getBundle(
                        Messages.BASE_NAME,
                        locale,
                        ResourceBundle.Control.getNoFallbackControl(
                                ResourceBundle.Control.FORMAT_PROPERTIES));
        Map<String, String> flat = new TreeMap<>();
        for (String key : bundle.keySet()) {
            flat.put(key, bundle.getString(key));
        }
        return flat;
    }

    /** The {@code {n}} placeholders of a value, sorted, as {@code 0,1}. */
    private static String placeholders(String value) {
        Matcher matcher = Pattern.compile("\\{(\\d+)}").matcher(value);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return String.join(",", found);
    }
}
