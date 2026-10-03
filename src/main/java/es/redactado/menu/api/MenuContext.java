package es.redactado.menu.api;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/**
 * Request-scoped context for a single menu render or interaction.
 *
 * <p>Carries the parsed action data, the Discord entities involved, and a handle
 * to the {@link Session} for the message, which is where navigation history and
 * menu state live. State deliberately does not live here: a context is discarded
 * when its handler returns, so anything kept on it would be lost between clicks.
 */
public interface MenuContext {

    /**
     * Menu identifier, as encoded in the component id.
     *
     * @return the menu id, for example {@code profile}
     */
    String menuId();

    /**
     * Action name, as encoded in the component id.
     *
     * @return the action name, for example {@code edit_birth}
     */
    String action();

    /**
     * Parameters that follow the action in the component id.
     *
     * @return the parameters, possibly empty
     */
    List<String> params();

    /**
     * Parameter at the given index.
     *
     * @param index zero-based position
     * @return the parameter, or empty when the index is out of range
     */
    Optional<String> param(int index);

    /**
     * Parameter at the given index, which must exist.
     *
     * @param index zero-based position
     * @return the parameter
     * @throws UserFacingException when the index is out of range
     */
    String requireString(int index);

    /**
     * Parameter at the given index, parsed as a 32-bit integer.
     *
     * @param index zero-based position
     * @return the parsed value
     * @throws UserFacingException when the index is out of range or the value is
     *     not an integer
     */
    int requireInt(int index);

    /**
     * Parameter at the given index, parsed as a 64-bit integer.
     *
     * @param index zero-based position
     * @return the parsed value
     * @throws UserFacingException when the index is out of range or the value is
     *     not a long
     */
    long requireLong(int index);

    /**
     * Id of the user who triggered the interaction.
     *
     * @return the user id
     */
    String userId();

    /**
     * Id of the guild the interaction came from.
     *
     * @return the guild id
     */
    String guildId();

    /**
     * Id of the channel carrying the menu message.
     *
     * @return the channel id, or empty outside a channel
     */
    Optional<String> channelId();

    /**
     * Thread the menu message lives in.
     *
     * @return the thread, or empty when the channel is not a thread
     */
    Optional<ThreadChannel> thread();

    /**
     * JDA user entity.
     *
     * @return the user
     */
    User discordUser();

    /**
     * JDA guild entity.
     *
     * @return the guild
     */
    Guild guild();

    /**
     * JDA member entity.
     *
     * @return the member, or empty outside a guild
     */
    Optional<Member> member();

    /**
     * The JDA event that triggered this interaction.
     *
     * @return the button or modal event
     */
    IReplyCallback event();

    /**
     * Acknowledges with an ephemeral deferred reply.
     *
     * @return the pending action, to be queued by the caller
     */
    ReplyCallbackAction deferReply();

    /**
     * Acknowledges with a deferred edit and queues it.
     */
    void deferEdit();

    /**
     * Id of the menu message this interaction belongs to.
     *
     * @return the message id, or empty for a modal that was not opened from a
     *     message
     */
    OptionalLong messageId();

    /**
     * The locale this interaction is answered in.
     *
     * <p>Resolved once when the context was built, from the user's own Discord setting
     * where there is one and the guild's otherwise, so every part of a render agrees on
     * the language and no lookup happens per message.
     *
     * @return the locale to answer in, never null
     */
    Locale locale();

    /**
     * Resolves a user-facing message for {@link #locale()}.
     *
     * <p>The convenience a component needs to render text without holding a
     * {@code Messages} of its own.
     *
     * @param key a key declared in {@code MessageKeys}
     * @param args values for the {@code {n}} placeholders in that message
     * @return the translated text
     */
    String t(String key, Object... args);

    /**
     * The session for this message, created on first use.
     *
     * <p>State written here survives between clicks, which is the whole point of
     * moving it off the context.
     *
     * @return the session, never null
     */
    Session session();

    /**
     * The session for this message, without creating one.
     *
     * @return the session, or empty when none exists or it expired
     */
    Optional<Session> findSession();

    /**
     * Moves to another view, using the session for history.
     *
     * @param mode how to move
     * @param targetMenuId the menu to show, ignored by {@link NavigationMode#BACK}
     * @return a completed future
     * @throws UserFacingException if the target menu is not registered
     */
    CompletableFuture<Void> navigate(NavigationMode mode, String targetMenuId);

    /**
     * A context addressing a different view of the same message.
     *
     * <p>Used to re-render a remembered view while going back. The interaction,
     * user, guild, and session are shared; only the menu, action, and parameters
     * change.
     *
     * @param entry the view to address
     * @return a context for that view
     */
    MenuContext at(NavEntry entry);
}
