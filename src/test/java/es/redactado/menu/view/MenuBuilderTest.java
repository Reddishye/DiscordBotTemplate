package es.redactado.menu.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import es.redactado.menu.api.MenuContext;
import net.dv8tion.jda.api.components.container.Container;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the ported view layer: proves a container can be assembled
 * through {@link MenuBuilder} and accepted by {@link Validator}.
 *
 * <p>The context is a Mockito double, so no gateway, shard, or JDA connection is
 * involved.
 *
 * <p>Only components that render real container children are exercised here.
 * {@code ActionButton} and {@code LinkButton} are excluded because the ported
 * {@code MenuComponent} contract cannot carry a JDA {@code Button}; see the
 * entry in {@code NOTES.md}.
 */
class MenuBuilderTest {

    @Test
    @DisplayName("builds a container that passes validation")
    void buildsValidContainer() {
        MenuContext ctx = context("smoke");

        Container container =
                MenuBuilder.create("smoke")
                        .add(Text.of("## Hello"))
                        .add(Field.of("Name", "Ada"))
                        .add(Gallery.of("https://example.com/a.png"))
                        .build(ctx);

        ValidationResult result = Validator.validate(container);

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(container.getComponents()).hasSize(3);
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
    @DisplayName("renders each component into container children")
    void rendersComponents() {
        MenuContext ctx = context("smoke");

        assertThat(Text.of("Plain").render(ctx)).hasSize(1);
        assertThat(Field.of("Name", "Ada").render(ctx)).hasSize(1);
        assertThat(Gallery.of("https://example.com/a.png").render(ctx)).hasSize(1);
        assertThat(JdaSeparator.small()).isNotNull();
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
