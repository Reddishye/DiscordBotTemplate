package es.redactado.config;

import es.redactado.command.handler.CommandListener;
import es.redactado.command.handler.MenuListener;
import java.util.List;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class Listeners {
    /**
     * Always registered. A feature adds more with {@link
     * es.redactado.feature.BotFeature#listener}.
     */
    public static final List<Class<? extends ListenerAdapter>> LISTENERS =
            List.of(CommandListener.class, MenuListener.class);
}
