package es.redactado.menu.view;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.MenuContext;
import java.util.List;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;

public class Gallery implements MenuComponent {
    private final List<String> imageUrls;

    private Gallery(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public static Gallery of(String... imageUrls) {
        if (imageUrls.length > Limits.MAX_MEDIA_GALLERY_ITEMS) {
            throw new IllegalArgumentException(
                    "MediaGallery max %d items, got %d"
                            .formatted(Limits.MAX_MEDIA_GALLERY_ITEMS, imageUrls.length));
        }
        return new Gallery(List.of(imageUrls));
    }

    @Override
    public List<ContainerChildComponent> render(MenuContext ctx) {
        var items = imageUrls.stream().map(MediaGalleryItem::fromUrl).toList();
        return List.of(MediaGallery.of(items));
    }
}
