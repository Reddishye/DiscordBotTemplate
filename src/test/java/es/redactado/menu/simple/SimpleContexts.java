package es.redactado.menu.simple;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import es.redactado.menu.core.Messages;
import es.redactado.menu.preset.Preset;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.entities.User;

/**
 * A context good enough to render a simple menu with.
 *
 * <p>The same shape as the view tests' fixture: a real session, because a pager writes page
 * state through it and a mocked session answers null instead of empty, and a {@code t} that
 * answers from the real bundles so a key renders as words rather than as itself.
 */
final class SimpleContexts {

    private SimpleContexts() {}

    /**
     * A context for one menu, one view and one preset.
     *
     * @param menuId the menu the context belongs to
     * @param view the view to render
     * @param preset the preset to render with
     * @param locale the locale {@code t} resolves against
     * @return the context
     */
    static MenuContext of(String menuId, String view, Preset preset, Locale locale) {
        return of(menuId, view, preset, locale, new Session());
    }

    /**
     * A context sharing one session, so paging state survives a redraw.
     *
     * @param menuId the menu the context belongs to
     * @param view the view to render
     * @param preset the preset to render with
     * @param locale the locale {@code t} resolves against
     * @param session the session the context reads and writes
     * @return the context
     */
    static MenuContext of(
            String menuId, String view, Preset preset, Locale locale, Session session) {
        MenuContext ctx = mock(MenuContext.class);
        User user = mock(User.class);
        when(user.getEffectiveName()).thenReturn("Ada");
        when(user.getIdLong()).thenReturn(42L);
        when(ctx.menuId()).thenReturn(menuId);
        when(ctx.action()).thenReturn(view);
        when(ctx.params()).thenReturn(List.of());
        when(ctx.preset()).thenReturn(preset);
        when(ctx.locale()).thenReturn(locale);
        when(ctx.discordUser()).thenReturn(user);
        when(ctx.userId()).thenReturn("42");
        when(ctx.guildId()).thenReturn("99");
        when(ctx.session()).thenReturn(session);
        when(ctx.findSession()).thenReturn(java.util.Optional.of(session));
        when(ctx.t(anyString(), any(Object[].class)))
                .thenAnswer(
                        call -> {
                            Object[] all = call.getArguments();
                            Object[] args = new Object[all.length - 1];
                            System.arraycopy(all, 1, args, 0, args.length);
                            return Messages.standard().get(locale, (String) all[0], args);
                        });
        return ctx;
    }
}
