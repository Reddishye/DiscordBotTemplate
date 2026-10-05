package es.redactado.feature;

import es.redactado.service.IService;

/**
 * An infrastructure service a {@link BotFeature} asked to start.
 *
 * <p>A record rather than a raw class, because Guice's set binder needs a concrete type and
 * {@code Class} is not one it can bind cleanly.
 */
public record InfrastructureService(Class<? extends IService> type) {}
