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
 * One piece of a bot: the commands, listeners, services, tables and SQL it owns.
 *
 * <p>Extend this, implement {@link #contribute()}, and list the class in {@code
 * META-INF/services/es.redactado.feature.BotFeature}. {@link
 * es.redactado.config.TemplateBindings} is the piece this repository already ships, and {@code
 * BotModule} installs it directly, so it is not in that file. A jar on the classpath is picked
 * up the same way, which is how a bot grows without editing the template's lists.
 *
 * <p>Settings that belong only to this feature go in their own YAML record through {@link
 * es.redactado.config.ConfigFiles#load}. {@code config.yml} stays the process settings.
 *
 * <p>{@link #configure()} is final because every feature has to open the same empty Guice sets
 * first. A bot that registers nothing must still be able to inject those sets.
 */
public abstract class BotFeature extends AbstractModule {

    /**
     * Opens an empty set for each kind of contribution, then lets the subclass fill the ones it
     * uses. Guice refuses to inject a set that no module created, so the empty sets are part of
     * the contract, not a leftover.
     */
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

    /**
     * Name what this feature adds. Called once, while Guice is being built, so the methods below
     * are registrations rather than work that should run at startup.
     */
    protected abstract void contribute();

    /** A slash command. {@code CommandRegister} indexes it by the name Discord sends back. */
    protected final void slashCommand(Class<? extends BaseSlashCommand> type) {
        Multibinder.newSetBinder(binder(), BaseSlashCommand.class).addBinding().to(type);
    }

    /** A command that acts on a message. The reply is ephemeral. */
    protected final void messageCommand(Class<? extends BaseMessageContextCommand> type) {
        Multibinder.newSetBinder(binder(), BaseMessageContextCommand.class).addBinding().to(type);
    }

    /** A command that acts on a user. The reply is ephemeral. */
    protected final void userCommand(Class<? extends BaseUserContextCommand> type) {
        Multibinder.newSetBinder(binder(), BaseUserContextCommand.class).addBinding().to(type);
    }

    /** A Hibernate class. {@code DatabaseManager} maps every entity any feature registered. */
    protected final void entity(Class<?> type) {
        Multibinder.newSetBinder(binder(), ManagedEntity.class)
                .addBinding()
                .toInstance(new ManagedEntity(type));
    }

    /**
     * A gateway listener that is not a command. Command classes that also listen, for
     * autocomplete, are registered by {@code CommandRegister} and do not need this.
     */
    protected final void listener(Class<? extends ListenerAdapter> type) {
        Multibinder.newSetBinder(binder(), ListenerBinding.class)
                .addBinding()
                .toInstance(new ListenerBinding(type));
    }

    /**
     * A service started before the gateway connects. It can use the database and the thread
     * pools. It cannot use {@code ShardManager}, which is created from the injector that is
     * still starting these services.
     */
    protected final void infrastructure(Class<? extends IService> type) {
        Multibinder.newSetBinder(binder(), InfrastructureService.class)
                .addBinding()
                .toInstance(new InfrastructureService(type));
    }

    /**
     * A service started after the first ready event, once guilds and the Discord API are
     * available. Declare {@code dependsOn()} if it needs an infrastructure service to have
     * started first; {@code ServiceManager} honours that, not the order of these calls.
     */
    protected final void business(Class<? extends IService> type) {
        Multibinder.newSetBinder(binder(), BusinessService.class)
                .addBinding()
                .toInstance(new BusinessService(type));
    }

    /**
     * A SQL script for one dialect ({@code sqlite}, {@code mariadb} or {@code h2}).
     *
     * <p>The version must be unique for that dialect across every feature. The resource is a
     * classpath path such as {@code /db/migration/sqlite/V2__notes.sql}.
     */
    protected final void migration(String dialect, int version, String resource) {
        Multibinder.newSetBinder(binder(), MigrationScript.class)
                .addBinding()
                .toInstance(new MigrationScript(dialect, version, resource));
    }
}
