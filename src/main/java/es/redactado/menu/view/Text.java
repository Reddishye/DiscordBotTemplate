package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

public class Text implements MenuComponent {
    private final String content;

    private Text(String content) {
        this.content = content;
    }

    public static Text of(String content) {
        return new Text(content);
    }

    public static Text title(String content) {
        return new Text("### " + content);
    }

    public static Text subtitle(String content) {
        return new Text("## " + content);
    }

    public static Text small(String content) {
        return new Text("-# " + content);
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        return List.of(TextDisplay.of(content));
    }
}
