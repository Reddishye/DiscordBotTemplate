package es.redactado.menu.core;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.StateNotFoundException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

public class BaseContext implements MenuContext {

    private final String menuId;
    private final String action;
    private final List<String> params;
    private final User user;
    private final Guild guild;
    private final Member member;
    private final Optional<String> channelId;
    private final Optional<ThreadChannel> thread;
    private final IReplyCallback event;
    private final Map<String, Object> state = new ConcurrentHashMap<>();
    private final Deque<MenuContext> backStack = new ArrayDeque<>();

    private BaseContext(
            String menuId,
            String action,
            List<String> params,
            User user,
            Guild guild,
            Member member,
            Optional<String> channelId,
            Optional<ThreadChannel> thread,
            IReplyCallback event) {
        this.menuId = menuId;
        this.action = action;
        this.params = params;
        this.user = user;
        this.guild = guild;
        this.member = member;
        this.channelId = channelId;
        this.thread = thread;
        this.event = event;
    }

    public static MenuContext fromButton(ButtonInteractionEvent e, ComponentId parsed) {
        return new BaseContext(
                parsed.menuId(),
                parsed.action(),
                parsed.params(),
                e.getUser(),
                e.getGuild(),
                e.getMember(),
                Optional.ofNullable(e.getChannelId()),
                e.getChannel() instanceof ThreadChannel t ? Optional.of(t) : Optional.empty(),
                e);
    }

    public static MenuContext fromModal(ModalInteractionEvent e, ComponentId parsed) {
        return new BaseContext(
                parsed.menuId(),
                parsed.action(),
                parsed.params(),
                e.getUser(),
                e.getGuild(),
                e.getMember(),
                Optional.ofNullable(e.getChannelId()),
                e.getChannel() instanceof ThreadChannel t ? Optional.of(t) : Optional.empty(),
                e);
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
        return index < params.size() ? Optional.of(params.get(index)) : Optional.empty();
    }

    @Override
    public String require(int index) {
        return param(index).orElseThrow(() -> new StateNotFoundException("param[" + index + "]"));
    }

    @Override
    public String userId() {
        return user.getId();
    }

    @Override
    public String guildId() {
        return guild.getId();
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
        if (event instanceof ButtonInteractionEvent be) be.deferEdit().queue();
        if (event instanceof ModalInteractionEvent me) me.deferEdit().queue();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T state(String key) {
        return (T) state.get(key);
    }

    @Override
    public <T> void setState(String key, T value) {
        state.put(key, value);
    }

    @Override
    public void removeState(String key) {
        state.remove(key);
    }

    @Override
    public void clearState() {
        state.clear();
    }

    @Override
    public int increment(String key) {
        int val = state.containsKey(key) ? (int) state.get(key) : 0;
        val++;
        state.put(key, val);
        return val;
    }

    @Override
    public void push(MenuContext previous) {
        backStack.push(previous);
    }

    @Override
    public Optional<MenuContext> pop() {
        return Optional.ofNullable(backStack.poll());
    }

    @Override
    public boolean hasPrevious() {
        return !backStack.isEmpty();
    }
}
