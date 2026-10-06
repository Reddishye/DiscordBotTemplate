package es.redactado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails the build if the menu framework reaches for the template's services.
 *
 * <p>The framework takes {@code java.util.concurrent.Executor} and nothing else, and the
 * integration classes that supply it are named here. That single rule is what lets the whole
 * menu package be tested without a bot, and it is the kind of boundary that decays quietly:
 * one convenient import in a view component and the next test needs a Guice injector.
 *
 * <p>Three rules, checked separately because they fail for different reasons:
 *
 * <ol>
 *   <li>nothing under {@code menu} imports {@code es.redactado} at all;
 *   <li>outside {@code menu}, the only importers of {@code es.redactado.service} are the
 *       integration classes and the two template files that already did so;
 *   <li>both lists name files that exist, so an exemption cannot rot into a hole.
 * </ol>
 */
class MenuDependencyTest {

    private static final Path MAIN_ROOT = Path.of("src/main/java/es/redactado");
    private static final Path MENU_ROOT = MAIN_ROOT.resolve("menu");

    /**
     * Integration code allowed to import the template's services, relative to the source root.
     *
     * <p>{@code MenuService} and {@code MenuSettings} are inside {@code es.redactado.service}
     * and so cannot import it. The classes listed here live outside that package and still
     * need a service: the listeners, the config record that builds menu settings, and the
     * database, which runs work on the task manager.
     */
    private static final List<String> INTEGRATION =
            List.of(
                    "command/handler/MenuListener.java",
                    "command/handler/CommandListener.java",
                    "config/BotConfig.java",
                    "database/DatabaseManager.java",
                    "feature/BotFeature.java",
                    "feature/FeatureCatalog.java",
                    "feature/InfrastructureService.java",
                    "feature/BusinessService.java",
                    "config/TemplateBindings.java");

    /**
     * Template files that already imported the services before the menu system existed.
     *
     * <p>{@code Main} starts the services. It is not menu code.
     */
    private static final List<String> PRE_EXISTING = List.of("Main.java");

    @Test
    @DisplayName("outside the menu package, only integration code imports es.redactado.service")
    void onlyIntegrationCodeImportsTheTemplateServices() throws IOException {
        List<String> importers = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.walk(MAIN_ROOT)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                if (file.startsWith(MENU_ROOT)) {
                    continue;
                }
                if (importsTemplateServices(file)) {
                    importers.add(relative(file));
                }
            }
        }

        assertThat(scanned).as("java files scanned").isGreaterThan(0);
        assertThat(importers)
                .as("a new importer outside the menu package is integration code and belongs here")
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.Stream.concat(INTEGRATION.stream(), PRE_EXISTING.stream())
                                .toList());
    }

    @Test
    @DisplayName("no file under the menu package imports es.redactado.service")
    void theMenuPackageNeverImportsTheServices() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (importsTemplateServices(file)) {
                    offenders.add(file + ": " + relative(file));
                }
            }
        }

        assertThat(offenders)
                .as("the framework takes an Executor and knows nothing about this template")
                .isEmpty();
    }

    @Test
    @DisplayName("the menu package does not import the template at all")
    void theMenuPackageIsFreeOfTheTemplate() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> files = Files.walk(MENU_ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.startsWith("import es.redactado.")
                            && !line.startsWith("import es.redactado.menu.")) {
                        offenders.add(file + ": " + line.strip());
                    }
                }
            }
        }

        assertThat(offenders)
                .as("the framework must not depend on anything outside itself")
                .isEmpty();
    }

    @Test
    @DisplayName("every named file exists, so a stale entry cannot hide a new importer")
    void theNamedFilesExist() {
        for (String path : INTEGRATION) {
            assertThat(MAIN_ROOT.resolve(path))
                    .as(
                            "an exemption naming a file that does not exist is a hole with a name"
                                    + " on it")
                    .isRegularFile();
            assertThat(MAIN_ROOT.resolve(path))
                    .as(
                            "integration code must live outside es.redactado.service, or it needs"
                                    + " no exemption")
                    .isNotEqualTo(
                            MAIN_ROOT.resolve("service").resolve(Path.of(path).getFileName()));
        }
        for (String path : PRE_EXISTING) {
            assertThat(MAIN_ROOT.resolve(path)).as("pre-existing file moved").isRegularFile();
        }
    }

    private static boolean importsTemplateServices(Path file) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("import es.redactado.service.")
                    || line.startsWith("import static es.redactado.service.")) {
                return true;
            }
        }
        return false;
    }

    private static String relative(Path file) {
        return MAIN_ROOT.relativize(file).toString().replace('\\', '/');
    }
}
