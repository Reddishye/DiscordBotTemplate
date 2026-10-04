package es.redactado.menu.preset;

/**
 * What a colour is being used for, rather than which colour it is.
 *
 * <p>A component says "this is a danger action" and the preset decides what danger looks
 * like. That indirection is the point: a menu author never writes a colour, so a bot can
 * be restyled by swapping one preset file, and a colour-blind or monochrome theme needs
 * no code changes at all.
 *
 * <p>The order matches the components of {@link Palette}, so a tone maps to exactly one
 * colour.
 */
public enum Tone {
    /** The menu's own colour, used for its accent and ordinary emphasis. */
    ACCENT,
    /** Something went right. */
    SUCCESS,
    /** Something needs attention but is not an error. */
    WARNING,
    /** Something is destructive or irreversible. */
    DANGER,
    /** Neutral information. */
    INFO,
    /** Deliberately unemphatic, such as a muted label. */
    NEUTRAL
}
