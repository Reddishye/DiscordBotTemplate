package es.redactado.menu.view;

import es.redactado.menu.api.Component;
import es.redactado.menu.api.Context;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class LinkButton implements Component {
    private final String url;
    private final String label;
    private final Emoji emoji;

    private LinkButton(String url, String label, Emoji emoji) {
        this.url = url;
        this.label = label;
        this.emoji = emoji;
    }

    public static LinkButton of(String url, String label) {
        return new LinkButton(url, label, null);
    }

    public LinkButton emoji(Emoji e) {
        return new LinkButton(url, label, e);
    }

    @Override
    public List<ContainerChildComponent> render(Context ctx) {
        var btn =
                label != null
                        ? net.dv8tion.jda.api.components.buttons.Button.link(url, label)
                        : net.dv8tion.jda.api.components.buttons.Button.link(url, "");
        if (emoji != null) btn = btn.withEmoji(emoji);
        return List.of((ContainerChildComponent) btn);
    }
}
