package es.redactado.menu.core;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.UserFacingException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/**
 * Immutable context for one interaction.
 *
 * <p>Holds no per-interaction state map and no back stack. Anything that has to
 * outlive the click lives in the {@link Session} this context points at.
 *
 * <p>Construction allocates only the context itself; the session is looked up
 * lazily, so a context is as cheap as the parsed id it came from.
 */
public final class BaseContext implements MenuContext {

    private static final String INVALID_PARAM_TEXT = "Invalid or missing parameter.";
    private static final Supplier<UserFacingException> INVALID_PARAM = BaseContext::invalidParam;

    private final String menuId;
    private final String action;
    private final List<String> params;
    private final User user;
    private final Guild guild;
    private final Member member;
    private final Optional<String> channelId;
    private final Optional<ThreadChannel> thread;
    private final IReplyCallback event;
    private final OptionalLong messageId;
    private final SessionStore sessions;
    private final Navigator navigator;

    private BaseContext(
            String menuId,
            String action,
            List<String> params,
            User user,
            Guild guild,
            Member member,
            Optional<String> channelId,
            Optional<ThreadChannel> thread,
            IReplyCallback event,
            OptionalLong messageId,
            SessionStore sessions,
            Navigator navigator) {
        this.menuId = menuId;
        this.action = action;
        this.params = params;
        this.user = user;
        this.guild = guild;
        this.member = member;
        this.channelId = channelId;
        this.thread = thread;
        this.event = event;
        this.messageId = messageId;
        this.sessions = sessions;
        this.navigator = navigator;
    }

    /**
     * Builds a context from a button click.
     *
     * @param event the JDA button event
     * @param parsed the decoded component id
     * @param sessions the store backing this context's session
     * @param navigator the navigator used by {@link #navigate}
     * @return the context
     */
    public static MenuContext fromButton(
            ButtonInteractionEvent event,
            ComponentId parsed,
            SessionStore sessions,
            Navigator navigator) {
        return from(event, parsed, sessions, navigator);
    }

    /**
     * Builds a context from a modal submission.
     *
     * @param event the JDA modal event
     * @param parsed the decoded modal id
     * @param sessions the store backing this context's session
     * @param navigator the navigator used by {@link #navigate}
     * @return the context
     */
    public static MenuContext fromModal(
            ModalInteractionEvent event,
            ComponentId parsed,
            SessionStore sessions,
            Navigator navigator) {
        return from(event, parsed, sessions, navigator);
    }

    private static MenuContext from(
            IReplyCallback event, ComponentId parsed, SessionStore sessions, Navigator navigator) {
        Message message =
                event instanceof ButtonInteractionEvent button
                        ? button.getMessage()
                        : event instanceof ModalInteractionEvent modal ? modal.getMessage() : null;
        OptionalLong messageId =
                message == null ? OptionalLong.empty() : OptionalLong.of(message.getIdLong());
        return new BaseContext(
                parsed.menuId(),
                parsed.action(),
                parsed.params(),
                event.getUser(),
                event.getGuild(),
                event.getMember(),
                Optional.ofNullable(event.getChannelId()),
                event.getChannel() instanceof ThreadChannel asThread
                        ? Optional.of(asThread)
                        : Optional.empty(),
                event,
                messageId,
                sessions,
                navigator);
    }

    @Override
    public String menuId() {
        return menuId;
    }

    @Override
    public String action() {
        return action;
    }

    @Override
    public List<String> params() {
        return params;
    }

    @Override
    public Optional<String> param(int index) {
        return index >= 0 && index < params.size()
                ? Optional.of(params.get(index))
                : Optional.empty();
    }

    @Override
    public String requireString(int index) {
        return param(index).filter(value -> !value.isBlank()).orElseThrow(INVALID_PARAM);
    }

    @Override
    public int requireInt(int index) {
        try {
            return Integer.parseInt(requireString(index));
        } catch (NumberFormatException e) {
            throw invalidParam();
        }
    }

    @Override
    public long requireLong(int index) {
        try {
            return Long.parseLong(requireString(index));
        } catch (NumberFormatException e) {
            throw invalidParam();
        }
    }

    private static UserFacingException invalidParam() {
        return new UserFacingException(INVALID_PARAM_TEXT);
    }

    @Override
    public String userId() {
        return user == null ? "" : user.getId();
    }

    @Override
    public String guildId() {
        return guild == null ? "" : guild.getId();
    }

    @Override
    public Optional<String> channelId() {
        return channelId;
    }

    @Override
    public Optional<ThreadChannel> thread() {
        return thread;
    }

    @Override
    public User discordUser() {
        return user;
    }

    @Override
    public Guild guild() {
        return guild;
    }

    @Override
    public Optional<Member> member() {
        return Optional.ofNullable(member);
    }

    @Override
    public IReplyCallback event() {
        return event;
    }

    @Override
    public ReplyCallbackAction deferReply() {
        return event.deferReply(true);
    }

    @Override
    public void deferEdit() {
        if (event instanceof ButtonInteractionEvent button) {
            button.deferEdit().queue();
        } else if (event instanceof ModalInteractionEvent modal) {
            modal.deferEdit().queue();
        }
    }

    @Override
    public OptionalLong messageId() {
        return messageId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>An interaction with no message, such as a modal not opened from one, gets
     * a detached session that nothing else can reach. State written to it is
     * discarded when the interaction ends, which is the honest outcome: there is no
     * message to hang history on.
     */
    @Override
    public Session session() {
        if (messageId.isEmpty()) {
            return new Session();
        }
        return sessions.getOrCreate(messageId.getAsLong());
    }

    @Override
    public Optional<Session> findSession() {
        if (messageId.isEmpty()) {
            return Optional.empty();
        }
        return sessions.find(messageId.getAsLong());
    }

    @Override
    public CompletableFuture<Void> navigate(NavigationMode mode, String targetMenuId) {
        return navigator.go(this, mode, targetMenuId);
    }

    @Override
    public MenuContext at(NavEntry entry) {
        return new BaseContext(
                entry.menuId(),
                entry.action(),
                entry.params(),
                user,
                guild,
                member,
                channelId,
                thread,
                event,
                messageId,
                sessions,
                navigator);
    }
}
