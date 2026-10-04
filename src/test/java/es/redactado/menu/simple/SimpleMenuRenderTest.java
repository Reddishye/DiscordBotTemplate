package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es.redactado.menu.api.Limits;
import es.redactado.menu.api.Menu;
import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.Msg;
import es.redactado.menu.api.Session;
import es.redactado.menu.api.ValidationResult;
import es.redactado.menu.api.Validator;
import es.redactado.menu.core.MessageKeys;
import es.redactado.menu.preset.BuiltinPresets;
import es.redactado.menu.preset.Preset;
import es.redactado.menu.preset.Tone;
import es.redactado.menu.view.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * What a declared view draws.
 *
 * <p>A simple menu is a promise that the ordinary framework renders what it was told to.
 * These tests read the container the menu actually produced rather than the builder it was
 * given, under every built-in preset, because a preset that made one element illegal or
 * invisible would be a bug in the menu rather than in the preset.
 */
class SimpleMenuRenderTest {

    /** Every element the content side of the DSL has, in one view. */
    private static final List<String> ELEMENTS =
            List.of(
                    "header",
                    "header-subtitle",
                    "text",
                    "text-msg",
                    "text-fn",
                    "field",
                    "divider",
                    "space",
                    "section",
                    "list",
                    "custom");

    static Stream<Preset> presets() {
        return BuiltinPresets.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("presets")
    @DisplayName("every element renders inside Discord's limits under every preset")
    void everyElementRendersWithinLimits(Preset preset) {
        Container container = render(preset, SimpleMenuRenderTest::allElements);

        ValidationResult result = Validator.validate(container);
        assertThat(result.isValid())
                .as("%s must render a valid container, but: %s", preset.name(), result.errors())
                .isTrue();
        assertThat(container.getComponents().size())
                .as("%s child count", preset.name())
                .isLessThanOrEqualTo(Limits.MAX_CONTAINER_CHILDREN);
        assertThat(texts(container))
                .as("%s must draw every text element", preset.name())
                .anyMatch(text -> text.contains("Body"))
                .anyMatch(text -> text.contains("Field value"))
                .anyMatch(text -> text.contains("From the model"))
                .anyMatch(text -> text.contains("item 0"));
    }

    @Test
    @DisplayName("a header draws its title, and its subtitle only when the preset wants one")
    void headerFollowsThePreset() {
        Preset withSubtitle = BuiltinPresets.MIDNIGHT;
        Preset without = BuiltinPresets.MINIMAL;

        Container shown =
                render(
                        withSubtitle,
                        view -> view.header(Msg.literal("Title"), Msg.literal("Subtitle")));
        Container dropped =
                render(without, view -> view.header(Msg.literal("Title"), Msg.literal("Subtitle")));

        assertThat(texts(shown))
                .as("a preset with subtitles shows them")
                .anyMatch(text -> text.contains("Title") && text.contains("Subtitle"));
        assertThat(texts(dropped))
                .as("a preset without subtitles drops them rather than rendering them small")
                .anyMatch(text -> text.contains("Title"))
                .noneMatch(text -> text.contains("Subtitle"));
    }

    @Test
    @DisplayName("a key is resolved in the reader's language")
    void keysAreLocalized() {
        Menu menu =
                Menus.simple("demo").home(v -> v.message(Msg.key(MessageKeys.NAV_BACK))).build();

        assertThat(texts(render(menu, BuiltinPresets.DEFAULT, Locale.ENGLISH))).contains("Back");
        assertThat(texts(render(menu, BuiltinPresets.DEFAULT, Locale.forLanguageTag("es"))))
                .contains("Atr\u00E1s");
    }

    @Test
    @DisplayName("a literal is not translated, which is why it suits content")
    void literalsAreLeftAlone() {
        Menu menu = Menus.simple("demo").home(v -> v.text("Pick a topic.")).build();

        assertThat(texts(render(menu, BuiltinPresets.DEFAULT, Locale.ENGLISH)))
                .contains("Pick a topic.");
        assertThat(texts(render(menu, BuiltinPresets.DEFAULT, Locale.forLanguageTag("es"))))
                .as("an author who writes a literal has accepted that it is one language")
                .contains("Pick a topic.");
    }

    @Test
    @DisplayName("a field draws its label and value side by side")
    void fieldDrawsLabelAndValue() {
        Container container =
                render(
                        BuiltinPresets.DEFAULT,
                        view -> view.field(Msg.literal("Queue"), scope -> "7"));

        assertThat(texts(container))
                .as("label and value in one line")
                .anyMatch(text -> text.contains("Queue") && text.contains("7"));
    }

    @Test
    @DisplayName("a divider and a space both draw something of their own")
    void dividerAndSpaceAreDistinct() {
        Container container =
                render(
                        BuiltinPresets.DEFAULT,
                        view -> view.text("Above").divider().text("Below").space().text("After"));

        assertThat(container.getComponents()).hasSize(5);
    }

    @Test
    @DisplayName("a section draws its text with the image beside it")
    void sectionDrawsThumbnail() {
        Container container =
                render(
                        BuiltinPresets.DEFAULT,
                        view -> view.section(Msg.literal("Art"), "https://example.com/a.png"));

        assertThat(container.getComponents()).hasSize(1);
        Section section = (Section) container.getComponents().getFirst();
        Thumbnail accessory = (Thumbnail) section.getAccessory();
        assertThat(accessory.getUrl()).isEqualTo("https://example.com/a.png");
        assertThat(section.getContentComponents())
                .anyMatch(
                        part ->
                                part instanceof TextDisplay display
                                        && display.getContent().contains("Art"));
    }

    @Test
    @DisplayName("a list draws a page of items and remembers which page it is on")
    void listPagesAndKeepsState() {
        Menu menu =
                Menus.simple("demo")
                        .home(v -> v.list("items", scope -> tenItems(), 5, String::toString))
                        .build();
        Session session = new Session();

        Container first =
                menu.render(
                                SimpleContexts.of(
                                        "demo",
                                        "home",
                                        BuiltinPresets.DEFAULT,
                                        Locale.ENGLISH,
                                        session))
                        .join();
        assertThat(texts(first))
                .as("the first page of five")
                .contains("item 0", "item 4")
                .doesNotContain("item 5");
        assertThat(buttons(first)).as("arrows, because there is a second page").hasSize(3);

        session.putState(es.redactado.menu.view.Pager.stateKey("items"), 1);
        Container second =
                menu.render(
                                SimpleContexts.of(
                                        "demo",
                                        "home",
                                        BuiltinPresets.DEFAULT,
                                        Locale.ENGLISH,
                                        session))
                        .join();

        assertThat(texts(second))
                .as("the second page, from the state the redraw shared")
                .contains("item 5", "item 9")
                .doesNotContain("item 0");
    }

    @Test
    @DisplayName("an empty list says so, in the reader's language")
    void emptyListIsExplained() {
        Menu menu =
                Menus.simple("demo")
                        .home(v -> v.list("items", scope -> List.of(), 5, String::toString))
                        .build();

        assertThat(
                        texts(
                                menu.render(
                                                SimpleContexts.of(
                                                        "demo",
                                                        "home",
                                                        BuiltinPresets.DEFAULT,
                                                        Locale.ENGLISH))
                                        .join()))
                .anyMatch(text -> text.contains("No items"));
    }

    @Test
    @DisplayName("a custom component is drawn as it came")
    void customComponentIsDrawn() {
        Container container =
                render(
                        BuiltinPresets.DEFAULT,
                        view -> view.custom(Text.small("from an escape hatch")));

        assertThat(texts(container))
                .as("the component drew itself, small-text prefix and all")
                .anyMatch(text -> text.contains("from an escape hatch"));
    }

    @Test
    @DisplayName("a custom component built from the scope sees the model")
    void customComponentSeesTheModel() {
        java.util.function.Function<Scope<Integer>, MenuComponent> scoped =
                scope -> Text.of("count " + scope.data());
        Menu menu =
                Menus.simple(
                                "demo",
                                ctx -> java.util.concurrent.CompletableFuture.completedFuture(9))
                        .home(v -> v.custom(scoped))
                        .build();

        assertThat(
                        texts(
                                menu.render(
                                                SimpleContexts.of(
                                                        "demo",
                                                        "home",
                                                        BuiltinPresets.DEFAULT,
                                                        Locale.ENGLISH))
                                        .join()))
                .contains("count 9");
    }

    @Test
    @DisplayName("a menu with no loader has a null model, and says so")
    void noLoaderMeansNullData() {
        Menu menu =
                Menus.simple("demo").home(v -> v.text(scope -> "data is " + scope.data())).build();

        assertThat(
                        texts(
                                menu.render(
                                                SimpleContexts.of(
                                                        "demo",
                                                        "home",
                                                        BuiltinPresets.DEFAULT,
                                                        Locale.ENGLISH))
                                        .join()))
                .as("the loader-less form has nothing to hand, and null is the honest value")
                .anyMatch(text -> text.contains("data is null"));
    }

    @Test
    @DisplayName("the loader runs once per render, and its model reaches the text")
    void loaderRunsOncePerRender() {
        int[] calls = new int[1];
        Menu menu =
                Menus.simple(
                                "demo",
                                ctx -> {
                                    calls[0]++;
                                    return java.util.concurrent.CompletableFuture.completedFuture(
                                            new Model("Ada"));
                                })
                        .home(v -> v.text(scope -> "Total: " + scope.data().total()))
                        .build();

        Container container =
                menu.render(
                                SimpleContexts.of(
                                        "demo", "home", BuiltinPresets.DEFAULT, Locale.ENGLISH))
                        .join();

        assertThat(calls[0]).as("one render, one load").isEqualTo(1);
        assertThat(texts(container)).anyMatch(text -> text.contains("Total: 3"));
    }

    @Test
    @DisplayName("a loader failure surfaces as the localized error, not a stack trace")
    void loaderFailureIsLocalized() {
        Menu menu =
                Menus.simple(
                                "demo",
                                ctx ->
                                        java.util.concurrent.CompletableFuture.failedFuture(
                                                new IllegalStateException("down")))
                        .home(v -> v.text("never"))
                        .build();

        assertThatThrownBy(
                        () ->
                                menu.render(
                                                SimpleContexts.of(
                                                        "demo",
                                                        "home",
                                                        BuiltinPresets.DEFAULT,
                                                        Locale.ENGLISH))
                                        .join())
                .as(
                        "the render fails with the loader's own error, which the router turns into"
                            + " one localized sentence; the framework never leaks the message text")
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a loader that throws outright is still a failed future, not a thrown exception")
    void throwingLoaderIsAFailedFuture() {
        Menu menu =
                Menus.simple(
                                "demo",
                                ctx -> {
                                    throw new IllegalStateException("down");
                                })
                        .home(v -> v.text("never"))
                        .build();

        assertThatThrownBy(
                        () ->
                                menu.render(
                                                SimpleContexts.of(
                                                        "demo",
                                                        "home",
                                                        BuiltinPresets.DEFAULT,
                                                        Locale.ENGLISH))
                                        .join())
                .as(
                        "a loader that throws is a failed future, not an exception thrown at the"
                                + " dispatcher that opened the render")
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the tone the menu asked for is the colour it renders in")
    void toneIsApplied() {
        Menu menu = Menus.simple("demo").tone(Tone.DANGER).home(v -> v.text("Body")).build();

        Container container =
                menu.render(
                                SimpleContexts.of(
                                        "demo", "home", BuiltinPresets.DEFAULT, Locale.ENGLISH))
                        .join();

        assertThat(container.getAccentColorRaw())
                .as("the menu asked for danger, so the accent is the danger colour")
                .isEqualTo(BuiltinPresets.DEFAULT.palette().danger());
    }

    @Test
    @DisplayName("the preset the menu forces is reported as its own")
    void forcedPresetIsReported() {
        assertThat(
                        Menus.simple("demo")
                                .preset("midnight")
                                .home(v -> v.text("x"))
                                .build()
                                .presetName())
                .contains("midnight");
        assertThat(Menus.simple("demo").home(v -> v.text("x")).build().presetName())
                .as("no forced preset means the menu follows the preferences")
                .isEmpty();
    }

    @Test
    @DisplayName("a shared menu is shared, and a personal one is not")
    void sharedIsReported() {
        assertThat(Menus.simple("demo").shared().home(v -> v.text("x")).build().shared()).isTrue();
        assertThat(Menus.simple("demo").home(v -> v.text("x")).build().shared()).isFalse();
    }

    @Test
    @DisplayName("each view is rendered by its own name, and a view name opens as an action")
    void viewsAreReachedByName() {
        Menu menu =
                Menus.simple("demo")
                        .home(v -> v.text("Home"))
                        .view("detail", v -> v.text("Detail"))
                        .build();

        assertThat(texts(renderView(menu, "home"))).contains("Home");
        assertThat(texts(renderView(menu, "detail"))).contains("Detail");
        assertThat(
                        menu.home(
                                SimpleContexts.of(
                                        "demo", "home", BuiltinPresets.DEFAULT, Locale.ENGLISH)))
                .as("opening a menu lands on home")
                .isEqualTo(new es.redactado.menu.api.NavEntry("demo", "home", List.of()));
    }

    @Test
    @DisplayName("an unknown view name is refused, not rendered blank")
    void unknownViewIsRefused() {
        Menu menu = Menus.simple("demo").home(v -> v.text("Home")).build();

        assertThatThrownBy(() -> renderView(menu, "nope"))
                .as("a stale message gets one localized sentence, not a blank container")
                .hasMessageContaining("menu.error.unknown_view");
    }

    private record Model(String name) {
        int total() {
            return 3;
        }
    }

    private static void allElements(ViewBuilder<Void> v) {
        v.header(Msg.literal("Title"), Msg.literal("Subtitle"))
                .text("Body")
                .message(Msg.key(MessageKeys.NAV_BACK))
                .text(scope -> "From the model: " + scope.data())
                .field(Msg.literal("Field"), scope -> "Field value")
                .divider()
                .space()
                .section(Msg.literal("Art"), "https://example.com/a.png")
                .list("items", scope -> tenItems(), 5, String::toString)
                .custom(Text.small("escape hatch"));
    }

    private static Container render(
            Preset preset, java.util.function.Consumer<ViewBuilder<Void>> view) {
        Menu menu = Menus.simple("demo").home(v -> view.accept(v)).build();
        return render(menu, preset, Locale.ENGLISH);
    }

    private static Container render(Menu menu, Preset preset, Locale locale) {
        return menu.render(SimpleContexts.of("demo", "home", preset, locale)).join();
    }

    private static Container renderView(Menu menu, String view) {
        return menu.render(SimpleContexts.of("demo", view, BuiltinPresets.DEFAULT, Locale.ENGLISH))
                .join();
    }

    private static List<String> tenItems() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add("item " + i);
        }
        return items;
    }

    private static List<String> texts(Container container) {
        List<String> found = new ArrayList<>();
        for (Object child : container.getComponents()) {
            if (child instanceof TextDisplay display) {
                found.add(display.getContent());
            }
        }
        return found;
    }

    /** The buttons, including the ones nested in an action row. */
    private static List<Button> buttons(Container container) {
        List<Button> found = new ArrayList<>();
        for (Object child : container.getComponents()) {
            if (child instanceof ActionRow row) {
                row.getComponents().stream()
                        .filter(Button.class::isInstance)
                        .map(Button.class::cast)
                        .forEach(found::add);
            } else if (child instanceof Button button) {
                found.add(button);
            }
        }
        return found;
    }
}
