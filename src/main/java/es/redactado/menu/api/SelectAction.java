package es.redactado.menu.api;

/**
 * A declared string select action: how it is acknowledged and what runs.
 *
 * @param ack how the interaction is acknowledged before the handler runs
 * @param handler the work
 */
public record SelectAction(Ack ack, SelectHandler handler) {}
