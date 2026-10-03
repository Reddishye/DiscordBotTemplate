package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.ComponentId;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.ButtonRole;
import es.redactado.menu.preset.IconKey;
import es.redactado.menu.preset.Preset;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks that a confirmation renders two buttons that do what they say.
 *
 * <p>Both ids matter: the confirm button must run the action the caller named with the
 * params it passed, and the cancel button must be a back so that leaving costs nothing.
 */
class ConfirmTest {

    @Test
    @DisplayName("the prompt comes first, then a row with the two answers")
    void rendersPromptThenRow() {
        List<ContainerChildComponent> children =
                Confirm.of(Text.of("Delete this?"), "reallyDelete", "42")
                        .render(ViewContexts.forPreset(BuiltinPresets.DEFAULT));

        assertThat(children).hasSize(2);
        assertThat(children.get(0))
                .isInstanceOf(net.dv8tion.jda.api.components.textdisplay.TextDisplay.class);
        assertThat(children.get(1)).isInstanceOf(ActionRow.class);
    }

    @Test
    @DisplayName("the confirm button runs the named action with its params")
    void confirmRunsTheAction() {
        Button confirm =
                buttons(
                                Confirm.of(Text.of("?"), "reallyDelete", "42", "extra")
                                        .render(ViewContexts.forPreset(BuiltinPresets.DEFAULT)))
                        .get(0);

        ComponentId id = ComponentId.decode(confirm.getCustomId()).orElseThrow();
        assertThat(id.menuId()).isEqualTo("m");
        assertThat(id.action()).isEqualTo("reallyDelete");
        assertThat(id.params()).containsExactly("42", "extra");
    }

    @Test
    @DisplayName("the cancel button goes back rather than running an action")
    void cancelGoesBack() {
        Button cancel =
                buttons(
                                Confirm.of(Text.of("?"), "reallyDelete")
                                        .render(ViewContexts.forPreset(BuiltinPresets.DEFAULT)))
                        .get(1);

        ComponentId id = ComponentId.decode(cancel.getCustomId()).orElseThrow();
        assertThat(id.action()).isEqualTo("nav");
        assertThat(id.params()).containsExactly("back");
    }

    @Test
    @DisplayName("the confirm button uses the success role by default")
    void defaultRoleIsSuccess() {
        for (Preset preset : BuiltinPresets.all()) {
            Button confirm = buttons(Confirm.of(Text.of("?"), "go").render(context(preset))).get(0);

            assertThat(confirm.getStyle())
                    .as("%s", preset.name())
                    .isEqualTo(preset.buttons().of(ButtonRole.SUCCESS));
        }
    }

    @Test
    @DisplayName("danger() uses the danger role instead")
    void dangerUsesDangerRole() {
        for (Preset preset : BuiltinPresets.all()) {
            Button confirm =
                    buttons(Confirm.of(Text.of("?"), "go").danger().render(context(preset))).get(0);

            assertThat(confirm.getStyle())
                    .as("%s", preset.name())
                    .isEqualTo(preset.buttons().of(ButtonRole.DANGER));
        }
    }

    @Test
    @DisplayName("the labels default to the bundles and are localized")
    void labelsDefaultToBundles() {
        ButtonRow english = buttonsOf(BuiltinPresets.DEFAULT, Locale.ENGLISH, false);
        ButtonRow spanish =
                buttonsOf(BuiltinPresets.DEFAULT, Locale.forLanguageTag("es-ES"), false);

        assertThat(english.confirm.getLabel()).isEqualTo("Confirm");
        assertThat(english.cancel.getLabel()).isEqualTo("Cancel");
        assertThat(spanish.confirm.getLabel()).isEqualTo("Confirmar");
        assertThat(spanish.cancel.getLabel()).isEqualTo("Cancelar");
    }

    @Test
    @DisplayName("custom labels replace the defaults")
    void customLabels() {
        Button row =
                buttons(
                                Confirm.of(Text.of("?"), "go")
                                        .yesLabel("Do it")
                                        .noLabel("Never mind")
                                        .render(ViewContexts.forPreset(BuiltinPresets.DEFAULT)))
                        .get(0);

        assertThat(row.getLabel()).isEqualTo("Do it");
        assertThat(
                        buttons(
                                        Confirm.of(Text.of("?"), "go")
                                                .yesLabel("Do it")
                                                .noLabel("Never mind")
                                                .render(
                                                        ViewContexts.forPreset(
                                                                BuiltinPresets.DEFAULT)))
                                .get(1)
                                .getLabel())
                .isEqualTo("Never mind");
    }

    @Test
    @DisplayName("the icons come from the preset and vanish when it defines none")
    void iconsFollowThePreset() {
        Button withIcons = buttonsOf(BuiltinPresets.DEFAULT, Locale.ENGLISH, false).confirm;
        Button withoutIcons = buttonsOf(BuiltinPresets.MINIMAL, Locale.ENGLISH, false).confirm;

        assertThat(withIcons.getEmoji().getFormatted())
                .isEqualTo(BuiltinPresets.DEFAULT.icons().formatted(IconKey.CONFIRM));
        assertThat(withoutIcons.getEmoji())
                .as("minimal defines no icons, so the button falls back to its label")
                .isNull();
    }

    @Test
    @DisplayName("a prompt and an action are both required")
    void requiredArguments() {
        assertThatThrownBy(() -> Confirm.of(null, "go"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs a prompt");
        assertThatThrownBy(() -> Confirm.of(Text.of("?"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("needs an action");
    }

    // ------------------------------------------------------------- helpers

    private record ButtonRow(Button confirm, Button cancel) {}

    private static ButtonRow buttonsOf(Preset preset, Locale locale, boolean danger) {
        MenuContext ctx = ViewContexts.forPreset(preset, locale);
        Confirm confirm = Confirm.of(Text.of("?"), "go");
        if (danger) {
            confirm = confirm.danger();
        }
        List<Button> found = buttons(confirm.render(ctx));
        return new ButtonRow(found.get(0), found.get(1));
    }

    private static MenuContext context(Preset preset) {
        return ViewContexts.forPreset(preset);
    }

    private static List<Button> buttons(List<ContainerChildComponent> children) {
        List<Button> found = new java.util.ArrayList<>();
        for (ContainerChildComponent child : children) {
            if (child instanceof ActionRow row) {
                row.getComponents().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .forEach(found::add);
            }
        }
        return found;
    }

    /** Keeps the unused-key checker honest about what this test pins. */
    @Test
    @DisplayName("the confirm keys are the ones the defaults resolve")
    void keysResolve() {
        assertThat(MessageKeys.CONFIRM_YES).isEqualTo("menu.confirm.yes");
        assertThat(MessageKeys.CONFIRM_NO).isEqualTo("menu.confirm.no");
    }
}
