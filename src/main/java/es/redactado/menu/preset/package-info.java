/**
 * Immutable descriptions of how a menu looks: colours, icons, spacing, header
 * treatment, and button roles.
 *
 * <p>A preset is data, not behaviour. It is read by the view layer at render time
 * and never mutated, so the same instance can be shared by every interaction
 * without synchronisation.
 *
 * <p>This package depends only on the JDK and JDA. It deliberately imports nothing
 * from {@code api}, {@code core}, or {@code view}, so a preset can be reasoned
 * about, serialised, and tested without the menu machinery.
 */
package es.redactado.menu.preset;
