package es.redactado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import es.redactado.config.Services;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.core.MenuRouter;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * The menu service, driven the way the template starts it and without connecting JDA.
 *
 * <p>A real {@link TaskManager} rather than a mock, because the two facts worth proving here
 * are that the service borrows a pool it does not own and that a schedule it creates is
 * cancelled when it stops. Neither is visible through a mock.
 */
class MenuServiceTest {

    private TaskManager taskManager;
    private MenuService service;

    @BeforeEach
    void setUp() {
        taskManager = new TaskManager();
        // Started first, exactly as the service registry does it: the menu service borrows
        // this instance's pools and cannot start before it does.
        taskManager.init();
        service = new MenuService(taskManager, settings());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
        taskManager.shutdown();
    }

    private static MenuSettings settings() {
        return MenuSettings.from(Dotenv.configure().ignoreIfMissing().load())
                .withCleanUpInterval(java.time.Duration.ofMillis(50));
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("init is idempotent and shutdown is too")
        void idempotent() {
            service.init();
            MenuRouter first = service.router();
            service.init();
            assertThat(service.router()).as("a second init keeps the same router").isSameAs(first);

            service.shutdown();
            service.shutdown();
            assertThatThrownBy(service::router).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("it declares the task manager as its dependency")
        void declaresItsDependency() {
            assertThat(service.dependsOn()).containsExactly(TaskManager.class);
        }

        @Test
        @DisplayName("nothing but init and shutdown works before it starts")
        void refusesBeforeInit() {
            assertThatThrownBy(service::router).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(service::presets).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(service::preferences).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> service.register(new TestMenu()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("register then open then shutdown, in that order, works")
        void registerOpenShutdown() {
            service.init();
            service.register(new TestMenu());

            SlashCommandInteractionEvent event = command();
            service.open(event, "test", true).join();

            verify(event.getHook(), timeout(AWAIT_MS))
                    .editOriginalComponents(
                            org.mockito.ArgumentMatchers.any(
                                    net.dv8tion.jda.api.components.MessageTopLevelComponent[]
                                            .class));
            service.shutdown();
            assertThatThrownBy(service::router).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("the scheduled drain exists while running and is cancelled on shutdown")
        void drainIsCancelled() {
            service.init();
            ScheduledFuture<?> schedule = service.cleanUpSchedule();

            // Compared as a boolean rather than asserted as an object: AssertJ has a
            // dedicated assertion for futures and the two overloads do not resolve here.
            assertThat(schedule != null).as("a drain must actually be scheduled").isTrue();
            assertThat(schedule.isCancelled()).isFalse();

            service.shutdown();

            assertThat(schedule.isCancelled())
                    .as("a drain left running would touch a store that is on its way out")
                    .isTrue();
        }

        @Test
        @DisplayName("a task manager that already stopped does not break the shutdown")
        void survivesATaskManagerThatStoppedFirst() {
            service.init();
            taskManager.shutdown();

            assertThatCode(() -> service.shutdown())
                    .as("the pool is gone, but shutting down must still tidy up")
                    .doesNotThrowAnyException();
            assertThatThrownBy(service::router).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("the injector")
    class Injection {

        @Test
        @DisplayName("the menu service sees the started task manager, not a fresh copy")
        void menuServiceSeesTheStartedTaskManager() {
            Injector injector = injector();
            ServiceManager registry = injector.getInstance(ServiceManager.class);

            registry.startAll(Services.INFRASTRUCTURE_SERVICES);
            MenuService menus = injector.getInstance(MenuService.class);

            // The proof, not a tautology: init() borrows taskManager.ioExecutor(), so a
            // service holding an unstarted copy could not have got this far.
            assertThatCode(menus::router).doesNotThrowAnyException();
            assertThatCode(menus::presets).doesNotThrowAnyException();
            assertThat(menus.cleanUpSchedule() != null)
                    .as("and it scheduled its drain on that same instance")
                    .isTrue();

            registry.stopAll();
        }

        @Test
        @DisplayName("a second resolution of the menu service returns the same instance")
        void menuServiceIsScoped() {
            Injector injector = injector();

            assertThat(injector.getInstance(MenuService.class))
                    .isSameAs(injector.getInstance(MenuService.class));
            assertThat(injector.getInstance(TaskManager.class))
                    .isSameAs(injector.getInstance(TaskManager.class));
        }

        /** The template's own injector, minus the parts that need a gateway. */
        private Injector injector() {
            return Guice.createInjector(new TestModule());
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("defaults apply when nothing is set")
        void defaults() {
            MenuSettings parsed = MenuSettings.from(Dotenv.configure().ignoreIfMissing().load());

            assertThat(parsed.presetsDirectory().toString()).isEqualTo("presets");
            assertThat(parsed.sessionMaxSize()).isEqualTo(50_000L);
            assertThat(parsed.sessionIdleTtl()).isEqualTo(java.time.Duration.ofMinutes(30));
            assertThat(parsed.userPresetsEnabled()).isFalse();
            assertThat(parsed.defaultPreset()).isEqualTo("default");
        }

        @Test
        @DisplayName("every setting can be overridden")
        void overrides() {
            Dotenv dotenv =
                    dotenv(
                            "MENU_PRESETS_DIR=/etc/looks",
                            "MENU_SESSION_MAX_SIZE=10",
                            "MENU_SESSION_IDLE_TTL=90s",
                            "MENU_USER_PRESETS_ENABLED=yes",
                            "MENU_DEFAULT_PRESET=midnight");

            MenuSettings parsed = MenuSettings.from(dotenv);

            assertThat(parsed.presetsDirectory().toString()).isEqualTo("/etc/looks");
            assertThat(parsed.sessionMaxSize()).isEqualTo(10L);
            assertThat(parsed.sessionIdleTtl()).isEqualTo(java.time.Duration.ofSeconds(90));
            assertThat(parsed.userPresetsEnabled()).isTrue();
            assertThat(parsed.defaultPreset()).isEqualTo("midnight");
        }

        @Test
        @DisplayName("a duration accepts minutes, seconds, hours and milliseconds")
        void durationForms() {
            assertThat(MenuSettings.from(dotenv("MENU_SESSION_IDLE_TTL=2h")).sessionIdleTtl())
                    .isEqualTo(java.time.Duration.ofHours(2));
            assertThat(MenuSettings.from(dotenv("MENU_SESSION_IDLE_TTL=45m")).sessionIdleTtl())
                    .isEqualTo(java.time.Duration.ofMinutes(45));
            assertThat(MenuSettings.from(dotenv("MENU_SESSION_IDLE_TTL=500ms")).sessionIdleTtl())
                    .isEqualTo(java.time.Duration.ofMillis(500));
            assertThat(MenuSettings.from(dotenv("MENU_SESSION_IDLE_TTL=5")).sessionIdleTtl())
                    .as("a bare number means minutes")
                    .isEqualTo(java.time.Duration.ofMinutes(5));
        }

        @Test
        @DisplayName("a bad value fails fast, naming the setting")
        void badValuesFailFast() {
            assertThatThrownBy(() -> MenuSettings.from(dotenv("MENU_SESSION_MAX_SIZE=many")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MENU_SESSION_MAX_SIZE")
                    .hasMessageContaining("many");
            assertThatThrownBy(() -> MenuSettings.from(dotenv("MENU_SESSION_MAX_SIZE=0")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MENU_SESSION_MAX_SIZE");
            assertThatThrownBy(() -> MenuSettings.from(dotenv("MENU_SESSION_IDLE_TTL=soon")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MENU_SESSION_IDLE_TTL")
                    .hasMessageContaining("30m");
            assertThatThrownBy(() -> MenuSettings.from(dotenv("MENU_USER_PRESETS_ENABLED=perhaps")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MENU_USER_PRESETS_ENABLED");
            assertThatThrownBy(() -> MenuSettings.from(dotenv("MENU_DEFAULT_PRESET=  ")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("MENU_DEFAULT_PRESET");
        }

        @Test
        @DisplayName("a missing presets directory is fine, the built-in presets still work")
        void missingPresetDirectoryIsFine() {
            MenuService withMissingDirectory =
                    new MenuService(
                            taskManager,
                            MenuSettings.from(dotenv("MENU_PRESETS_DIR=/nowhere/at/all"))
                                    .withCleanUpInterval(java.time.Duration.ofMillis(50)));
            try {
                withMissingDirectory.init();

                assertThat(withMissingDirectory.presets().all())
                        .as("the five built-ins are there regardless of the directory")
                        .isNotEmpty();
            } finally {
                withMissingDirectory.shutdown();
            }
        }
    }

    // ------------------------------------------------------------- fixtures

    /**
     * A dotenv over fixed {@code KEY=VALUE} strings, so a test sets exactly what it is
     * about and nothing else.
     */
    private static Dotenv dotenv(String... assignments) {
        Dotenv dotenv = mock(Dotenv.class);
        java.util.Map<String, String> values = new java.util.HashMap<>();
        for (String assignment : assignments) {
            int equals = assignment.indexOf('=');
            values.put(assignment.substring(0, equals), assignment.substring(equals + 1));
        }
        Mockito.when(dotenv.get(Mockito.anyString()))
                .thenAnswer(call -> values.get(call.getArgument(0, String.class)));
        Mockito.when(dotenv.get(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        call ->
                                values.getOrDefault(
                                        call.getArgument(0, String.class),
                                        call.getArgument(1, String.class)));
        return dotenv;
    }

    /** The injector the template builds, without the gateway it would otherwise need. */
    private static final class TestModule extends com.google.inject.AbstractModule {
        @Override
        protected void configure() {
            bind(Dotenv.class).toInstance(dotenv());
        }
    }

    private static final class TestMenu extends es.redactado.menu.core.AbstractMenu {
        TestMenu() {
            super("test");
        }

        @Override
        protected void declare(es.redactado.menu.api.ActionTable.Builder table) {
            // Nothing to declare: this menu only has to exist and render.
        }

        @Override
        public CompletableFuture<Container> render(MenuContext ctx) {
            return CompletableFuture.completedFuture(Container.of(TextDisplay.of("test")));
        }
    }

    /**
     * A slash command with a hook that accepts an edit.
     *
     * <p>Built here rather than borrowed from the menu package's own mocks, which are
     * package-private on purpose: a test outside that package may not see them, and copying
     * one is cheaper than making it public.
     */
    private static SlashCommandInteractionEvent command() {
        net.dv8tion.jda.api.interactions.InteractionHook hook =
                mock(net.dv8tion.jda.api.interactions.InteractionHook.class);
        var edit = mock(net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction.class);
        when(edit.useComponentsV2()).thenReturn(edit);
        when(edit.submit())
                .thenReturn(
                        CompletableFuture.completedFuture(
                                mock(net.dv8tion.jda.api.entities.Message.class)));
        when(hook.editOriginalComponents(
                        Mockito.any(
                                net.dv8tion.jda.api.components.MessageTopLevelComponent[].class)))
                .thenReturn(edit);
        ReplyCallbackAction deferReply = mock(ReplyCallbackAction.class);
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        net.dv8tion.jda.api.entities.User user = mock(net.dv8tion.jda.api.entities.User.class);
        when(user.getIdLong()).thenReturn(42L);
        when(event.getUser()).thenReturn(user);
        when(event.getGuild()).thenReturn(null);
        when(event.getUserLocale()).thenReturn(DiscordLocale.ENGLISH_UK);
        when(event.deferReply(Mockito.any(Boolean.class))).thenReturn(deferReply);
        when(event.isAcknowledged()).thenReturn(true);
        when(event.getHook()).thenReturn(hook);
        return event;
    }

    private static final int AWAIT_MS = 5_000;
}
