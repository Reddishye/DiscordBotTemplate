package es.redactado.config;

import es.redactado.database.DatabaseManager;
import es.redactado.service.IService;
import es.redactado.service.MenuService;
import es.redactado.service.TaskManager;
import java.util.List;

public class Services {

    /**
     * Started before JDA connects. A feature appends its own services through {@link
     * es.redactado.feature.BotFeature#infrastructure}. Must not depend on ShardManager.
     */
    public static final List<Class<? extends IService>> INFRASTRUCTURE_SERVICES =
            List.of(TaskManager.class, DatabaseManager.class, MenuService.class);

    /**
     * Started after the first ready event. Empty on purpose: a feature adds its own with {@link
     * es.redactado.feature.BotFeature#business}.
     */
    public static final List<Class<? extends IService>> BUSINESS_SERVICES = List.of();
}
