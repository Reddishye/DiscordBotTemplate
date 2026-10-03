package es.redactado.menu.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryPresetPreferencesTest {

    private final InMemoryPresetPreferences preferences = new InMemoryPresetPreferences();

    @Test
    @DisplayName("an unknown guild has no preference")
    void guildDefaultsToEmpty() {
        assertThat(preferences.guildPreset(1L).join()).isEmpty();
    }

    @Test
    @DisplayName("an unknown user has no preference")
    void userDefaultsToEmpty() {
        assertThat(preferences.userPreset(1L).join()).isEmpty();
    }

    @Test
    @DisplayName("setting a guild preference is read back")
    void setGuild() {
        preferences.setGuild(1L, "minimal");

        assertThat(preferences.guildPreset(1L).join()).contains("minimal");
    }

    @Test
    @DisplayName("setting a guild preference again replaces it")
    void setGuildReplaces() {
        preferences.setGuild(1L, "minimal");
        preferences.setGuild(1L, "midnight");

        assertThat(preferences.guildPreset(1L).join()).contains("midnight");
    }

    @Test
    @DisplayName("clearing a guild preference leaves it unset")
    void clearGuild() {
        preferences.setGuild(1L, "minimal");
        preferences.clearGuild(1L);

        assertThat(preferences.guildPreset(1L).join()).isEmpty();
    }

    @Test
    @DisplayName("clearing a guild that was never set is harmless")
    void clearUnknownGuild() {
        preferences.clearGuild(99L);

        assertThat(preferences.guildPreset(99L).join()).isEmpty();
    }

    @Test
    @DisplayName("setting a user preference is read back and cleared")
    void setAndClearUser() {
        preferences.setUser(2L, "vibrant");
        assertThat(preferences.userPreset(2L).join()).contains("vibrant");

        preferences.clearUser(2L);
        assertThat(preferences.userPreset(2L).join()).isEmpty();
    }

    @Test
    @DisplayName("guild and user preferences are kept apart")
    void guildAndUserAreIndependent() {
        preferences.setGuild(1L, "minimal");
        preferences.setUser(1L, "midnight");

        assertThat(preferences.guildPreset(1L).join()).contains("minimal");
        assertThat(preferences.userPreset(1L).join()).contains("midnight");
    }

    @Test
    @DisplayName("a null preset name is rejected")
    void nullNameRejected() {
        assertThatThrownBy(() -> preferences.setGuild(1L, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> preferences.setUser(1L, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("lookups are already completed, so resolution never waits")
    void lookupsAreCompleted() {
        preferences.setGuild(1L, "minimal");

        assertThat(preferences.guildPreset(1L)).isCompleted();
        assertThat(preferences.userPreset(1L)).isCompleted();
    }
}
