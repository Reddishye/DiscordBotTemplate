/**
 * Presentation layer: the builder that assembles a JDA container and the component
 * implementations that fill it. Rendering here is a pure function of a context and
 * performs no I/O.
 *
 * <p>JDA splits components across three disjoint hierarchies, so this package
 * has one interface per hierarchy: {@link es.redactado.menu.view.MenuComponent}
 * for container children, {@link es.redactado.menu.view.RowItem} for action-row
 * children, and {@link es.redactado.menu.view.Accessory} for section accessories.
 * Buttons and thumbnails are not container children and must go through
 * {@link es.redactado.menu.view.Row} or a JDA section respectively.
 */
package es.redactado.menu.view;
