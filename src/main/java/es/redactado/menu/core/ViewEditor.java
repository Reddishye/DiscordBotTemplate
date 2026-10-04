package es.redactado.menu.core;

import es.redactado.menu.api.Validator;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single place a rendered container is sent to Discord.
 *
 * <p>Centralising it means validation cannot be skipped by a code path that
 * forgets, and it keeps the Discord-specific details of editing a message out of
 * the menus themselves.
 *
 * <p>Uses {@code submit} rather than {@code queue} so the caller gets a future and
 * a failed REST call is observable instead of silently dropped.
 */
public final class ViewEditor {

    private static final Logger LOG = LoggerFactory.getLogger(ViewEditor.class);

    private ViewEditor() {}

    /**
     * Validates a container and, if it passes, writes it to the original message.
     *
     * <p>A container that breaks a hard limit is never sent: the future completes
     * exceptionally and Discord is not asked to render something it would reject.
     *
     * @param hook the interaction hook of an already-acknowledged interaction
     * @param container the container to show
     * @return a future completing when the edit is accepted, or completing
     *     exceptionally if validation or the REST call fails
     */
    public static CompletableFuture<Void> edit(InteractionHook hook, Container container) {
        try {
            Validator.verify(container);
        } catch (RuntimeException e) {
            LOG.warn("Refusing to send a container that breaks a Discord limit", e);
            return CompletableFuture.failedFuture(e);
        }
        return hook.editOriginalComponents(container)
                .useComponentsV2()
                .submit()
                .thenApply(message -> null);
    }
}
