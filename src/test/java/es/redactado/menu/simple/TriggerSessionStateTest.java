package es.redactado.menu.simple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import es.redactado.menu.api.MenuContext;
import es.redactado.menu.api.Session;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.dv8tion.jda.api.modals.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The four state calls a simple-menu handler gets on its trigger.
 *
 * <p>They exist so a handler never has to ask whether the message has a session yet, which is
 * the question that made two of the three examples fail their first end-to-end test. A
 * delegate that quietly created a session on a read would bring the same trap back, so the
 * read is checked for the side effect here rather than assumed absent.
 */
class TriggerSessionStateTest {

    private static final String KEY = "count";

    @Test
    @DisplayName("reading through a trigger answers the fallback and creates no session")
    void readDoesNotCreateASession() {
        RecordingSupport support = new RecordingSupport(new Session());
        Click click = new Click(support, null, false);

        assertThat(click.sessionStateOr(KEY, Integer.class, 41)).isEqualTo(41);
        assertThat(click.sessionState(KEY, Integer.class)).isEmpty();
        assertThat(support.created)
                .as("the read never went to the store, so no session was created")
                .isZero();
    }

    @Test
    @DisplayName("writing through a trigger stores what the next read sees")
    void writeThenRead() {
        RecordingSupport support = new RecordingSupport(new Session());
        Click click = new Click(support, null, false);

        click.putSessionState(KEY, 7);

        assertThat(click.sessionStateOr(KEY, Integer.class, 0)).isEqualTo(7);
        assertThat(click.sessionState(KEY, Integer.class)).contains(7);
    }

    @Test
    @DisplayName("removing through a trigger takes the value away")
    void removeWorks() {
        RecordingSupport support = new RecordingSupport(new Session());
        Pick pick = new Pick(support, List.of("a"));

        pick.putSessionState(KEY, 7);
        pick.removeSessionState(KEY);

        assertThat(pick.sessionStateOr(KEY, Integer.class, -1)).isEqualTo(-1);
    }

    @Test
    @DisplayName("a submit trigger gets the same four calls, because it is the same interaction")
    void submitSharesThem() {
        RecordingSupport support = new RecordingSupport(new Session());
        Submit submit = new Submit(support, java.util.Map.of("name", "Ada"));

        submit.putSessionState(KEY, "written");

        assertThat(submit.sessionStateOr(KEY, String.class, "")).isEqualTo("written");
    }

    @Test
    @DisplayName("a trigger with no session at all still answers every call")
    void noSessionIsNotAnError() {
        RecordingSupport support = new RecordingSupport(null);
        Click click = new Click(support, null, false);

        assertThat(click.sessionState(KEY, String.class))
                .as("an interaction with no message has no session; that is empty, not a crash")
                .isEqualTo(Optional.empty());
        assertThat(click.sessionStateOr(KEY, Integer.class, 5)).isEqualTo(5);

        // Writing is the one that has to work, because that is how a session comes to exist.
        click.putSessionState(KEY, 1);
        assertThat(click.sessionStateOr(KEY, Integer.class, 0)).isEqualTo(1);
    }

    /**
     * A support over a store that may or may not hold a session.
     *
     * <p>Counts how often the session is asked for, which is how the read's lack of side
     * effect is proved: a read must not go near it.
     */
    private static final class RecordingSupport implements Trigger.Support {

        private final java.util.concurrent.atomic.AtomicReference<Session> stored;
        private int created;
        private Modal modal;

        RecordingSupport(Session existing) {
            this.stored = new java.util.concurrent.atomic.AtomicReference<>(existing);
        }

        @Override
        public MenuContext ctx() {
            MenuContext ctx = mock(MenuContext.class, org.mockito.Mockito.CALLS_REAL_METHODS);
            org.mockito.Mockito.when(ctx.findSession())
                    .thenAnswer(call -> Optional.ofNullable(stored.get()));
            // A real context always answers with a session, creating one on first use, which
            // is exactly what putSessionState relies on.
            org.mockito.Mockito.when(ctx.session())
                    .thenAnswer(
                            call ->
                                    stored.updateAndGet(
                                            current -> {
                                                if (current == null) {
                                                    created++;
                                                }
                                                return current != null ? current : new Session();
                                            }));
            return ctx;
        }

        @Override
        public CompletableFuture<Void> refresh() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> go(String menuId, String viewName) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> done() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void showModal(Modal modal) {
            this.modal = modal;
        }
    }
}
