package es.redactado.config;

/**
 * Commands are contributed by Guice modules such as {@link TemplateBindings}, not by a static
 * list. Add a binding of {@link es.redactado.command.type.BaseSlashCommand} from the module
 * that owns the feature.
 */
public final class Commands {
    private Commands() {}
}
