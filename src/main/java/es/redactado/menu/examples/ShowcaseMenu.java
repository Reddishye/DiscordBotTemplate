package es.redactado.menu.examples;

import es.redactado.menu.api.Ack;
import es.redactado.menu.api.ActionTable;
import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.NavEntry;
import es.redactado.menu.api.NavigationMode;
import es.redactado.menu.core.AbstractMenu;
import es.redactado.menu.preset.IconKey;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.PresetRegistry;
import es.redactado.menu.view.ActionButton;
import es.redactado.menu.view.Confirm;
import es.redactado.menu.view.Divider;
import es.redactado.menu.view.Field;
import es.redactado.menu.view.Header;
import es.redactado.menu.view.LinkButton;
import es.redactado.menu.view.MenuBuilder;
import es.redactado.menu.view.ModalForm;
import es.redactado.menu.view.Nav;
import es.redactado.menu.view.Pager;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.SelectMenu;
import es.redactado.menu.view.Text;
import es.redactado.menu.view.ThumbnailComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

/**
 * Every view of the framework on one menu, backed by fake data and doing no I/O.
 *
 * <p>This is the reference for what a menu can look like, and it is compiled and tested
 * but **not registered anywhere**: nothing here runs until a bot deliberately wires it
 * up, which is the only safe default for example code. The wiring belongs to whoever owns
 * the bot's startup, and it is one line in a {@code Listeners} or startup class.
 *
 * <p><strong>It previews presets instead of setting them.</strong> Picking a preset from
 * the presets view stores the name in this session only, and every view then renders
 * through {@link MenuContext#withPreset}. Nothing global changes: no guild preference, no
 * user preference, no file. That is what makes it safe to run against a real bot, and it
 * is also the quickest way to see what a preset actually looks like.
 *
 * <p><strong>Texts here are literals.</strong> This is the one package
 * {@code NoHardcodedUserTextTest} exempts, because an example that routed its own labels
 * through a bundle would show the framework's second-hardest problem instead of its
 * components. A real menu has no exemption: it puts every user-facing string in a bundle
 * and reads it through {@link MenuContext#t}.
 *
 * <p>Views are chosen by {@link MenuContext#action()}, and moving between them is a
 * declared {@code go} action rather than {@code Nav.push}, because a navigation id
 * addresses a <em>menu</em> while a view is an action inside one. See NOTES.md.
 *
 * <p><strong>A click names an interaction, not a view.</strong> A button declared
 * {@code delete} runs while the component view is on screen, so the action alone cannot say
 * what is being looked at. The current view is therefore remembered in the session under
 * {@link #VIEW_KEY}, and an action that is not itself a view redraws whatever view is
 * remembered. Getting this wrong is not subtle: Back would return to a view named after the
 * button that was pressed, which renders the home view and looks like the history is broken.
 */
public final class ShowcaseMenu extends AbstractMenu {

    /** Session key holding the preset this menu is previewing. */
    public static final String PRESET_KEY = "showcase:preset";

    /** Session key holding the ids of the fake items still present. */
    public static final String ITEMS_KEY = "showcase:items";

    /** Session key holding the answers of the last submitted form. */
    public static final String ANSWERS_KEY = "showcase:answers";

    /** Session key holding the name of the role button last pressed. */
    public static final String DEMO_KEY = "showcase:demo";

    /** Session key holding the value submitted through the edit form. */
    public static final String EDITED_KEY = "showcase:edited";

    /** Session key holding which view this message is showing. */
    public static final String VIEW_KEY = "showcase:view";

    static final String HOME = "home";
    static final String COMPONENTS = "components";
    static final String PRESETS = "presets";
    static final String CONFIRM = "confirm";
    static final String MODAL = "modal";

    static final String GO = "go";
    static final String DEMO = "demo";
    static final String DELETE = "delete";
    static final String PICK_PRESET = "pick_preset";
    static final String OPEN_FORM = "open_form";
    static final String SUBMIT_FORM = "submit_form";
    static final String EDIT_FIELD = "edit_field";
    static final String SUBMIT_EDIT = "submit_edit";
    static final String CONFIRM_YES = "confirm_yes";
    static final String CONFIRM_NO = "confirm_no";

    /** Placeholder image, never fetched by this class: a menu only renders the URL. */
    private static final String IMAGE = "https://example.invalid/showcase.png";

    private static final String DOCS = "https://example.invalid/docs";
    private static final int FAKE_ITEM_COUNT = 25;
    private static final int PAGE_SIZE = 5;
    private static final int ANSWER_LIMIT = 120;

