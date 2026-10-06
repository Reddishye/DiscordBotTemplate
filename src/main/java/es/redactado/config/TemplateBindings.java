package es.redactado.config;

import es.redactado.command.PingCommand;
import es.redactado.command.handler.CommandListener;
import es.redactado.command.handler.MenuListener;
import es.redactado.database.DatabaseManager;
import es.redactado.database.model.ChannelPanel;
import es.redactado.database.model.PresetPreference;
import es.redactado.feature.BotFeature;
import es.redactado.service.MenuService;
import es.redactado.service.TaskManager;

/**
 * What this template runs.
 *
 * <p>Add a line in {@link #contribute()} for a command, listener, service or entity. A separate
 * jar does the same thing in its own {@link BotFeature}.
 */
public final class TemplateBindings extends BotFeature {

    @Override
    protected void contribute() {
        service(TaskManager.class);
        service(DatabaseManager.class);
        service(MenuService.class);
        listener(CommandListener.class);
        listener(MenuListener.class);
        slashCommand(PingCommand.class);
        entity(PresetPreference.class);
        entity(ChannelPanel.class);
    }
}
