package es.redactado.feature;

import es.redactado.service.IService;

/**
 * A business service a {@link BotFeature} asked to start after the first ready event.
 *
 * <p>A record rather than a raw class, for the same reason as {@link InfrastructureService}.
 */
public record BusinessService(Class<? extends IService> type) {}
