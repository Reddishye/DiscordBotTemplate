package es.redactado.menu.api;

/**
 * Discord V2 component limits.
 * See https://discord.com/developers/docs/interactions/message-components
 */
public final class Limits {
    private Limits() {}

    /** Max ContainerChildComponent elements per Container. */
    public static final int MAX_CONTAINER_CHILDREN = 25;

    /** Warn threshold, below the hard container limit. */
    public static final int WARN_CONTAINER_CHILDREN = 20;

    /** Max buttons/selects per ActionRow. */
    public static final int MAX_ACTION_ROW_CHILDREN = 5;

    /** Max length of a custom_id. */
    public static final int MAX_CUSTOM_ID_LENGTH = 100;

    /** Max items in a MediaGallery. */
    public static final int MAX_MEDIA_GALLERY_ITEMS = 10;
}
