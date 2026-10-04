package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;

import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.NavigationAction;
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
    @DisplayName("a view target names the current menu and the view after it")
    void viewNamesTheCurrentMenu() {
        Button button = render(Nav.view("details", "Details"), BuiltinPresets.DEFAULT);

        ComponentId id = ComponentId.decode(button.getCustomId()).orElseThrow();
        assertThat(id.menuId()).as("the menu doing the rendering").isEqualTo("m");
        assertThat(id.action()).isEqualTo("nav");
        assertThat(id.params()).containsExactly("push", "m", "details");
    }

    @Test
    @DisplayName("a view target carries its params after the view")
    void viewCarriesParams() {
        Button button = render(Nav.view("details", "Details", "42", "x"), BuiltinPresets.DEFAULT);

        assertThat(ComponentId.decode(button.getCustomId()).orElseThrow().params())
                .containsExactly("push", "m", "details", "42", "x");
    }

    @Test
    @DisplayName("swap replaces the view of the current menu")
    void swapReplaces() {
        Button button = render(Nav.swap("filters", "Filters"), BuiltinPresets.DEFAULT);

        assertThat(ComponentId.decode(button.getCustomId()).orElseThrow().params())
                .containsExactly("replace", "m", "filters");
    }

    @Test
    @DisplayName("a view of another menu keeps the menu it was given")
    void toAnotherMenu() {
        Button button =
                render(Nav.to("settings", "appearance", "Appearance"), BuiltinPresets.DEFAULT);

        ComponentId id = ComponentId.decode(button.getCustomId()).orElseThrow();
        assertThat(id.menuId()).as("the menu rendering the button").isEqualTo("m");
        assertThat(id.params()).containsExactly("push", "settings", "appearance");
    }

    @Test
    @DisplayName("a menu target keeps the short id, with no view segment")
    void menuTargetKeepsTheShortId() {
        Button button = render(Nav.push("other", "Other"), BuiltinPresets.DEFAULT);

        assertThat(button.getCustomId()).isEqualTo("menu:m:nav:push:other");
    }

    @Test
    @DisplayName("a view target survives the round trip through the router's parser")
    void viewTargetsRoundTrip() {
        for (Nav nav :
                List.of(
                        Nav.view("details", "Details", "42"),
                        Nav.swap("filters", "Filters"),
                        Nav.to("settings", "appearance", "Appearance"))) {
            NavigationAction parsed =
                    NavigationAction.fromContext(
                            contextWithParams(paramsOf(render(nav, BuiltinPresets.DEFAULT))));

            assertThat(parsed.target())
                    .as("%s must decode back to what it encoded", nav.getClass().getSimpleName())
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("every navigation style comes from the preset, view targets included")
    void everyStyleComesFromThePreset() {
        for (Preset preset : BuiltinPresets.all()) {
            for (Nav nav :
                    List.of(
                            Nav.view("details", "Details"),
                            Nav.swap("filters", "Filters"),
                            Nav.to("settings", "appearance", "Appearance"))) {
                assertThat(render(nav, preset).getStyle())
                        .as("%s view navigation", preset.name())
                        .isEqualTo(preset.buttons().of(ButtonRole.SECONDARY));
            }
        }
    }

    @Test
    @DisplayName("a view target takes an icon and a label override like the others")
    void viewTakesIconAndLabel() {
        Nav plain = Nav.view("details", "Details");
        Nav labelled = Nav.view("details", "Details").label("See details");
        Nav icon = Nav.view("details", "Details").icon(IconKey.NEXT);

        assertThat(render(plain, BuiltinPresets.DEFAULT).getLabel()).isEqualTo("Details");
        assertThat(render(labelled, BuiltinPresets.DEFAULT).getLabel()).isEqualTo("See details");
        assertThat(render(icon, BuiltinPresets.DEFAULT).getEmoji()).isNotNull();
        assertThat(render(icon, BuiltinPresets.DEFAULT).getEmoji().getFormatted())
                .isEqualTo(BuiltinPresets.DEFAULT.icons().formatted(IconKey.NEXT));
        assertThat(render(plain, BuiltinPresets.DEFAULT).getEmoji())
                .as("no icon unless asked for")
                .isNull();
    }

    @Test
    @DisplayName("a view label is localized like any other caller-supplied one")
    void viewLabelIsLocalized() {
        Button spanish =
                (Button)
                        Nav.view("details", "Detalles")
                                .render(
                                        ViewContexts.forPreset(
                                                BuiltinPresets.DEFAULT,
                                                Locale.forLanguageTag("es-ES")));

        assertThat(spanish.getLabel()).isEqualTo("Detalles");
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

    private static List<String> paramsOf(Button button) {
        return ComponentId.decode(button.getCustomId()).orElseThrow().params();
    }

    /** A context carrying the id parameters, as the router builds one for a nav click. */
    private static es.redactado.menu.api.MenuContext contextWithParams(List<String> params) {
        es.redactado.menu.api.MenuContext ctx =
                org.mockito.Mockito.mock(es.redactado.menu.api.MenuContext.class);
        org.mockito.Mockito.when(ctx.params()).thenReturn(params);
        org.mockito.Mockito.when(ctx.requireString(0)).thenReturn(params.get(0));
        for (int i = 1; i < params.size(); i++) {
            org.mockito.Mockito.when(ctx.param(i)).thenReturn(java.util.Optional.of(params.get(i)));
        }
        return ctx;
    }
}
