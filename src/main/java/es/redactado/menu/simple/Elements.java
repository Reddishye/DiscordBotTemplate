package es.redactado.menu.simple;

import es.redactado.menu.api.MenuComponent;
import es.redactado.menu.api.Msg;
import es.redactado.menu.view.Divider;
import es.redactado.menu.view.Field;
import es.redactado.menu.view.Header;
import es.redactado.menu.view.Pager;
import es.redactado.menu.view.Row;
import es.redactado.menu.view.Text;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;

/**
 * The content elements of the DSL: everything a view shows rather than does.
 *
 * <p>One file of records because they are all the same shape, small, immutable, and only
 * meaningful next to each other.
 *
 * <p>Every record resolves its text when it renders, not when it is declared. That is what
 * lets one declared menu render in two languages.
 *
 * @param <M> the model type of the menu the view belongs to
 */
final class Elements {

    private Elements() {}

    /** A title, with an optional line under it that the preset may drop. */
    record HeaderElement<M>(Msg title, Msg subtitle) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            Header header = Header.of(title.get(scope.ctx()));
            if (subtitle != null) {
                return header.subtitle(subtitle.get(scope.ctx()));
            }
            return header;
        }
    }

    /**
     * A line of text.
     *
     * <p>Held as a function of the scope rather than as a {@link Msg}, so the same element
     * can carry either a message that only needs the context or one that needs the model
     * too, without a second shape.
     */
    record TextElement<M>(Function<Scope<M>, String> text) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            return Text.of(text.apply(scope));
        }
    }

    /** A labelled value, side by side with any other field in the container. */
    record FieldElement<M>(Msg label, Function<Scope<M>, String> value) implements Element<M> {

        @Override
        public MenuComponent render(Scope<M> scope) {
            return Field.of(label.get(scope.ctx()), value.apply(scope));
        }
    }

    /** A horizontal rule, or the blank line a preset asks for instead of one. */
    record DividerElement<M>(boolean line) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            return line ? Divider.line() : Divider.space();
        }
    }

    /** Text with an image beside it, which is what a JDA section is for. */
    record SectionElement<M>(Msg text, String thumbnailUrl) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            return SimpleMenu.wrap(
                    Section.of(
                            Thumbnail.fromUrl(thumbnailUrl),
                            TextDisplay.of(text.get(scope.ctx()))));
        }
    }

    /**
     * A paged list of text items.
     *
     * @param id identifies the pager, and its paging state, within the session
     * @param items the whole list, for this render
     * @param pageSize how many items one page shows
     * @param itemText turns an item into the line that shows it
     */
    record ListElement<M, T>(
            String id,
            Function<Scope<M>, List<T>> items,
            int pageSize,
            Function<T, String> itemText)
            implements Element<M> {

        @Override
        public MenuComponent render(Scope<M> scope) {
            return Pager.of(
                    id, items.apply(scope), pageSize, item -> Text.of(itemText.apply(item)));
        }
    }

    /**
     * A component the DSL has no word for.
     *
     * <p>Held as a supplier rather than a component so that the constant form does not build
     * one shared, already-resolved component for every interaction, and the escape hatch
     * still gets a fresh one each time.
     */
    record CustomElement<M>(Supplier<MenuComponent> component) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            return component.get();
        }
    }

    /** A row of buttons or a link. */
    record RowElement<M>(RowBuilder<M> row) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            return row.render(scope.ctx());
        }
    }

    /**
     * A select menu, which gets a row of its own because a row holds either buttons or one
     * select and never both.
     */
    record SelectElement<M>(String action, Msg placeholder, SelectSpec spec) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            es.redactado.menu.view.SelectMenu select =
                    es.redactado.menu.view.SelectMenu.of(action, placeholder.get(scope.ctx()));
            for (SelectSpec.Option option : spec.options()) {
                select =
                        option.description() == null
                                ? select.option(option.value(), option.label())
                                : select.option(
                                        option.value(), option.label(), option.description());
            }
            if (!spec.selected().isEmpty()) {
                select = select.selected(spec.selected().toArray(new String[0]));
            }
            if (spec.max() > 0) {
                select = select.range(spec.min(), spec.max());
            }
            return Row.of(select);
        }
    }

    /** A component built from the scope, for the escape hatch that needs the model. */
    record ScopedElement<M>(Function<Scope<M>, MenuComponent> component) implements Element<M> {
        @Override
        public MenuComponent render(Scope<M> scope) {
            return component.apply(scope);
        }
    }
}
