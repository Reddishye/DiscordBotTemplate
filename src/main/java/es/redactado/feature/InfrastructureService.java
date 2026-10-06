package es.redactado.feature;

import es.redactado.service.IService;

/** A service registered with {@link BotFeature#service}. */
public record InfrastructureService(Class<? extends IService> type) {}
