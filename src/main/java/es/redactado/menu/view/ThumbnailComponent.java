package es.redactado.menu.view;

import es.redactado.menu.api.MenuContext;
import net.dv8tion.jda.api.components.section.SectionAccessoryComponent;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;

/** An image shown beside a JDA section's text. */
public final class ThumbnailComponent implements Accessory {

    private final String url;

    private ThumbnailComponent(String url) {
        this.url = url;
    }

    /**
     * Creates a thumbnail from a remote image URL.
     *
     * @param url the image URL
     * @return the thumbnail accessory
     */
    public static ThumbnailComponent of(String url) {
        return new ThumbnailComponent(url);
    }

    @Override
    public SectionAccessoryComponent render(MenuContext ctx) {
        return Thumbnail.fromUrl(url);
    }
}
