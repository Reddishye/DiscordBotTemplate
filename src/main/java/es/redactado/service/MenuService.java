package es.redactado.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import es.redactado.config.BotConfig;
import es.redactado.database.DatabaseManager;
import es.redactado.menu.api.Menu;
import es.redactado.menu.core.MenuExecutor;
import es.redactado.menu.core.MenuRouter;
import es.redactado.menu.core.Messages;
import es.redactado.menu.core.PresetResolver;
import es.redactado.menu.core.SessionConfig;
import es.redactado.menu.core.SessionStore;
import es.redactado.menu.preset.InMemoryPresetPreferences;
import es.redactado.menu.preset.PresetPreferences;
import es.redactado.menu.preset.PresetRegistry;
import es.redactado.menu.preset.PresetStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the menu framework for this bot: the router, the sessions, the presets and the
 * executor they run on.
 *
 * <p><strong>This is the only place the two halves meet.</strong> The menu package takes an
 * {@link java.util.concurrent.Executor} and knows nothing about this template; this class
 * imports {@link TaskManager} and hands it one. That boundary is what lets the framework be
 * tested without a bot and this service be tested without JDA.
 *
 * <p><strong>The executor is borrowed, never owned.</strong> {@link MenuExecutor#shared} was
 * given the task manager's I/O pool, so shutting this service down leaves that pool alone
 * and the service manager can stop the task manager afterwards in its own order.
 *
 * <p><strong>Opening a menu is the developer's decision.</strong> This class provides the
 * primitive and ships no commands. Register a menu with {@link #register(Menu)} and open it
 * from whatever interaction makes sense: a slash command, another system's button, a modal.
 *
 * <p>Like {@link TaskManager}, every method except {@code init} and {@code shutdown} throws
 * {@link IllegalStateException} when this service is not running, because a caller holding a
 * stale reference should be told rather than handed a half-built router.
 */
@Singleton
public class MenuService implements IService {

    private static final Logger LOG = LoggerFactory.getLogger(MenuService.class);

    private final TaskManager taskManager;
    private final MenuSettings settings;
    private final DatabaseManager database;

    private volatile MenuRouter router;
    private volatile PresetRegistry registry;
    private volatile PresetPreferences preferences;
    private volatile StoredPresetPreferences storedPreferences;
    private volatile ChannelPanels panels;
    private volatile PresetStore presetStore;
    private volatile SessionStore sessions;
    private volatile ScheduledFuture<?> cleanUp;

    /**
     * @param taskManager the service whose pools the menus run on; must be the same
     *     instance the service registry started, which is why both are singletons
     * @param dotenv where this template's settings come from
     */
    @Inject
    public MenuService(TaskManager taskManager, BotConfig config, DatabaseManager database) {
        this(taskManager, config.menu(), database);
    }

    /** Visible for tests, which drive the settings directly and keep preferences in memory. */
    MenuService(TaskManager taskManager, MenuSettings settings) {
        this(taskManager, settings, null);
    }

    private MenuService(TaskManager taskManager, MenuSettings settings, DatabaseManager database) {
        this.taskManager = taskManager;
        this.settings = settings;
        this.database = database;
    }

    @Override
    public List<Class<? extends IService>> dependsOn() {
        return database == null
                ? List.of(TaskManager.class)
                : List.of(TaskManager.class, DatabaseManager.class);
    }

    @Override
    public synchronized void init() {
        if (router != null) {
            return;
        }

        PresetRegistry startedRegistry = new PresetRegistry();
        PresetPreferences chosenPreferences;
        StoredPresetPreferences chosenStored = null;
        ChannelPanels chosenPanels = null;
        if (database == null) {
            chosenPreferences = new InMemoryPresetPreferences();
        } else {
            chosenStored = new StoredPresetPreferences(database);
            chosenPreferences = chosenStored;
            chosenPanels = new ChannelPanels(database);
        }
        SessionStore startedSessions =
                new SessionStore(
                        new SessionConfig(settings.sessionMaxSize(), settings.sessionIdleTtl()));
        MenuRouter startedRouter =
                MenuRouter.builder()
                        .executor(MenuExecutor.shared(taskManager.ioExecutor()))
                        .maxInFlight(settings.maxInFlight())
                        .sessions(startedSessions)
                        .messages(Messages.standard())
                        .presets(
                                new PresetResolver(
                                        startedRegistry,
                                        chosenPreferences,
                                        settings.userPresetsEnabled()))
                        .build();

        PresetStore startedStore = new PresetStore(startedRegistry, settings.presetsDirectory());
        startedStore.reload();
        startedStore.startWatching();

        this.registry = startedRegistry;
        this.preferences = chosenPreferences;
        this.storedPreferences = chosenStored;
        this.panels = chosenPanels;
        this.sessions = startedSessions;
        this.router = startedRouter;
        this.presetStore = startedStore;
        this.cleanUp =
                taskManager.scheduleAtFixedRate(
                        startedSessions::cleanUp,
                        settings.cleanUpInterval().toMillis(),
                        settings.cleanUpInterval().toMillis(),
                        TimeUnit.MILLISECONDS);

        LOG.info(
                "MenuService started (presets={}, sessions={} for {})",
                settings.presetsDirectory(),
                settings.sessionMaxSize(),
                settings.sessionIdleTtl());
    }

    @Override
    public synchronized void shutdown() {
        if (router == null) {
            return;
        }

        ScheduledFuture<?> scheduled = cleanUp;
        cleanUp = null;
        if (scheduled != null) {
            // Cancelled before anything is closed, so a drain cannot start against a
            // store that is on its way out. A task manager that has already stopped
            // rejects the cancellation, which is harmless.
            try {
                scheduled.cancel(false);
            } catch (RuntimeException alreadyStopped) {
                LOG.debug("Task manager already stopped; nothing to cancel");
            }
        }

        PresetStore store = presetStore;
        presetStore = null;
        if (store != null) {
            store.close();
        }

        MenuRouter current = router;
        SessionStore currentSessions = sessions;
        router = null;
        sessions = null;
        registry = null;
        preferences = null;
        storedPreferences = null;
        panels = null;

        // The router was given the task manager's executor to borrow, so closing it does
        // not touch that pool: close() only stops what it created.
        if (current != null) {
            current.close();
        }
        if (currentSessions != null) {
            currentSessions.close();
        }
        LOG.info("MenuService stopped");
    }

    /**
     * Registers a menu so interactions can route to it.
     *
     * @param menu the menu, whose id is the route
     * @throws IllegalStateException if this service is not running
     * @throws IllegalArgumentException if the id is already registered
     */
    public void register(Menu menu) {
        running().register(menu.id(), menu);
    }

    /**
     * The router, for a listener that has to dispatch interactions to it.
     *
     * @return the router
     * @throws IllegalStateException if this service is not running
     */
    public MenuRouter router() {
        return running();
    }

    /**
     * The presets this bot offers, which is also where custom files are loaded.
     *
     * @return the registry
     * @throws IllegalStateException if this service is not running
     */
    public PresetRegistry presets() {
        return require(registry, "presets");
    }

    /**
     * Where guild and user preset choices are kept.
     *
     * <p>When a database is available the choices are stored there. The test constructor keeps
     * them in memory.
     *
     * @return the preferences in use
     * @throws IllegalStateException if this service is not running
     */
    public PresetPreferences preferences() {
        return require(preferences, "preferences");
    }

    /**
     * Stores a guild's preset choice. Requires the service to be running with a database.
     */
    public CompletableFuture<Void> setGuildPreset(long guildId, String name) {
        StoredPresetPreferences stored = require(storedPreferences, "stored preferences");
        return stored.setGuild(guildId, name);
    }

    /** Stores a user's preset choice. Requires the service to be running with a database. */
    public CompletableFuture<Void> setUserPreset(long userId, String name) {
        return require(storedPreferences, "stored preferences").setUser(userId, name);
    }

    /** Shared channel messages this bot updates in place. */
    public ChannelPanels panels() {
        return require(panels, "channel panels");
    }

    /**
     * Opens a menu in reply to an interaction.
     *
     * @param event the interaction to answer
     * @param menuId a registered menu id
     * @param ephemeral whether only the opening user sees it
     * @return a future completing when the menu has been sent
     * @throws IllegalStateException if this service is not running
     * @throws es.redactado.menu.api.MenuNotFoundException if no menu is registered under
     *     that id
     */
    public CompletableFuture<Void> open(IReplyCallback event, String menuId, boolean ephemeral) {
        return running().open(event, menuId, ephemeral);
    }

    /** The settings this service was built with, for a startup log or a command. */
    MenuSettings settings() {
        return settings;
    }

    /**
     * The schedule that drains pending session eviction, or null when not running.
     *
     * <p>Visible for tests, which have to prove it is cancelled on shutdown without waiting
     * for a minute to pass. There is no other way to observe a schedule from outside.
     */
    ScheduledFuture<?> cleanUpSchedule() {
        return cleanUp;
    }

    private MenuRouter running() {
        MenuRouter current = router;
        if (current == null) {
            throw new IllegalStateException("MenuService is not running");
        }
        return current;
    }

    private <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("MenuService is not running: no " + what);
        }
        return value;
    }
}