    private final PresetRegistry registry;

    /**
     * @param registry the presets this menu offers to preview
     */
    public ShowcaseMenu(PresetRegistry registry) {
        super("showcase");
        this.registry = registry;
    }

    @Override
    protected void declare(ActionTable.Builder table) {
        table.button(GO, Ack.DEFER_EDIT, this::goTo);
        table.button(DEMO, Ack.DEFER_EDIT, this::demoPressed);
        table.button(DELETE, Ack.DEFER_EDIT, (ctx, event) -> showView(ctx, CONFIRM));
        table.select(PICK_PRESET, Ack.DEFER_EDIT, this::presetPicked);
        table.button(OPEN_FORM, Ack.MODAL, this::openForm);
        table.modal(SUBMIT_FORM, Ack.DEFER_REPLY, this::formSubmitted);
        table.button(EDIT_FIELD, Ack.MODAL, this::openEditForm);
        table.modal(SUBMIT_EDIT, Ack.DEFER_REPLY, this::editSubmitted);
        table.button(CONFIRM_YES, Ack.DEFER_EDIT, (ctx, event) -> deleteAnItem(ctx));
        table.button(
                CONFIRM_NO, Ack.DEFER_EDIT, (ctx, event) -> ctx.navigate(NavigationMode.BACK, ""));
    }

    @Override
    public CompletableFuture<Container> render(MenuContext ctx) {
        String view = viewOf(ctx);
        ctx.session().putState(VIEW_KEY, view);
        // Every view renders through withPreset, so a preview chosen in one view is what
        // the next view draws too.
        MenuContext drawing = ctx.withPreset(activePreset(ctx));
        return CompletableFuture.completedFuture(containerFor(drawing, view));
    }

    /**
     * The view this interaction is about, falling back to the remembered one.
     *
     * <p>A view named by the action wins, because that is how a navigation target and an
     * initial open both arrive. Anything else, such as a button or a page arrow, redraws
     * what is already on screen.
     */
    private String viewOf(MenuContext ctx) {
        String named = ctx.action();
        if (VIEWS.contains(named)) {
            return named;
        }
        return ctx.session().state(VIEW_KEY, String.class).filter(VIEWS::contains).orElse(HOME);
    }

    // ------------------------------------------------------------- the views

    private Container containerFor(MenuContext ctx, String view) {
        return switch (view) {
            case HOME -> homeView(ctx);
            case COMPONENTS -> components(ctx);
            case PRESETS -> presets(ctx);
            case CONFIRM -> confirm(ctx);
            case MODAL -> modal(ctx);
            default -> homeView(ctx);
        };
    }

    private Container homeView(MenuContext ctx) {
        return MenuBuilder.create(id())
                .add(
                        Header.of("Menu showcase")
                                .subtitle("Every component, one menu")
                                .icon(IconKey.INFO))
                .add(
                        Text.of(
                                "Each button below opens a view of this same menu. Nothing here"
                                        + " touches a database or a network."))
                .add(
                        Row.of(
                                ActionButton.primary(GO, "Components").params(COMPONENTS),
                                ActionButton.secondary(GO, "Presets").params(PRESETS)))
                .add(
                        Row.of(
                                ActionButton.success(GO, "Confirmation").params(CONFIRM),
                                ActionButton.danger(GO, "Modal form").params(MODAL)))
                .add(Row.of(Nav.push(id(), "Reload the showcase")))
                .build(ctx);
    }

    private Container components(MenuContext ctx) {
        return MenuBuilder.create(id())
                .add(
                        Header.of("Components")
                                .subtitle("Everything the view layer can render")
                                .icon(IconKey.SETTINGS))
                .add(Text.of("Text is passed through untouched, so a menu can use markdown."))
                .add(Field.of("Read-only field", "the value"))
                .add(
                        Field.editable(
                                        "Editable field",
                                        ctx.session()
                                                .state(EDITED_KEY, String.class)
                                                .orElse("press edit"),
                                        EDIT_FIELD)
                                .icon(IconKey.EDIT))
                .add(Divider.line())
                .add(Divider.space())
                .add(thumbnailSection(IMAGE, "A section with a thumbnail accessory."))
                .add(
                        Row.of(
                                ActionButton.primary(DEMO, "Primary").params("primary"),
                                ActionButton.secondary(DEMO, "Secondary").params("secondary"),
                                ActionButton.success(DEMO, "Success").params("success"),
                                ActionButton.danger(DELETE, "Danger")))
                .add(Text.small(lastDemo(ctx)))
                .add(Row.of(LinkButton.of(DOCS, "Documentation").icon(IconKey.LINK)))
                .add(
                        Pager.of(
                                "items",
                                items(ctx),
                                PAGE_SIZE,
                                item -> Field.of("Fake item " + item, "generated in memory")))
                .add(Row.of(Nav.back(), ActionButton.secondary(GO, "Confirm").params(CONFIRM)))
                .build(ctx);
    }

