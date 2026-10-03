package es.redactado.menu.api;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/**
 * Request-scoped context for a single menu render or interaction.
 *
 * <p>Carries the parsed action data, the Discord entities involved, and the
 * per-event state of the source implementation. The state and back-stack
 * accessors are retained for the port and are expected to move into a
 * message-scoped session.
 */
public interface Context {

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
     * @throws StateNotFoundException when the index is out of range
     */
    String require(int index);

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
     * Reads a state value.
     *
     * @param key state key
     * @param <T> value type
     * @return the stored value, or {@code null} when absent
     */
    <T> T state(String key);

    /**
     * Stores a state value.
     *
     * @param key state key
     * @param value value to store
     * @param <T> value type
     */
    <T> void setState(String key, T value);

    /**
     * Removes a state value.
     *
     * @param key state key
     */
    void removeState(String key);

    /** Removes every state value held by this context. */
    void clearState();

    /**
     * Increments an integer state value, treating an absent key as zero.
     *
     * @param key state key
     * @return the value after incrementing
     */
    int increment(String key);

    /**
     * Pushes a context onto the back stack.
     *
     * @param previous the context to remember
     */
    void push(Context previous);

    /**
     * Pops the most recent context off the back stack.
     *
     * @return the previous context, or empty when the stack is empty
     */
    Optional<Context> pop();

    /**
     * Reports whether a previous context is on the back stack.
     *
     * @return {@code true} when the stack is not empty
     */
    boolean hasPrevious();
}
