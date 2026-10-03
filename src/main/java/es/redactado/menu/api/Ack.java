package es.redactado.menu.api;

/**
 * How the router acknowledges an interaction before handing it to a handler.
 *
 * <p>Discord allows an interaction to be acknowledged exactly once, within three
 * seconds of the click. Declaring the mode per action keeps that deadline in one
 * place instead of spreading it across handler code.
 */
public enum Ack {

    /**
     * The router calls {@code event.deferEdit()} and the handler edits the
     * original message through the interaction hook. The default for anything
     * that re-renders a menu.
     */
    DEFER_EDIT,

    /**
     * The router calls {@code event.deferReply(true)} and the handler sends an
     * ephemeral follow-up. Use when the outcome is a notice rather than an edit.
     */
    DEFER_REPLY,

    /**
     * The router acknowledges nothing, because opening a modal must be the first
     * and only response to an interaction. A handler using this mode is
     * responsible for calling {@code replyModal} itself.
     */
    MODAL,

    /**
     * The router acknowledges nothing and the handler owns the whole response,
     * including the acknowledgement. Rare, and only for actions that must choose
     * between several response types at runtime.
     */
    NONE
}
