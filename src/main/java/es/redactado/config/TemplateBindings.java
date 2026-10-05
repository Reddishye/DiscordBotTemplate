package es.redactado.config;

import es.redactado.command.PingCommand;
import es.redactado.database.model.ChannelPanel;
import es.redactado.database.model.PresetPreference;
import es.redactado.feature.BotFeature;

/**
 * What this template itself contributes. A bot adds another {@link BotFeature}; it does not edit
 * this class. Listeners and services that the template always starts stay in {@link Listeners}
 * and {@link Services}. A feature appends to those through {@link BotFeature}.
 */
public final class TemplateBindings extends BotFeature {

    @Override
    protected void contribute() {
        slashCommand(PingCommand.class);
        entity(PresetPreference.class);
        entity(ChannelPanel.class);
    }
}
