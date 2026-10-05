package es.redactado.service;

import es.redactado.database.DatabaseManager;
import es.redactado.database.model.ChannelPanel;
import es.redactado.menu.api.Validator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.RestAction;

/**
 * Remembers the message a shared menu occupies in a channel, and edits that message on the
 * next publish.
 *
 * <p>The caller renders the container. This class only sends it and stores the message id, one
 * row per guild, channel and menu.
 */
public final class ChannelPanels {

    private final DatabaseManager database;

    public ChannelPanels(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Optional<Long>> messageId(
            long guildId, long channelId, String menuId) {
        return database.readAsync(
                session ->
                        find(session, guildId, channelId, menuId).map(ChannelPanel::getMessageId));
    }

    /**
     * Edits the stored message, or sends a new one when there is no row or Discord no longer
     * has that message.
     *
     * @return a future for the message id now on screen
     */
    public CompletableFuture<Long> publish(
            MessageChannel channel, long guildId, String menuId, Container container) {
        Validator.verify(container);
        long channelId = channel.getIdLong();
        return messageId(guildId, channelId, menuId)
                .thenCompose(
                        existing ->
                                existing.map(id -> editOrSend(channel, id, container))
                                        .orElseGet(() -> send(channel, container)))
                .thenCompose(message -> remember(guildId, channelId, menuId, message.getIdLong()));
    }

    private CompletableFuture<Message> editOrSend(
            MessageChannel channel, long messageId, Container container) {
        return submit(channel.editMessageComponentsById(messageId, container).useComponentsV2())
                .exceptionallyCompose(error -> send(channel, container));
    }

    private static CompletableFuture<Message> send(MessageChannel channel, Container container) {
        return submit(channel.sendMessageComponents(container).useComponentsV2());
    }

    private static <T> CompletableFuture<T> submit(RestAction<T> action) {
        return action.submit();
    }

    private CompletableFuture<Long> remember(
            long guildId, long channelId, String menuId, long messageId) {
        return database.inTransactionAsync(
                session -> {
                    Optional<ChannelPanel> row = find(session, guildId, channelId, menuId);
                    if (row.isEmpty()) {
                        session.persist(new ChannelPanel(guildId, channelId, menuId, messageId));
                    } else {
                        row.get().setMessageId(messageId);
                    }
                    return messageId;
                });
    }

    private static Optional<ChannelPanel> find(
            org.hibernate.Session session, long guildId, long channelId, String menuId) {
        return session.createQuery(
                        "from ChannelPanel p where p.guildId = :guild and p.channelId = :channel"
                                + " and p.menuId = :menu",
                        ChannelPanel.class)
                .setParameter("guild", guildId)
                .setParameter("channel", channelId)
                .setParameter("menu", menuId)
                .uniqueResultOptional();
    }
}
