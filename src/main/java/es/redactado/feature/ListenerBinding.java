package es.redactado.feature;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * A gateway listener a {@link BotFeature} asked {@code Main} to register.
 *
 * <p>A record rather than a raw class, for the same reason as {@link InfrastructureService}.
 */
public record ListenerBinding(Class<? extends ListenerAdapter> type) {}
