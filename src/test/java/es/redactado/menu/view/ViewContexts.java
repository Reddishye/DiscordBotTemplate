package es.redactado.menu.view;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.core.Messages;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.Preset;
import java.util.Locale;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

/**
 * A {@link MenuContext} good enough to render a menu with.
 *
 * <p>Every view component now reads the preset and resolves user text through its
 * context, so a test that stubs only {@code menuId} renders nothing. The stubs live here
 * because the details are fussy: stubbing {@code t} needs {@code any(Object[].class)}
 * rather than {@code any()}, since a call with no placeholders still arrives as an empty
 * array, and a single-element matcher silently fails to match it.
 */
final class ViewContexts {

    private ViewContexts() {}

    /** A context rendering with the default preset and English text. */
    static MenuContext english() {
        return forPreset(BuiltinPresets.DEFAULT, Locale.ENGLISH);
    }

    /** A context rendering with a chosen preset and English text. */
    static MenuContext forPreset(Preset preset) {
        return forPreset(preset, Locale.ENGLISH);
    }

    /**
     * A context rendering with a chosen preset and locale.
     *
     * @param preset the preset the components should read
     * @param locale the locale {@code t} resolves against
     * @return the context
     */
    static MenuContext forPreset(Preset preset, Locale locale) {
        MenuContext ctx = mock(MenuContext.class, CALLS_REAL_METHODS);
        User user = mock(User.class);
        Guild guild = mock(Guild.class);
        // A real session, not a mock: pagers read and write page state through it, and a
        // mocked session returns null and fails somewhere inside the component.
        Session session = new Session();
        when(user.getEffectiveName()).thenReturn("Ada");
        when(user.getIdLong()).thenReturn(42L);
        when(guild.getIdLong()).thenReturn(99L);
        when(ctx.menuId()).thenReturn("m");
        // A rendered view has an action; a pager embeds it in its button ids.
        when(ctx.action()).thenReturn("home");
        when(ctx.preset()).thenReturn(preset);
        when(ctx.locale()).thenReturn(locale);
        when(ctx.discordUser()).thenReturn(user);
        when(ctx.guild()).thenReturn(guild);
        when(ctx.guildId()).thenReturn("99");
        when(ctx.userId()).thenReturn("42");
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(java.util.Optional.of(session));
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            // The invocation reports its varargs spread out, so the key is
                            // argument 0 and everything after it is a placeholder value.
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(locale, (String) all[0], args);
                        });
        return ctx;
    }
}
