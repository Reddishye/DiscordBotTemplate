package es.redactado.menu.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.view.Limits;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComponentIdTest {

    @Nested
    @DisplayName("decode")
    class Decode {

        @Test
        @DisplayName("reads menu, action and a single param")
        void singleParam() {
            ComponentId id = decode("menu:entry:edit_birth:42");

            assertThat(id.menuId()).isEqualTo("entry");
            assertThat(id.action()).isEqualTo("edit_birth");
            assertThat(id.params()).containsExactly("42");
            assertThat(id.paramCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("reads a menu and action with no params")
        void noParams() {
            ComponentId id = decode("menu:entry:view");

            assertThat(id.menuId()).isEqualTo("entry");
            assertThat(id.action()).isEqualTo("view");
            assertThat(id.params()).isEmpty();
            assertThat(id.paramCount()).isZero();
        }

        @Test
        @DisplayName("reads many params")
        void manyParams() {
            ComponentId id = decode("menu:entry:open:1:two:three:4");

            assertThat(id.params()).containsExactly("1", "two", "three", "4");
            assertThat(id.paramCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("keeps an empty param in the middle")
        void emptyParamInMiddle() {
            ComponentId id = decode("menu:a:b::c");

            assertThat(id.params()).containsExactly("", "c");
        }

        @Test
        @DisplayName("reads a trailing colon as one empty param")
        void trailingColon() {
            ComponentId id = decode("menu:a:b:");

            assertThat(id.menuId()).isEqualTo("a");
            assertThat(id.action()).isEqualTo("b");
            assertThat(id.params()).containsExactly("");
        }

        @Test
        @DisplayName("reads consecutive trailing colons")
        void consecutiveTrailingColons() {
            assertThat(decode("menu:a:b::").params()).containsExactly("", "");
        }

        @ParameterizedTest
        @DisplayName("rejects malformed input")
        @ValueSource(
                strings = {
                    "",
                    "menu",
                    "menu:",
                    "menu:a",
                    "menu::b",
                    "menu:a:",
                    "other:a:b",
                    "MENU:a:b",
                    "prefixmenu:a:b",
                    ":a:b"
                })
        void rejectsMalformed(String customId) {
            assertThat(ComponentId.decode(customId)).isEmpty();
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThat(ComponentId.decode(null)).isEmpty();
        }

        @Test
        @DisplayName("accepts an id of exactly the maximum length")
        void acceptsMaximumLength() {
            String encoded = ComponentId.encode(menuIdOfLength(Limits.MAX_CUSTOM_ID_LENGTH), "a");

            assertThat(encoded).hasSize(Limits.MAX_CUSTOM_ID_LENGTH);
            assertThat(decode(encoded).menuId()).hasSize(Limits.MAX_CUSTOM_ID_LENGTH - 7);
        }
    }

    @Nested
    @DisplayName("encode")
    class Encode {

        @Test
        @DisplayName("rejects an id one character over the limit")
        void rejectsOneOverLimit() {
            String menuId = menuIdOfLength(Limits.MAX_CUSTOM_ID_LENGTH + 1);

            assertThatThrownBy(() -> ComponentId.encode(menuId, "a"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(Limits.MAX_CUSTOM_ID_LENGTH + 1))
                    .hasMessageContaining(String.valueOf(Limits.MAX_CUSTOM_ID_LENGTH))
                    .hasMessageNotContaining(menuId);
        }

        @Test
        @DisplayName("rejects an empty menu id")
        void rejectsEmptyMenuId() {
            assertThatThrownBy(() -> ComponentId.encode("", "a"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("menuId")
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects an empty action")
        void rejectsEmptyAction() {
            assertThatThrownBy(() -> ComponentId.encode("a", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("action")
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects a colon in the menu id")
        void rejectsColonInMenuId() {
            assertThatThrownBy(() -> ComponentId.encode("a:b", "act"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("menuId");
        }

        @Test
        @DisplayName("rejects a colon in the action")
        void rejectsColonInAction() {
            assertThatThrownBy(() -> ComponentId.encode("a", "ac:t"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("action");
        }

        @Test
        @DisplayName("rejects a colon in a param and names the index")
        void rejectsColonInParam() {
            assertThatThrownBy(() -> ComponentId.encode("a", "act", "ok", "b:ad"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("index 1");
        }

        @Test
        @DisplayName("allows an empty param")
        void allowsEmptyParam() {
            assertThat(ComponentId.encode("a", "act", "")).isEqualTo("menu:a:act:");
        }

        @Test
        @DisplayName("rejects null segments")
        void rejectsNull() {
            assertThatThrownBy(() -> ComponentId.encode(null, "a"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("menuId");
            assertThatThrownBy(() -> ComponentId.encode("a", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("action");
            assertThatThrownBy(() -> ComponentId.encode("a", "act", (String) null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("accepts a list of params")
        void acceptsList() {
            assertThat(ComponentId.encode("a", "act", List.of("1", "2")))
                    .isEqualTo("menu:a:act:1:2");
        }
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("encode then decode is lossless")
        void roundTrip() {
            List<String> params = List.of("42", "abc", "");

            ComponentId id = decode(ComponentId.encode("profile", "open", params));

            assertThat(id.menuId()).isEqualTo("profile");
            assertThat(id.action()).isEqualTo("open");
            assertThat(id.params()).isEqualTo(params);
        }

        @Test
        @DisplayName("round trips a long id at the limit")
        void roundTripAtLimit() {
            String action = "a".repeat(90);

            ComponentId id = decode(ComponentId.encode("m", action));

            assertThat(id.action()).isEqualTo(action);
        }
    }

    @Nested
    @DisplayName("param accessors")
    class ParamAccessors {

        private final ComponentId id = decode("menu:a:act:1:2");

        @Test
        @DisplayName("param returns the value in range")
        void paramInRange() {
            assertThat(id.param(0)).contains("1");
            assertThat(id.param(1)).contains("2");
        }

        @Test
        @DisplayName("param is empty out of range")
        void paramOutOfRange() {
            assertThat(id.param(2)).isEmpty();
            assertThat(id.param(-1)).isEmpty();
        }

        @Test
        @DisplayName("require returns the value in range")
        void requireInRange() {
            assertThat(id.require(1)).isEqualTo("2");
        }

        @Test
        @DisplayName("require throws out of range and names the index")
        void requireOutOfRange() {
            assertThatThrownBy(() -> id.require(5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("index 5");
        }

        @Test
        @DisplayName("the params list is immutable")
        void paramsImmutable() {
            assertThatThrownBy(() -> id.params().add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("isMenuId")
    class IsMenuId {

        @Test
        @DisplayName("accepts ids carrying the prefix")
        void acceptsPrefix() {
            assertThat(ComponentId.isMenuId("menu:a:b")).isTrue();
            assertThat(ComponentId.isMenuId("menu:")).isTrue();
        }

        @Test
        @DisplayName("rejects null and foreign ids")
        void rejectsOthers() {
            assertThat(ComponentId.isMenuId(null)).isFalse();
            assertThat(ComponentId.isMenuId("")).isFalse();
            assertThat(ComponentId.isMenuId("other:a:b")).isFalse();
        }
    }

    @Nested
    @DisplayName("throughput")
    class Throughput {

        /**
         * Regression guard for the constant per-interaction overhead required in
         * section 3 of the plan: decoding runs on every button press, so it must
         * stay allocation-light.
         *
         * <p>The bound is deliberately loose. One million decodes of a
         * four-segment id measure at roughly 90 ms on current hardware, so the
         * 10-second ceiling below leaves more than two orders of magnitude of
         * headroom for slow CI hardware, a cold JVM, and a debug-info build. The
         * assertion is about "no accidental regex or per-call array blowup", not
         * about a throughput number, so a generous bound is the correct choice
         * over a tight one that would flake.
         */
        @Test
        @DisplayName("decodes one million ids without blowing up")
        void decodesOneMillion() {
            int iterations = 1_000_000;
            long boundNanos = 10_000_000_000L;
            long start = System.nanoTime();

            int params = 0;
            for (int i = 0; i < iterations; i++) {
                params +=
                        ComponentId.decode("menu:profile:edit_birth:" + i)
                                .orElseThrow()
                                .paramCount();
            }

            long elapsed = System.nanoTime() - start;

            assertThat(params).isEqualTo(iterations);
            assertThat(elapsed).isLessThan(boundNanos);
        }
    }

    private static ComponentId decode(String customId) {
        return ComponentId.decode(customId).orElseThrow();
    }

    /**
     * Builds a menu id long enough that {@code menu:<menuId>:a} is exactly
     * {@code total} characters. The {@code menu:} prefix is 5 characters, the
     * separator before the action is 1, and the one-character action is 1, so the
     * fixed overhead is 7.
     */
    private static String menuIdOfLength(int total) {
        return "m".repeat(total - 7);
    }
}
