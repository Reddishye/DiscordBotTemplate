package es.redactado.feature;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** A listener registered with {@link BotFeature#listener}. */
public record ListenerBinding(Class<? extends ListenerAdapter> type) {}
