package es.redactado.config;

import de.exlll.configlib.YamlConfigurations;
import java.nio.file.Path;

/**
 * Loads a YAML file from the same directory as {@code config.yml}.
 *
 * <p>Use this when a class in another jar needs its own file. Settings for this bot go in {@link
 * ConfigFile} instead.
 */
public final class ConfigFiles {

    private final Path directory;

    public ConfigFiles(Path directory) {
        this.directory = directory;
    }

    public static ConfigFiles beside(Path configFile) {
        Path parent = configFile.toAbsolutePath().getParent();
        return new ConfigFiles(parent == null ? Path.of(".") : parent);
    }

    /**
     * @param fileName the file name, such as {@code welcome.yml}
     * @param type a ConfigLib record or {@code @Configuration} class
     * @return the loaded value, creating the file when it is missing
     */
    public <T> T load(String fileName, Class<T> type) {
        return YamlConfigurations.update(directory.resolve(fileName), type);
    }

    public Path directory() {
        return directory;
    }
}
