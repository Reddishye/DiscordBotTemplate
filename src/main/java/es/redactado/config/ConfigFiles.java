package es.redactado.config;

import de.exlll.configlib.YamlConfigurations;
import java.nio.file.Path;

/**
 * Where a feature keeps settings that do not belong in {@code config.yml}.
 *
 * <p>{@link #load} writes a record to its own file beside the core config, using ConfigLib's
 * defaults and comments, and reads it back on the next start. A feature in another jar can do
 * this without adding fields to {@link ConfigFile}. Environment overrides for that file are the
 * feature's own concern; the core overlay only covers {@code config.yml}.
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
