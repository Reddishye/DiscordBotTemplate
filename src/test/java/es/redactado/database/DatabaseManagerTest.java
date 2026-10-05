package es.redactado.database;

import static org.assertj.core.api.Assertions.assertThat;

import es.redactado.config.BotConfig;
import es.redactado.config.ConfigFile;
import es.redactado.database.model.ChannelPanel;
import es.redactado.database.model.PresetPreference;
import es.redactado.service.StoredPresetPreferences;
import es.redactado.service.TaskManager;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseManagerTest {

    @TempDir Path dir;

    @Test
    void sqliteStoresAGuildPreset() {
        roundTrip("SQLITE", dir.resolve("sqlite"));
    }

    @Test
    void h2StoresAGuildPreset() {
        roundTrip("H2", dir.resolve("h2"));
    }

    private static void roundTrip(String type, Path path) {
        ConfigFile defaults = new ConfigFile();
        BotConfig config =
                BotConfig.from(
                        new ConfigFile(
                                defaults.bot(),
                                new ConfigFile.DatabaseFile(
                                        type, "localhost", 3306, "bot", "", "", path.toString()),
                                defaults.pool(),
                                defaults.hibernate(),
                                defaults.menu(),
                                defaults.commands(),
                                defaults.sentry()));
        TaskManager tasks = new TaskManager();
        tasks.init();
        DatabaseManager database =
                new DatabaseManager(
                        config,
                        Set.of(
                                new ManagedEntity(PresetPreference.class),
                                new ManagedEntity(ChannelPanel.class)),
                        tasks,
                        Set.of());
        try {
            database.init();
            StoredPresetPreferences preferences = new StoredPresetPreferences(database);
            preferences.setGuild(42L, "midnight").join();

            assertThat(preferences.guildPreset(42L).join()).contains("midnight");
            assertThat(preferences.guildPreset(7L).join()).isEmpty();

            preferences.setGuild(42L, "default").join();
            assertThat(preferences.guildPreset(42L).join()).contains("default");
            preferences.clearGuild(42L).join();
            assertThat(preferences.guildPreset(42L).join()).isEmpty();
        } finally {
            database.shutdown();
            tasks.shutdown();
        }
    }
}
