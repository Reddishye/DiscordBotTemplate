package es.redactado.command.dispatch;

import es.redactado.command.type.BaseMessageContextCommand;
import es.redactado.command.type.BaseSlashCommand;
import es.redactado.command.type.BaseUserContextCommand;
import io.sentry.Sentry;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The same steps for every command: permission, cooldown, acknowledge, run, then one reply
 * if it failed.
 *
 * <p>Acknowledgement happens on the calling thread, before the handler, because Discord allows
 * three seconds and the handler may block. The handler runs on the I/O executor.
 */
public final class CommandDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(CommandDispatcher.class);
    private static final String DENIED = "You do not have permission to use that command.";
    private static final String FAILED = "Something went wrong while running that command.";

    private final Executor executor;
    private final Duration defaultCooldown;
    private final Cooldowns cooldowns;

    public CommandDispatcher(Executor executor, Duration defaultCooldown) {
        this(executor, defaultCooldown, new Cooldowns());
    }

    CommandDispatcher(Executor executor, Duration defaultCooldown, Cooldowns cooldowns) {
        this.executor = executor;
        this.defaultCooldown = defaultCooldown;
        this.cooldowns = cooldowns;
    }

    public void slash(SlashCommandInteractionEvent event, BaseSlashCommand command) {
        if (!allowed(event.getMember(), command.permissions())) {
            event.reply(DENIED).setEphemeral(true).queue();
            return;
        }
        Optional<Duration> wait =
                reserve(
                        command.getCommandData().getName(),
                        event.getUser().getIdLong(),
                        command.cooldown());
        if (wait.isPresent()) {
            event.reply(waitMessage(wait.get())).setEphemeral(true).queue();
            return;
        }
        event.deferReply(command.ephemeral()).queue();
        run(
                command.getCommandData().getName(),
                "slash",
                () -> command.handle(event),
                message -> reply(event, message));
    }

    public void message(MessageContextInteractionEvent event, BaseMessageContextCommand command) {
        event.deferReply(true).queue();
        run(
                command.getCommandData().getName(),
                "message context",
                () -> command.handle(event),
                message -> reply(event, message));
    }

    public void user(UserContextInteractionEvent event, BaseUserContextCommand command) {
        event.deferReply(true).queue();
        run(
                command.getCommandData().getName(),
                "user context",
                () -> command.handle(event),
                message -> reply(event, message));
    }

    private Optional<Duration> reserve(String name, long userId, Duration commandCooldown) {
        Duration cooldown = commandCooldown == null ? defaultCooldown : commandCooldown;
        return cooldowns.tryAcquire(name, userId, cooldown, System.currentTimeMillis());
    }

    private void run(String name, String kind, Runnable body, Consumer<String> reply) {
        CompletableFuture.runAsync(body, executor)
                .whenComplete(
                        (ignored, error) -> {
                            if (error == null) {
                                return;
                            }
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            if (cause instanceof CommandFailure failure) {
                                reply.accept(failure.getMessage());
                                return;
                            }
                            LOG.error("{} command '{}' failed", kind, name, cause);
                            Sentry.captureException(cause);
                            try {
                                reply.accept(FAILED);
                            } catch (RuntimeException replyError) {
                                LOG.warn("Failed to send error reply for '{}'", name, replyError);
                            }
                        });
    }

    private static boolean allowed(Member member, Set<Permission> required) {
        if (required == null || required.isEmpty() || member == null) {
            return true;
        }
        return member.hasPermission(required);
    }

    private static String waitMessage(Duration wait) {
        long seconds = Math.max(1, wait.toSeconds());
        return "Wait " + seconds + " seconds before using that command again.";
    }

    private static void reply(SlashCommandInteractionEvent event, String message) {
        if (!event.isAcknowledged()) {
            event.reply(message).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(message).setEphemeral(true).queue();
        }
    }

    private static void reply(UserContextInteractionEvent event, String message) {
        if (!event.isAcknowledged()) {
            event.reply(message).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(message).setEphemeral(true).queue();
        }
    }

    private static void reply(MessageContextInteractionEvent event, String message) {
        if (!event.isAcknowledged()) {
            event.reply(message).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(message).setEphemeral(true).queue();
        }
    }
}