    private Container presets(MenuContext ctx) {
        return MenuBuilder.create(id())
                .add(
                        Header.of("Presets")
                                .subtitle("Preview a look without changing it")
                                .icon(IconKey.INFO))
                .add(
                        Text.of(
                                "Picking one changes this session only. Guild and user preferences"
                                        + " are untouched."))
                .add(presetSelect())
                .add(Text.small("Presetting: " + activePreset(ctx).name()))
                .add(Row.of(Nav.back()))
                .build(ctx);
    }

    private Container confirm(MenuContext ctx) {
        return MenuBuilder.create(id())
                .add(
                        Confirm.of(Text.of("Remove a fake item from this session?"), CONFIRM_YES)
                                .danger())
                .add(Text.small("Remaining items: " + items(ctx).size()))
                .add(Row.of(Nav.back()))
                .build(ctx);
    }

    private Container modal(MenuContext ctx) {
        MenuBuilder builder =
                MenuBuilder.create(id())
                        .add(
                                Header.of("Modal form")
                                        .subtitle("A form is the first and only answer")
                                        .icon(IconKey.EDIT))
                        .add(
                                Text.of(
                                        "The button below opens a modal. Discord allows one modal"
                                            + " per interaction, so the action declares Ack.MODAL"
                                            + " and the router acknowledges nothing."))
                        .add(
                                Row.of(
                                        ActionButton.primary(OPEN_FORM, "Open the form")
                                                .params(FORM_FIELD),
                                        Nav.back()));
        answers(ctx).forEach((field, value) -> builder.add(Field.of(field, clipped(value))));
        return builder.build(ctx);
    }

    // ------------------------------------------------------------- components

    /** One option per preset, with its description clipped to what an option accepts. */
    private Row presetSelect() {
        SelectMenu select = SelectMenu.of(PICK_PRESET, "Pick a preset to preview");
        for (Preset preset : registry.all()) {
            select = select.option(preset.name(), preset.name(), clipped(preset.description()));
        }
        return Row.of(select);
    }

    /**
     * A section with a thumbnail beside its text.
     *
     * <p>Assembled from JDA directly because a thumbnail is an accessory rather than a
     * container child, so it has to go inside a section to reach a container at all.
     */
    private static MenuComponent thumbnailSection(String image, String caption) {
        return ctx ->
                List.of(
                        Section.of(
                                ThumbnailComponent.of(image).render(ctx), TextDisplay.of(caption)));
    }

    // ------------------------------------------------------------- handlers

    /** Moves to another view of this menu, remembering where it came from. */
    private CompletableFuture<Void> goTo(MenuContext ctx, ButtonInteractionEvent event) {
        return showView(ctx, ctx.requireString(0));
    }

    /**
     * Renders a named view, pushing the current one so Back returns here.
     *
     * <p>Mirrors what the built-in page action does, and is needed because a navigation id
     * addresses a menu rather than a view.
     */
    private CompletableFuture<Void> showView(MenuContext ctx, String view) {
        // The remembered view, not ctx.action(): the action is the button that was
        // pressed, and putting that on the stack would send Back to a view named after it.
        ctx.session().push(new NavEntry(ctx.menuId(), viewOf(ctx), ctx.params()));
        return refresh(ctx.at(new NavEntry(ctx.menuId(), view, ctx.params())));
    }

    /** Stores the picked preset in this session and redraws. */
    private CompletableFuture<Void> presetPicked(
            MenuContext ctx, StringSelectInteractionEvent event) {
        ctx.session().putState(PRESET_KEY, event.getValues().getFirst());
        return refresh(ctx);
    }

    /**
     * Records which role button was pressed.
     *
     * <p>The four buttons share one action and differ only by their parameter, so the table
     * stays small while every button still routes somewhere real. An undeclared button would
     * answer "unknown action" on click, which is the wrong lesson for a showcase.
     */
    private CompletableFuture<Void> demoPressed(MenuContext ctx, ButtonInteractionEvent event) {
        ctx.session().putState(DEMO_KEY, ctx.requireString(0));
        return refresh(ctx);
    }

