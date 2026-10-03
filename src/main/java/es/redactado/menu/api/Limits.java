package es.redactado.menu.api;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.SelectMenu;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.modals.Modal;

/**
 * Discord V2 component limits.
 * See https://discord.com/developers/docs/interactions/message-components
 *
 * <p>The select and modal limits are read from the JDA constants rather than copied as
 * numbers, so a JDA upgrade that moves a limit cannot leave a literal here contradicting
 * the library that enforces it.
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

    /** Max options in a string select menu. */
    public static final int MAX_SELECT_OPTIONS = SelectMenu.OPTIONS_MAX_AMOUNT;

    /** Max length of a select menu placeholder. */
    public static final int MAX_SELECT_PLACEHOLDER_LENGTH = SelectMenu.PLACEHOLDER_MAX_LENGTH;

    /** Max length of a select option's value, which is what a handler receives. */
    public static final int MAX_SELECT_VALUE_LENGTH = SelectOption.VALUE_MAX_LENGTH;

    /** Max length of a select option's label, which the user reads. */
    public static final int MAX_SELECT_LABEL_LENGTH = SelectOption.LABEL_MAX_LENGTH;

    /** Max length of a select option's description. */
    public static final int MAX_SELECT_DESCRIPTION_LENGTH = SelectOption.DESCRIPTION_MAX_LENGTH;

    /** Max fields in a modal. */
    public static final int MAX_MODAL_FIELDS = Modal.MAX_COMPONENTS;

    /** Max length of a modal title. */
    public static final int MAX_MODAL_TITLE_LENGTH = Modal.MAX_TITLE_LENGTH;

    /** Max length of a modal field's label. */
    public static final int MAX_MODAL_LABEL_LENGTH = Label.LABEL_MAX_LENGTH;

    /** Max length of a modal field's custom id. */
    public static final int MAX_MODAL_FIELD_ID_LENGTH = TextInput.MAX_ID_LENGTH;

    /** Max length of a modal field's placeholder. */
    public static final int MAX_MODAL_PLACEHOLDER_LENGTH = TextInput.MAX_PLACEHOLDER_LENGTH;

    /** Max length of a modal field's value. */
    public static final int MAX_MODAL_VALUE_LENGTH = TextInput.MAX_VALUE_LENGTH;
}
