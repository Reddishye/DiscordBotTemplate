package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.MenuContext;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the ported view layer: proves a container can be assembled
 * through {@link MenuBuilder} and accepted by {@link Validator}.
 *
 * <p>The context is a Mockito double, so no gateway, shard, or JDA connection is
 * involved.
 */
class MenuBuilderTest {

    private static final String IMAGE = "https://example.com/a.png";
    private static final String DOCS = "https://example.com/docs";

    @Test
    @DisplayName("builds a container that passes validation")
    void buildsValidContainer() {
        MenuContext ctx = context("smoke");

        Container container =
                MenuBuilder.create("smoke")
                        .add(Text.of("## Hello"))
                        .add(Field.of("Name", "Ada"))
                        .add(Gallery.of(IMAGE))
                        .add(Row.of(ActionButton.primary("edit", "Edit")))
                        .build(ctx);

        ValidationResult result = Validator.validate(container);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(container.getComponents()).hasSize(4);
        assertThat(container.isMessageCompatible()).isTrue();
    }

    @Test
    @DisplayName("verify accepts the container and returns it unchanged")
    void verifyAcceptsContainer() {
        MenuContext ctx = context("smoke");
        Container container = MenuBuilder.create("smoke").add(Text.of("Hello")).build(ctx);

        assertThatCode(() -> Validator.verify(container)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("applies accent color and spoiler flags")
    void appliesContainerFlags() {
        MenuContext ctx = context("smoke");

        Container container =
                MenuBuilder.create("smoke")
                        .add(Text.of("Hello"))
                        .accentColor(0x5865F2)
                        .spoiler(true)
                        .build(ctx);

        assertThat(container.getAccentColorRaw()).isEqualTo(0x5865F2);
        assertThat(container.isSpoiler()).isTrue();
    }

    @Test
    @DisplayName("renders each container component into container children")
    void rendersContainerComponents() {
        MenuContext ctx = context("smoke");

        assertThat(Text.of("Plain").render(ctx)).hasSize(1);
        assertThat(Field.of("Name", "Ada").render(ctx)).hasSize(1);
        assertThat(Gallery.of(IMAGE).render(ctx)).hasSize(1);
    }

    @Test
    @DisplayName("row places buttons and links with their ids and url intact")
    void rowRendersButtonsAndLinks() {
        MenuContext ctx = context("smoke");

        Container container =
                MenuBuilder.create("smoke")
                        .add(
                                Row.of(
                                        ActionButton.primary("edit", "Edit"),
                                        LinkButton.of(DOCS, "Docs")))
                        .build(ctx);

        ActionRow row = (ActionRow) container.getComponents().get(0);
        List<Button> buttons = row.getButtons();

        assertThat(buttons).hasSize(2);
        assertThat(buttons.get(0).getCustomId()).isEqualTo("menu:smoke:edit");
        assertThat(buttons.get(0).getLabel()).isEqualTo("Edit");
        assertThat(buttons.get(0).getUrl()).isNull();
        assertThat(buttons.get(1).getUrl()).isEqualTo(DOCS);
        assertThat(buttons.get(1).getLabel()).isEqualTo("Docs");
    }

    @Test
    @DisplayName("row items carry extra params and the disabled state")
    void rowRendersParamsAndDisabledState() {
        MenuContext ctx = context("smoke");

        Container container =
                MenuBuilder.create("smoke")
                        .add(
                                Row.of(
                                        ActionButton.secondary("open", "Open")
                                                .params("42", "abc")
                                                .disabled(true)))
                        .build(ctx);

        Button button = ((ActionRow) container.getComponents().get(0)).getButtons().getFirst();

        assertThat(button.getCustomId()).isEqualTo("menu:smoke:open:42:abc");
        assertThat(button.isDisabled()).isTrue();
    }

    @Test
    @DisplayName("section renders a thumbnail accessory beside its text")
    void sectionRendersThumbnailAccessory() {
        MenuContext ctx = context("smoke");
        SectionAccessoryComponent accessory = ThumbnailComponent.of(IMAGE).render(ctx);

        Section section = Section.of(accessory, TextDisplay.of("Body copy"));

        assertThat(accessory).isInstanceOf(Thumbnail.class);
        assertThat(section.getAccessory()).isInstanceOf(Thumbnail.class);
        assertThat(section.getContentComponents()).hasSize(1);
    }

    @Test
    @DisplayName("a full container of text, row, section and gallery validates")
    void fullContainerValidates() {
        MenuContext ctx = context("smoke");

        List<ContainerChildComponent> children =
                List.of(
                        TextDisplay.of("## Title"),
                        Row.of(ActionButton.primary("edit", "Edit"), LinkButton.of(DOCS, "Docs"))
                                .render(ctx)
                                .getFirst(),
                        Section.of(
                                ThumbnailComponent.of(IMAGE).render(ctx),
                                TextDisplay.of("Body copy")),
                        Gallery.of(IMAGE).render(ctx).getFirst());

        ValidationResult result = Validator.validate(Container.of(children));

        assertThat(result.isValid()).isTrue();
        assertThat(result.warnings()).isEmpty();
        assertThat(Container.of(children).isMessageCompatible()).isTrue();
    }

    @Test
    @DisplayName("row rejects more than five items")
    void rowRejectsTooManyItems() {
        RowItem[] six =
                new RowItem[] {
                    ActionButton.primary("a", "A"),
                    ActionButton.primary("b", "B"),
                    ActionButton.primary("c", "C"),
                    ActionButton.primary("d", "D"),
                    ActionButton.primary("e", "E"),
                    ActionButton.primary("f", "F")
                };

        assertThatThrownBy(() -> Row.of(six))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 5")
                .hasMessageContaining("got 6");
    }

    @Test
    @DisplayName("row rejects an empty item list")
    void rowRejectsNoItems() {
        assertThatThrownBy(() -> Row.of(new RowItem[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    @DisplayName("reports the menu id it was created with")
    void reportsMenuId() {
        assertThat(MenuBuilder.create("profile").menuId()).isEqualTo("profile");
    }

    private MenuContext context(String menuId) {
        MenuContext ctx = mock(MenuContext.class);
        when(ctx.menuId()).thenReturn(menuId);
        return ctx;
    }
}
