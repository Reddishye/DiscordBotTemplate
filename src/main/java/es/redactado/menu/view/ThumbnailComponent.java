package es.redactado.menu.view;

import es.redactado.menu.api.Component;
import es.redactado.menu.api.Context;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;

public class ThumbnailComponent implements Component {
    private final String url;

    private ThumbnailComponent(String url) {
        this.url = url;
    }

    public static ThumbnailComponent of(String url) {
        return new ThumbnailComponent(url);
    }

    @Override
    public List<ContainerChildComponent> render(Context ctx) {
        return List.of(
                (ContainerChildComponent)
                        net.dv8tion.jda.api.components.thumbnail.Thumbnail.fromUrl(url));
    }
}
