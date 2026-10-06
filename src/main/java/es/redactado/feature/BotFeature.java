package es.redactado.feature;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import es.redactado.command.type.BaseMessageContextCommand;
import es.redactado.command.type.BaseSlashCommand;
import es.redactado.command.type.BaseUserContextCommand;
import es.redactado.database.ManagedEntity;
import es.redactado.database.MigrationScript;
import es.redactado.service.IService;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Registers one part of the bot.
 *
 * <p>The bot's own registrations live in {@link es.redactado.config.TemplateBindings}. Add a line
 * there. A class in another jar extends this type and is named, one class per line, in {@code
 * META-INF/services/es.redactado.feature.BotFeature}.
 *
 * <p>Call the methods below from {@link #contribute()}. They record classes. They do not start
 * anything. {@code Main} starts services and registers listeners after the injector exists.
 */
public abstract class BotFeature extends AbstractModule {

    @Override
    protected final void configure() {
        Multibinder.newSetBinder(binder(), InfrastructureService.class);
        Multibinder.newSetBinder(binder(), BusinessService.class);
        Multibinder.newSetBinder(binder(), ListenerBinding.class);
        Multibinder.newSetBinder(binder(), MigrationScript.class);
        Multibinder.newSetBinder(binder(), BaseSlashCommand.class);
        Multibinder.newSetBinder(binder(), BaseMessageContextCommand.class);
        Multibinder.newSetBinder(binder(), BaseUserContextCommand.class);
        Multibinder.newSetBinder(binder(), ManagedEntity.class);
        contribute();
    }

    /** Add this feature's commands, listeners, services, entities and migrations. */
    protected abstract void contribute();

    /** Slash command, indexed by the name in its command data. */
    protected final void slashCommand(Class<? extends BaseSlashCommand> type) {
        Multibinder.newSetBinder(binder(), BaseSlashCommand.class).addBinding().to(type);
    }

    /** Command shown when someone right-clicks a message. */
    protected final void messageCommand(Class<? extends BaseMessageContextCommand> type) {
        Multibinder.newSetBinder(binder(), BaseMessageContextCommand.class).addBinding().to(type);
    }

    /** Command shown when someone right-clicks a user. */
    protected final void userCommand(Class<? extends BaseUserContextCommand> type) {
        Multibinder.newSetBinder(binder(), BaseUserContextCommand.class).addBinding().to(type);
    }

    /** Hibernate class. Included in the session factory. */
    protected final void entity(Class<?> type) {
        Multibinder.newSetBinder(binder(), ManagedEntity.class)
                .addBinding()
                .toInstance(new ManagedEntity(type));
    }

    /** Gateway listener. {@code Main} registers it on the shard manager. */
    protected final void listener(Class<? extends ListenerAdapter> type) {
        Multibinder.newSetBinder(binder(), ListenerBinding.class)
                .addBinding()
                .toInstance(new ListenerBinding(type));
    }

    /**
     * Service started before the bot connects. Use this for the database, caches and anything
     * else that does not call Discord yet.
     */
    protected final void service(Class<? extends IService> type) {
        Multibinder.newSetBinder(binder(), InfrastructureService.class)
                .addBinding()
                .toInstance(new InfrastructureService(type));
    }

    /**
     * Service started once Discord has sent ready. Use this when the service looks up guilds or
     * sends messages during {@code init}.
     */
    protected final void ready(Class<? extends IService> type) {
        Multibinder.newSetBinder(binder(), BusinessService.class)
                .addBinding()
                .toInstance(new BusinessService(type));
    }

    /**
     * SQL file for one database kind: {@code sqlite}, {@code h2} or {@code mariadb}.
     *
     * <p>{@code version} is the number in the file name ({@code V2__notes.sql} is version 2). Each
     * number is used once per database kind. The template's scripts are version 1.
     *
     * @param resource classpath path, for example {@code /db/migration/sqlite/V2__notes.sql}
     */
    protected final void migration(String dialect, int version, String resource) {
        Multibinder.newSetBinder(binder(), MigrationScript.class)
                .addBinding()
                .toInstance(new MigrationScript(dialect, version, resource));
    }
}
