package es.redactado.menu.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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
 * Every view of the profile example, under every built-in preset.
 *
 * <p>The profile example is the densest view in the codebase: two pagers, two fields, a
 * confirmation and a row of three buttons in one container, and a role view whose rows are
 * themselves rows. A preset that adds a footer line, or a header style with more room, could
 * push any of that past Discord's limits, and that has to fail here rather than on a bot.
 *
 * <p>A fresh menu and a fresh service per case, because a case that leaves a cached profile
 * behind would change what the next one renders.
 */
class ProfileExampleRenderTest {

    /** The role view's id parameter, which is the only view that takes one. */
    private static final String ROLE_PARAM = "1001";

    static Stream<Arguments> presetsAndViews() {
        List<Arguments> cases = new ArrayList<>();
        List<String> views =
                List.of(
                        ProfileExampleMenu.HOME,
                        ProfileExampleMenu.ROLE,
                        ProfileExampleMenu.LINKS,
                        ProfileExampleMenu.CONFIRM_REMOVE);
        for (Preset preset : BuiltinPresets.all()) {
            for (String view : views) {
                cases.add(Arguments.of(view, preset, menu()));
            }
        }
        return cases.stream();
    }

    private static Supplier<ProfileExampleMenu> menu() {
        return () -> ProfileExampleMenu.using(new FakeProfileService(0));
    }

    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("presetsAndViews")
    @DisplayName("every view of the profile example renders inside Discord's limits")
    void everyViewRendersWithinLimits(
            String view, Preset preset, Supplier<ProfileExampleMenu> built) {
        ProfileExampleMenu menu = built.get();
        try (menu) {
            Container container =
                    menu.render(context(view, preset, List.of(ROLE_PARAM), new Session())).join();

            ValidationResult result = Validator.validate(container);
            assertThat(result.isValid())
                    .as(
                            "view '%s' under %s must be valid, but: %s",
                            view, preset.name(), result.errors())
                    .isTrue();
            assertThat(container.getComponents().size())
                    .as("view '%s' under %s child count", view, preset.name())
                    .isLessThanOrEqualTo(Limits.MAX_CONTAINER_CHILDREN);
            assertThat(container.getAccentColorRaw())
                    .as("view '%s' renders in the preset it was given", view)
                    .isEqualTo(preset.palette().accent());
        }
    }

    /** A context for one view with one preset, resolving keys from the real bundles. */
    static MenuContext context(String view, Preset preset, List<String> params, Session session) {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        User user = mock(User.class);
        when(user.getEffectiveName()).thenReturn("Ada");
        when(user.getIdLong()).thenReturn(1L);
        when(ctx.menuId()).thenReturn(ProfileExampleMenu.ID);
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(params);
        when(ctx.preset()).thenReturn(preset);
        when(ctx.locale()).thenReturn(Locale.ENGLISH);
        // A real session, because both views hold a pager and a pager reads its page from here.
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(Optional.of(session));
        when(ctx.userId()).thenReturn("1");
        when(ctx.discordUser()).thenReturn(user);
        // requireLong is abstract on the interface, so a mock answers 0 for it. The real
        // context parses the param out of the id, and the end-to-end test is where that
        // parsing is proved; here the fixture just does the same arithmetic.
        when(ctx.requireLong(anyInt()))
                .thenAnswer(call -> Long.parseLong(params.get(call.<Integer>getArgument(0))));
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
