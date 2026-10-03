package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;

import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import es.redactado.menu.preset.Preset;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that navigation buttons encode what they promise.
 *
 * <p>The id is the whole contract here: the router reads the mode and target back out of
 * it, and a wrong id is a button that silently navigates somewhere nobody intended.
 */
class NavTest {

    @Test
    @DisplayName("back encodes the back mode and no target")
    void backEncodesBack() {
        Button button = render(Nav.back(), BuiltinPresets.DEFAULT);

        ComponentId id = ComponentId.decode(button.getCustomId()).orElseThrow();
        assertThat(id.menuId()).isEqualTo("m");
        assertThat(id.action()).isEqualTo("nav");
        assertThat(id.params()).containsExactly("back");
    }

    @Test
    @DisplayName("push, replace and root each encode their own mode and the target")
    void modesEncodeTheirTargets() {
        assertParams(Nav.push("other", "Details"), "push", "other");
        assertParams(Nav.replace("other", "Details"), "replace", "other");
        assertParams(Nav.root("other", "Details"), "root", "other");
    }

    @Test
    @DisplayName("the mode survives a round trip through the id")
    void modesDecodeBack() {
        Button button = render(Nav.root("other", "Details"), BuiltinPresets.DEFAULT);
        ComponentId id = ComponentId.decode(button.getCustomId()).orElseThrow();

        assertThat(NavigationMode.valueOf(id.require(0).toUpperCase(Locale.ROOT)))
                .isEqualTo(NavigationMode.ROOT);
        assertThat(id.require(1)).isEqualTo("other");
    }

    @Test
    @DisplayName("back takes its label from the bundle, the others from the caller")
    void labelsFollowTheirSource() {
        assertThat(render(Nav.back(), BuiltinPresets.DEFAULT).getLabel()).isEqualTo("Back");
        assertThat(render(Nav.push("other", "Details"), BuiltinPresets.DEFAULT).getLabel())
                .isEqualTo("Details");
    }

    @Test
    @DisplayName("the back label is localized")
    void backLabelIsLocalized() {
        Button spanish =
                (Button)
                        Nav.back()
                                .render(
                                        ViewContexts.forPreset(
                                                BuiltinPresets.DEFAULT,
                                                Locale.forLanguageTag("es-ES")));

        assertThat(spanish.getLabel()).isEqualTo("Atr\u00E1s");
    }

    @Test
    @DisplayName("back carries the BACK icon, resolved from the preset")
    void backCarriesBackIcon() {
        Button button = render(Nav.back(), BuiltinPresets.DEFAULT);

        assertThat(button.getEmoji()).isNotNull();
        assertThat(button.getEmoji().getFormatted())
                .isEqualTo(BuiltinPresets.DEFAULT.icons().formatted(IconKey.BACK));
    }

    @Test
    @DisplayName("a preset with no icons renders the button with none")
    void noIconWhenPresetHasNone() {
        assertThat(BuiltinPresets.MINIMAL.icons().formatted(IconKey.BACK)).isEmpty();

        assertThat(render(Nav.back(), BuiltinPresets.MINIMAL).getEmoji()).isNull();
    }

    @Test
    @DisplayName("the other modes carry no icon unless one is asked for")
    void optionalIcon() {
        Button plain = render(Nav.push("other", "Details"), BuiltinPresets.DEFAULT);
        Button withIcon =
                render(Nav.push("other", "Details").icon(IconKey.NEXT), BuiltinPresets.DEFAULT);

        assertThat(plain.getEmoji()).isNull();
        assertThat(withIcon.getEmoji()).isNotNull();
        assertThat(withIcon.getEmoji().getFormatted())
                .isEqualTo(BuiltinPresets.DEFAULT.icons().formatted(IconKey.NEXT));
    }

    @Test
    @DisplayName("every navigation button uses the preset's secondary style")
    void styleComesFromThePreset() {
        for (Preset preset : BuiltinPresets.all()) {
            for (Nav button :
                    List.of(
                            Nav.back(),
                            Nav.push("other", "Details"),
                            Nav.replace("other", "Details"),
                            Nav.root("other", "Details"))) {
                assertThat(render(button, preset).getStyle())
                        .as("%s button for %s", preset.name(), button)
                        .isEqualTo(preset.buttons().of(ButtonRole.SECONDARY));
            }
        }
    }

    private static void assertParams(Nav nav, String mode, String target) {
        ComponentId id =
                ComponentId.decode(render(nav, BuiltinPresets.DEFAULT).getCustomId()).orElseThrow();

        assertThat(id.action()).isEqualTo("nav");
        assertThat(id.params()).containsExactly(mode, target);
    }

    private static Button render(Nav nav, Preset preset) {
        return (Button) nav.render(ViewContexts.forPreset(preset));
    }
}
