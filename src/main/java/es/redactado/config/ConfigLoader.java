package es.redactado.config;

import de.exlll.configlib.YamlConfigurations;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads {@code config.yml}, creates it from {@link ConfigFile} when it is missing, then applies
 * environment variables. {@code CONFIG_FILE} selects a different path.
 */
public final class ConfigLoader {

    private ConfigLoader() {}

    public static BotConfig load() {
        String configured = System.getenv("CONFIG_FILE");
        Path path = Path.of(configured == null || configured.isBlank() ? "config.yml" : configured);
        return load(path, System.getenv());
    }

    public static BotConfig load(Path path, Map<String, String> env) {
        ConfigFile file = YamlConfigurations.update(path, ConfigFile.class);
        return BotConfig.from(EnvOverlay.apply(file, env));
    }
}
