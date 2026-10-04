package es.redactado.config;

import es.redactado.service.IService;
import es.redactado.service.MenuService;
import es.redactado.service.TaskManager;
import java.util.List;

public class Services {

    /**
     * Started before JDA connects.
     * Safe for: database, cache, repositories, config loaders.
     * Must NOT depend on ShardManager.
     */
    public static final List<Class<? extends IService>> INFRASTRUCTURE_SERVICES =
            List.of(TaskManager.class, MenuService.class);

    /**
     * Started after JDA fires its first ReadyEvent.
     * Safe for: anything that needs ShardManager, guild data, or Discord API.
     */
    public static final List<Class<? extends IService>> BUSINESS_SERVICES = List.of();
}