    /** The last role pressed, or a prompt to press one. */
    private String lastDemo(MenuContext ctx) {
        return ctx.session()
                .state(DEMO_KEY, String.class)
                .map(pressed -> "Last role pressed: " + pressed)
                .orElse("No role button pressed yet.");
    }

    private CompletableFuture<Void> openForm(MenuContext ctx, ButtonInteractionEvent event) {
        ModalForm form = ModalForm.create(ctx, SUBMIT_FORM, "Tell us about yourself", FORM_FIELD);
        form.shortField(NAME, "Name").placeholder("Ada Lovelace").required(true).length(1, 40);
        form.paragraph(REASON, "Why?").required(false).length(0, 200);
        showModal(ctx, form.build());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> formSubmitted(MenuContext ctx, ModalInteractionEvent event) {
        Map<String, String> submitted = ModalForm.read(event);
        submitted.keySet().stream()
                .sorted()
                .forEach(
                        field ->
                                ctx.session()
                                        .putState(ANSWERS_KEY + ":" + field, submitted.get(field)));
        // The submission's own action is not a view, so the modal view is addressed
        // explicitly before redrawing.
        return refresh(ctx.at(new NavEntry(ctx.menuId(), MODAL, List.of(FORM_FIELD))));
    }

    /** The edit button beside a field opens a form rather than doing the edit inline. */
    private CompletableFuture<Void> openEditForm(MenuContext ctx, ButtonInteractionEvent event) {
        ModalForm form = ModalForm.create(ctx, SUBMIT_EDIT, "Edit the field");
        form.shortField(EDIT_VALUE, "Value").value("the value").required(true).length(0, 60);
        showModal(ctx, form.build());
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> editSubmitted(MenuContext ctx, ModalInteractionEvent event) {
        ctx.session().putState(EDITED_KEY, ModalForm.read(event).getOrDefault(EDIT_VALUE, ""));
        return refresh(ctx.at(new NavEntry(ctx.menuId(), COMPONENTS, List.of())));
    }

    private CompletableFuture<Void> deleteAnItem(MenuContext ctx) {
        List<String> remaining = new ArrayList<>(items(ctx));
        if (!remaining.isEmpty()) {
            remaining.removeLast();
            ctx.session().putState(ITEMS_KEY, remaining);
        }
        return ctx.navigate(NavigationMode.BACK, "");
    }

    // ------------------------------------------------------------- fake state

    /** The preset this session is previewing, or the one the router resolved. */
    private Preset activePreset(MenuContext ctx) {
        String chosen = ctx.session().state(PRESET_KEY, String.class).orElse(null);
        return chosen == null ? ctx.preset() : registry.getOrDefault(chosen);
    }

    /** The fake items, dropping any this session deleted. */
    @SuppressWarnings("unchecked")
    private List<String> items(MenuContext ctx) {
        return ctx.session()
                .state(ITEMS_KEY, List.class)
                .map(stored -> (List<String>) stored)
                .orElseGet(() -> generatedItems());
    }

    private static List<String> generatedItems() {
        List<String> items = new ArrayList<>(FAKE_ITEM_COUNT);
        for (int i = 1; i <= FAKE_ITEM_COUNT; i++) {
            items.add(String.valueOf(i));
        }
        return items;
    }

    private static Map<String, String> answers(MenuContext ctx) {
        Map<String, String> found = new LinkedHashMap<>();
        found.put(NAME, ctx.session().state(ANSWERS_KEY + ":" + NAME, String.class).orElse(""));
        found.put(REASON, ctx.session().state(ANSWERS_KEY + ":" + REASON, String.class).orElse(""));
        found.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        return found;
    }

    /** Clips to a limit, with an ellipsis when something was cut. */
    private static String clipped(String value) {
        if (value.length() <= ANSWER_LIMIT) {
            return value;
        }
        return value.substring(0, ANSWER_LIMIT - 1) + "\\u2026";
    }

    static final String FORM_FIELD = "form";

    /** Every view, in the order the home view offers them. */
    static final List<String> VIEWS = List.of(HOME, COMPONENTS, PRESETS, CONFIRM, MODAL);

    static final String NAME = "name";
    static final String REASON = "reason";
    static final String EDIT_VALUE = "value";
}
