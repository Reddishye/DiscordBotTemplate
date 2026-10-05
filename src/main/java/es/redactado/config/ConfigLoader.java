package es.redactado.config;

import de.exlll.configlib.YamlConfigurations;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads {@code config.yml}, then applies the environment.
 *
 * <p>{@code CONFIG_FILE} chooses the path because the file cannot name itself. {@link
 * YamlConfigurations#update} creates the file from the record defaults when it is missing and
 * adds keys when the record grows. The environment is applied after that, in memory only.
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
