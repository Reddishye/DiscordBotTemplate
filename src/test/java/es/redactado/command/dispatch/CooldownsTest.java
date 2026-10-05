package es.redactado.command.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CooldownsTest {

    @Test
    void zeroMeansOff() {
        Cooldowns cooldowns = new Cooldowns();

        assertThat(cooldowns.tryAcquire("ping", 1, Duration.ZERO, 1_000)).isEmpty();
        assertThat(cooldowns.tryAcquire("ping", 1, Duration.ZERO, 1_000)).isEmpty();
    }

    @Test
    void secondCallWaits() {
        Cooldowns cooldowns = new Cooldowns();

        assertThat(cooldowns.tryAcquire("ping", 1, Duration.ofSeconds(10), 1_000)).isEmpty();
        assertThat(cooldowns.tryAcquire("ping", 1, Duration.ofSeconds(10), 1_500))
                .contains(Duration.ofMillis(9_500));
        assertThat(cooldowns.tryAcquire("ping", 2, Duration.ofSeconds(10), 1_500)).isEmpty();
        assertThat(cooldowns.tryAcquire("ping", 1, Duration.ofSeconds(10), 11_000)).isEmpty();
    }
}
