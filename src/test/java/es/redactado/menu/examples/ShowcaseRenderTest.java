package es.redactado.menu.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.ValidationResult;
import es.redactado.menu.api.Validator;
import es.redactado.menu.core.Messages;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetRegistry;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Renders every view of the showcase under every built-in preset.
 *
 * <p>The showcase exists to be looked at, so the first thing worth proving is that it draws
 * something legal in every look: a preset that made the header taller or the rows busier
 * could push a view past Discord's limits, and that has to fail here rather than on a bot.
 */
class ShowcaseRenderTest {

    /** The views, as the action that selects them. */
    private static final List<String> VIEWS =
            List.of(
                    ShowcaseMenu.HOME,
                    ShowcaseMenu.COMPONENTS,
                    ShowcaseMenu.PRESETS,
                    ShowcaseMenu.CONFIRM,
                    ShowcaseMenu.MODAL);

    static Stream<Arguments> presetsAndViews() {
        return BuiltinPresets.all().stream()
                .flatMap(
                        preset ->
                                VIEWS.stream()
                                        .map(view -> Arguments.of(preset.name(), preset, view)));
    }

    @ParameterizedTest(name = "{0}/{2}")
    @MethodSource("presetsAndViews")
    @DisplayName("every view of every preset renders inside Discord's limits")
    void everyViewRendersWithinLimits(String presetName, Preset preset, String view) {
        Container container = render(preset, view);

        ValidationResult result = Validator.validate(container);
        assertThat(result.isValid())
                .as("%s view '%s' must render within Discord's limits", presetName, view)
                .isTrue();
        assertThat(container.getComponents().size())
                .as("%s view '%s' child count", presetName, view)
                .isLessThanOrEqualTo(Limits.MAX_CONTAINER_CHILDREN);
        assertThat(container.getAccentColorRaw())
                .as("%s view '%s' must render in the preset it was given", presetName, view)
                .isEqualTo(preset.palette().accent());
    }

    private static Container render(Preset preset, String view) {
        // join is the completion check: render is a pure function of the context, and a
        // future that has not completed would make every assertion below meaningless.
        return new ShowcaseMenu(new PresetRegistry())
                .render(context(preset, view, new Session()))
                .join();
    }

    /**
     * A context that resolves a view with a preset, without a gateway.
     *
     * <p>A real session rather than a mocked one, because the showcase reads its preset
     * preview and its fake items out of it.
     */
    /**
     * A context for one view with one preset.
     *
     * <p>{@code withPreset} answers with a fresh context sharing the same session, which is
     * what a real one does: only the look changes when a menu previews a preset, and the
     * fake items and the chosen preset live in the session both contexts must see.
     */
    private static MenuContext context(Preset preset, String view, Session session) {
        MenuContext ctx = mock(MenuContext.class);
        User user = mock(User.class);
        when(user.getEffectiveName()).thenReturn("Ada");
        when(user.getIdLong()).thenReturn(42L);
        when(ctx.menuId()).thenReturn("showcase");
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(preset);
        when(ctx.withPreset(any())).thenAnswer(call -> context(call.getArgument(0), view, session));
        when(ctx.discordUser()).thenReturn(user);
        // A real session, not a mocked one: the showcase reads its preset preview and its
        // fake items out of it, and a mocked session answers null instead of empty.
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(java.util.Optional.of(session));
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(Locale.ENGLISH, (String) all[0], args);
                        });
        return ctx;
    }
}
