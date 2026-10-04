package es.redactado.menu.examples;

import es.redactado.menu.examples.ProfileExampleMenu.Profile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A profile service that blocks, on purpose, for a fixed number of milliseconds.
 *
 * <p>Every method here is a blocking call, which is what makes this example worth reading:
 * a real repository call looks exactly like this, and the whole point of
 * {@link ProfileExampleMenu} is that none of it runs on a JDA event thread.
 *
 * <p>The delay is simulated with {@code Thread.sleep} rather than a scheduler, because the
 * point is that the *caller* blocks: a scheduler would let the call return immediately and
 * prove nothing about where the blocking happens.
 *
 * <p>Counts every call, so a test can show that a cache is doing its job. That is the one
 * assertion a reader should take from this file: fifty renders of one profile must cost one
 * {@link #loadCalls()}, not fifty.
 */
public final class FakeProfileService {

    private final long latencyMillis;
    private final Map<Long, Profile> profiles = new ConcurrentHashMap<>();
    private final AtomicInteger loads = new AtomicInteger();
    private final AtomicInteger birthWrites = new AtomicInteger();
    private final AtomicInteger linkWrites = new AtomicInteger();
    private final AtomicInteger removals = new AtomicInteger();

    /**
     * @param latencyMillis how long every call pretends to take; zero makes it instant
     */
    public FakeProfileService(long latencyMillis) {
        this.latencyMillis = latencyMillis;
    }

    /**
     * Loads a profile, creating one the first time a key is asked for.
     *
     * @param userId the profile's owner
     * @return the profile
     */
    public Profile loadBlocking(long userId) {
        delay();
        loads.incrementAndGet();
        return profiles.computeIfAbsent(userId, FakeProfileService::initialProfile);
    }

    /**
     * Stores a birth date.
     *
     * @param userId the profile's owner
     * @param birthDate the date, as the user typed it
     * @return the stored profile
     */
    public Profile saveBirthBlocking(long userId, String birthDate) {
        delay();
        birthWrites.incrementAndGet();
        Profile current = require(userId);
        Profile updated = new Profile(current.id(), birthDate, current.roles(), current.links());
        profiles.put(userId, updated);
        return updated;
    }

    /**
     * Adds a link.
     *
     * @param userId the profile's owner
     * @param label what the link is called
     * @param url where it goes
     * @return the stored profile
     */
    public Profile addLinkBlocking(long userId, String label, String url) {
        delay();
        linkWrites.incrementAndGet();
        Profile current = require(userId);
        List<Profile.Link> links = new ArrayList<>(current.links());
        links.add(new Profile.Link(label, url));
        Profile updated =
                new Profile(current.id(), current.birthDate(), current.roles(), List.copyOf(links));
        profiles.put(userId, updated);
        return updated;
    }

    /**
     * Removes a profile.
     *
     * @param userId the profile's owner
     * @return whether anything was there to remove
     */
    public boolean removeBlocking(long userId) {
        delay();
        removals.incrementAndGet();
        return profiles.remove(userId) != null;
    }

    /** How many times a profile was loaded, which is how the cache is judged. */
    public int loadCalls() {
        return loads.get();
    }

    /** How many birth dates were written. */
    public int birthWrites() {
        return birthWrites.get();
    }

    /** How many links were added. */
    public int linkWrites() {
        return linkWrites.get();
    }

    /** How many profiles were removed. */
    public int removals() {
        return removals.get();
    }

    /** The stored profile for a key, without loading it. */
    public Profile stored(long userId) {
        return profiles.get(userId);
    }

    private Profile require(long userId) {
        Profile current = profiles.get(userId);
        if (current == null) {
            throw new IllegalStateException("No profile for " + userId);
        }
        return current;
    }

    private void delay() {
        if (latencyMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pretending to be slow", e);
        }
    }

    /**
     * The profile a user starts with: enough roles and links for both pagers to have a second
     * page, which is the only way a pager's arrows are exercised by reading the view.
     *
     * <p>The role ids are fixed rather than derived from the user, because a role belongs to a
     * guild rather than to a person and a test should be able to write {@code 1001} and mean
     * the same role wherever it appears.
     *
     * <p>The role ids are fixed rather than derived from the user, because a role belongs to
     * a guild rather than to a person and a test should be able to write {@code 1001} and
     * mean the same role everywhere.
     *
     * @param userId the owner
     * @return the starting profile
     */
    public static Profile initialProfile(long userId) {
        List<Profile.Role> roles =
                List.of(
                        new Profile.Role(1001L, "Owner", 8, "Everything"),
                        new Profile.Role(2001L, "Moderator", 4, "Can mute"),
                        new Profile.Role(3001L, "Helper", 2, "Can timeout"),
                        new Profile.Role(4001L, "Member", 1, "Can talk"),
                        new Profile.Role(5001L, "Reader", 0, "Can look"),
                        new Profile.Role(6001L, "Guest", 0, "Barely there"));

        return new Profile(
                userId,
                "1990-01-01",
                roles,
                List.of(
                        new Profile.Link("Site", "https://example.com"),
                        new Profile.Link("Docs", "https://example.com/docs"),
                        new Profile.Link("Support", "https://example.com/support"),
                        new Profile.Link("Status", "https://example.com/status"),
                        new Profile.Link("Privacy", "https://example.com/privacy"),
                        new Profile.Link("Terms", "https://example.com/terms")));
    }
}
