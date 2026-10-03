package es.redactado.menu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static NavEntry entry(String menuId, String action) {
        return new NavEntry(menuId, action, List.of());
    }

    @Test
    @DisplayName("push and pop come back in reverse order")
    void pushPopOrder() {
        Session session = new Session();

        session.push(entry("a", "one"));
        session.push(entry("b", "two"));
        session.push(entry("c", "three"));

        assertThat(session.depth()).isEqualTo(3);
        assertThat(session.pop()).contains(entry("c", "three"));
        assertThat(session.pop()).contains(entry("b", "two"));
        assertThat(session.pop()).contains(entry("a", "one"));
        assertThat(session.pop()).isEmpty();
        assertThat(session.depth()).isZero();
    }

    @Test
    @DisplayName("peek shows the newest entry without removing it")
    void peekDoesNotRemove() {
        Session session = new Session();
        session.push(entry("a", "one"));
        session.push(entry("b", "two"));

        assertThat(session.peek()).contains(entry("b", "two"));
        assertThat(session.depth()).isEqualTo(2);
        assertThat(session.peek()).contains(entry("b", "two"));
    }

    @Test
    @DisplayName("peek and pop are empty on a fresh session")
    void emptySession() {
        Session session = new Session();

        assertThat(session.peek()).isEmpty();
        assertThat(session.pop()).isEmpty();
        assertThat(session.depth()).isZero();
    }

    @Test
    @DisplayName("depth is capped, dropping the oldest and keeping the newest twenty")
    void depthIsCapped() {
        Session session = new Session();
        for (int i = 0; i < 25; i++) {
            session.push(entry("m", "view" + i));
        }

        assertThat(session.depth()).isEqualTo(Session.MAX_DEPTH);
        assertThat(session.peek()).contains(entry("m", "view24"));
        assertThat(session.pop()).contains(entry("m", "view24"));
        assertThat(session.pop()).contains(entry("m", "view23"));
        // 25 were pushed and the five oldest discarded, so view5 is now the bottom.
        assertThat(session.pop()).contains(entry("m", "view22"));
        assertThat(session.depth()).isEqualTo(Session.MAX_DEPTH - 3);
    }

    @Test
    @DisplayName("clearStack empties the history but keeps state")
    void clearStack() {
        Session session = new Session();
        session.push(entry("a", "one"));
        session.putState("page", 3);

        session.clearStack();

        assertThat(session.depth()).isZero();
        assertThat(session.state("page", Integer.class)).contains(3);
    }

    @Test
    @DisplayName("typed state returns the value, empty when absent")
    void typedState() {
        Session session = new Session();

        assertThat(session.state("page", Integer.class)).isEmpty();

        session.putState("page", 3);

        assertThat(session.state("page", Integer.class)).contains(3);
    }

    @Test
    @DisplayName("typed state is empty when the value is another type")
    void typedStateWrongType() {
        Session session = new Session();
        session.putState("page", "three");

        assertThat(session.state("page", Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("removeState drops a value")
    void removeState() {
        Session session = new Session();
        session.putState("page", 3);

        session.removeState("page");

        assertThat(session.state("page", Integer.class)).isEmpty();
    }

    @Test
    @DisplayName("concurrent pushes never exceed the cap and never throw")
    void concurrentPushes() throws InterruptedException {
        Session session = new Session();
        int threads = 8;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            int id = t;
            pool.execute(
                    () -> {
                        try {
                            start.await();
                            for (int i = 0; i < perThread; i++) {
                                session.push(entry("m", id + ":" + i));
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        assertThat(done.await(AWAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(session.depth()).isEqualTo(Math.min(threads * perThread, Session.MAX_DEPTH));
    }

    @Test
    @DisplayName("a null state value is rejected")
    void rejectsNullState() {
        Session session = new Session();

        assertThatThrownBy(() -> session.putState("k", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("NavEntry rejects a null or empty menu id and copies its params")
    void navEntryValidation() {
        assertThatThrownBy(() -> new NavEntry(null, "home", List.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NavEntry("", "home", List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        List<String> mutable = new java.util.ArrayList<>(List.of("a"));
        NavEntry navEntry = new NavEntry("m", "act", mutable);
        mutable.add("b");

        assertThat(navEntry.params()).containsExactly("a");
        assertThatThrownBy(() -> navEntry.params().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final long AWAIT_SECONDS = 5;
}
