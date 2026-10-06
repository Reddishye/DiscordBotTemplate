package es.redactado.feature;

import es.redactado.service.IService;

/** A service registered with {@link BotFeature#ready}. */
public record BusinessService(Class<? extends IService> type) {}
