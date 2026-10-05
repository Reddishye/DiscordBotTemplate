package es.redactado.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @TempDir Path dir;

    @Test
    void fileIsCreatedAndEnvironmentWins() {
        BotConfig config =
                ConfigLoader.load(
                        dir.resolve("config.yml"),
                        Map.of(
                                "BOT_TOKEN", "secret",
                                "BOT_MENU_SESSION_IDLE_TTL", "90s",
                                "BOT_MENU_SESSION_MAX_SIZE", "10",
                                "BOT_MENU_USER_PRESETS_ENABLED", "yes",
                                "BOT_MENU_DEFAULT_PRESET", "midnight",
                                "BOT_MENU_PRESETS_DIR", "/etc/looks"));

        assertThat(config.token()).isEqualTo("secret");
        assertThat(config.menu().sessionIdleTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(config.menu().sessionMaxSize()).isEqualTo(10L);
        assertThat(config.menu().userPresetsEnabled()).isTrue();
        assertThat(config.menu().defaultPreset()).isEqualTo("midnight");
        assertThat(config.menu().presetsDirectory().toString()).isEqualTo("/etc/looks");
        assertThat(config.pool().maxSize()).isEqualTo(1);
        assertThat(Files.exists(dir.resolve("config.yml"))).isTrue();
    }

    @Test
    void blankEnvironmentDoesNotOverride() {
        BotConfig config = ConfigLoader.load(dir.resolve("config.yml"), Map.of("BOT_TOKEN", "  "));

        assertThat(config.token()).isEqualTo("change-me");
    }

    @Test
    void legacyTokenStillWorks() {
        BotConfig config =
                ConfigLoader.load(dir.resolve("config.yml"), Map.of("DISCORD_TOKEN", "from-env"));

        assertThat(config.token()).isEqualTo("from-env");
    }

    @Test
    void botTokenBeatsLegacyToken() {
        BotConfig config =
                ConfigLoader.load(
                        dir.resolve("config.yml"),
                        Map.of("BOT_TOKEN", "new", "DISCORD_TOKEN", "old"));

        assertThat(config.token()).isEqualTo("new");
    }

    @Test
    void durationForms() {
        assertThat(loaded("BOT_MENU_SESSION_IDLE_TTL", "2h").menu().sessionIdleTtl())
                .isEqualTo(Duration.ofHours(2));
        assertThat(loaded("BOT_MENU_SESSION_IDLE_TTL", "45m").menu().sessionIdleTtl())
                .isEqualTo(Duration.ofMinutes(45));
        assertThat(loaded("BOT_MENU_SESSION_IDLE_TTL", "500ms").menu().sessionIdleTtl())
                .isEqualTo(Duration.ofMillis(500));
        assertThat(loaded("BOT_MENU_SESSION_IDLE_TTL", "5").menu().sessionIdleTtl())
                .isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void badValuesNameTheSetting() {
        assertThatThrownBy(() -> loaded("BOT_MENU_SESSION_MAX_SIZE", "many"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOT_MENU_SESSION_MAX_SIZE");
        assertThatThrownBy(() -> loaded("BOT_MENU_SESSION_IDLE_TTL", "soon"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menu.sessionIdleTtl");
        assertThatThrownBy(() -> loaded("BOT_MENU_USER_PRESETS_ENABLED", "perhaps"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOT_MENU_USER_PRESETS_ENABLED");
    }

    private BotConfig loaded(String key, String value) {
        return ConfigLoader.load(dir.resolve(key + ".yml"), Map.of(key, value));
    }
}
