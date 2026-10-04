/**
 * The menu framework: renderable menus, components and presets, with no bot in sight.
 *
 * <p>A menu here is a container of components plus a table of actions. Everything around it
 * exists to make that pair safe to run at the same time from a hundred messages: a router
 * that decodes component ids and claims messages, a session per message, an executor that
 * keeps blocking work off JDA's threads, and one edit path so a message is never written by
 * two things at once.
 *
 * <p>The packages divide along those lines. {@code api} is what a caller programs against,
 * {@code core} is how an interaction reaches a menu, {@code view} is the component set,
 * {@code preset} is how a menu looks, {@code simple} is a declarative way to write a menu
 * that still produces an ordinary one, and {@code examples} is code to read.
 *
 * <p><strong>The package must not import {@code es.redactado.service}.</strong> It receives
 * {@link java.util.concurrent.Executor} instances and nothing else, so the same code runs
 * under the template's pools, under a test's deterministic executor, or with no wiring at
 * all. {@code MenuDependencyTest} enforces it.
 */
package es.redactado.menu;
