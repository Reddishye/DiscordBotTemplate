/**
 * Runnable examples that are compiled and tested but never registered.
 *
 * <p>Nothing in this package starts on its own. A bot wires an example up deliberately,
 * by adding it to its own startup code, which keeps example behaviour out of a production
 * bot that did not ask for it.
 *
 * <p>The examples read no database and call no network, so a test can render every view
 * of every one of them without a gateway, a container or a fixture.
 */
package es.redactado.menu.examples;
