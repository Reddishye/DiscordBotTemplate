package es.redactado.menu.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.ValidationResult;
import es.redactado.menu.api.Validator;
import es.redactado.menu.core.Messages;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every view of every example, under every built-in preset.
 *
 * <p>The examples exist to be read, so the first thing worth proving is that what a reader
 * would run draws something legal in every look: a preset that made a header taller or a row
 * busier could push a view past Discord's limits, and that has to fail here rather than on a
 * bot.
 *
 * <p>A fresh menu is built per case rather than shared, because the state one case leaves in
 * its session would otherwise change what the next case renders.
 */
class SimpleExamplesRenderTest {

    static Stream<Arguments> presetsAndViews() {
        List<Arguments> cases = new ArrayList<>();
        for (Preset preset : BuiltinPresets.all()) {
            cases.add(
                    Arguments.of(
                            "help/home", HelpMenu.ID, HelpMenu.HOME, preset, Tone.INFO, help()));
            cases.add(
                    Arguments.of("help/faq", HelpMenu.ID, HelpMenu.FAQ, preset, Tone.INFO, help()));
            cases.add(
                    Arguments.of(
                            "counter/home",
                            CounterMenu.ID,
                            CounterMenu.HOME,
                            preset,
                            Tone.SUCCESS,
                            counter()));
            cases.add(
                    Arguments.of(
                            "counter/confirm",
                            CounterMenu.ID,
                            CounterMenu.CONFIRM,
                            preset,
                            Tone.SUCCESS,
                            counter()));
            cases.add(
                    Arguments.of(
                            "server_info/home",
                            ServerInfoMenu.ID,
                            ServerInfoMenu.HOME,
                            preset,
                            Tone.INFO,
                            serverInfo()));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} under {3}")
    @MethodSource("presetsAndViews")
    @DisplayName("every view of every example renders inside Discord's limits")
    void everyExampleViewRendersWithinLimits(
            String name,
            String menuId,
            String view,
            Preset preset,
            Tone tone,
            Supplier<Menu> built) {
        Container container = built.get().render(context(menuId, view, preset)).join();

        ValidationResult result = Validator.validate(container);
        assertThat(result.isValid())
                .as("%s must render a valid container, but: %s", name, result.errors())
                .isTrue();
        assertThat(container.getComponents().size())
                .as("%s child count", name)
                .isLessThanOrEqualTo(Limits.MAX_CONTAINER_CHILDREN);
        assertThat(container.getAccentColorRaw())
                .as(
                        "%s is a %s menu, so it takes that colour from the preset it was given",
                        name, tone)
                .isEqualTo(toneColor(preset, tone));
    }

    /** The colour a tone resolves to in a preset, which is what a container's accent is. */
    private static int toneColor(Preset preset, Tone tone) {
        return switch (tone) {
            case ACCENT -> preset.palette().accent();
            case SUCCESS -> preset.palette().success();
            case WARNING -> preset.palette().warning();
            case DANGER -> preset.palette().danger();
            case INFO -> preset.palette().info();
            case NEUTRAL -> preset.palette().neutral();
        };
    }

    private static Supplier<Menu> help() {
        return HelpMenu::build;
    }

    private static Supplier<Menu> counter() {
        return CounterMenu::build;
    }

    private static Supplier<Menu> serverInfo() {
        // A one-millisecond service: enough to be asynchronous, short enough not to be slow.
        return () -> ServerInfoMenu.build(new ServerInfoMenu.FakeService(1));
    }

    /** A context for one view with one preset, resolving keys from the real bundles. */
    private static MenuContext context(String menuId, String view, Preset preset) {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        User user = mock(User.class);
        Session session = new Session();
        when(user.getEffectiveName()).thenReturn("Ada");
        when(user.getIdLong()).thenReturn(42L);
        when(ctx.menuId()).thenReturn(menuId);
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(preset);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        // A real session, not a mocked one: the counter reads its count out of it.
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(Optional.of(session));
        when(ctx.userId()).thenReturn("42");
        when(ctx.discordUser()).thenReturn(user);
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
