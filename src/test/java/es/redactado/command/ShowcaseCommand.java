package es.redactado.command;

import com.google.inject.Inject;
import es.redactado.command.type.BaseSlashCommand;
import es.redactado.menu.examples.ShowcaseMenu;
import es.redactado.service.MenuService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * An example command that opens a menu, kept here so it is compiled.
 *
 * <p>The framework ships no commands: opening a menu is the developer's decision, and the
 * framework's job is to make the decision easy rather than to make it. This is what that
 * looks like.
 *
 * <p>Two things it gets right that a menu opening has to get right:
 *
 * <ul>
 *   <li><strong>Register once.</strong> {@link MenuService#register} is idempotent per id but
 *       refuses a duplicate, so a registration inside a command runs the first time and
 *       throws on the second. Registering in the service's own startup, or behind a flag, is
 *       what a busy bot should do.
 *   <li><strong>Say whether the menu is private.</strong> The second argument to
 *       {@link MenuService#open} is ephemeral. A personal menu belongs in an ephemeral reply,
 *       because its buttons are bound to whoever ran the command.
 * </ul>
 *
 * <p>To use it, add the class to {@code Commands.SLASH_COMMANDS}:
 *
 * <pre>{@code
 * List.of(PingCommand.class, ShowcaseCommand.class)
 * }</pre>
 *
 * <p>and follow {@code docs/manual-test.md}, which walks the showcase by hand.
 */
public class ShowcaseCommand implements BaseSlashCommand {

    private final MenuService menuService;

    /**
     * @param menuService the service that owns the router
     */
    @Inject
    public ShowcaseCommand(MenuService menuService) {
        this.menuService = menuService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("showcase", "Open the menu showcase")
                .setNSFW(false)
                .setContexts(InteractionContextType.GUILD, InteractionContextType.BOT_DM);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        menuService.register(new ShowcaseMenu(menuService.presets()));
        menuService.open(event, "showcase", true);
    }
}
