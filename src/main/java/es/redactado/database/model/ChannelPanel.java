package es.redactado.database.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * The message a shared menu currently occupies in a channel.
 *
 * <p>One row per guild, channel and menu. Publishing again edits that message instead of
 * posting a second copy.
 */
@Entity
@Table(
        name = "channel_panel",
        uniqueConstraints = @UniqueConstraint(columnNames = {"guild_id", "channel_id", "menu_id"}))
public class ChannelPanel extends BaseDomain {

    @Column(name = "guild_id", nullable = false)
    private long guildId;

    @Column(name = "channel_id", nullable = false)
    private long channelId;

    @Column(name = "menu_id", nullable = false, length = 64)
    private String menuId;

    @Column(name = "message_id", nullable = false)
    private long messageId;

    protected ChannelPanel() {}

    public ChannelPanel(long guildId, long channelId, String menuId, long messageId) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.menuId = menuId;
        this.messageId = messageId;
    }

    public long getGuildId() {
        return guildId;
    }

    public long getChannelId() {
        return channelId;
    }

    public String getMenuId() {
        return menuId;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }
}
