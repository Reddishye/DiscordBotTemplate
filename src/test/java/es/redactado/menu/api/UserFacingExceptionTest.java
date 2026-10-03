package es.redactado.menu.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserFacingExceptionTest {

    @Test
    @DisplayName("keeps the message it was given")
    void keepsMessage() {
        UserFacingException error = new UserFacingException("You need the admin role.");

        assertThat(error.getMessage()).isEqualTo("You need the admin role.");
    }

    @Test
    @DisplayName("is a MenuException, so a single catch covers menu failures")
    void isMenuException() {
        assertThat(new UserFacingException("Nope.")).isInstanceOf(MenuException.class);
    }

    @Test
    @DisplayName("propagates through a menu catch block")
    void caughtAsMenuException() {
        assertThatThrownBy(
                        () -> {
                            throw new UserFacingException("That profile is closed.");
                        })
                .isInstanceOf(MenuException.class)
                .hasMessage("That profile is closed.");
    }
}
