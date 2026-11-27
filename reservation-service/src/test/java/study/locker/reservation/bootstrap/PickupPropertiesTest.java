package study.locker.reservation.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PickupPropertiesTest {
    @EnableConfigurationProperties(PickupProperties.class)
    static class Binding {}

    @ParameterizedTest
    @CsvSource({"0s, 5, ttl", "-1m, 5, ttl", "15m, 0, maxAttempts", "15m, -1, maxAttempts"})
    void refusesInvalidPolicyOnStartup(String ttl, int maxAttempts, String field) {
        new ApplicationContextRunner()
                .withUserConfiguration(Binding.class)
                .withPropertyValues(
                        "locker.pickup.ttl=" + ttl, "locker.pickup.max-attempts=" + maxAttempts)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .hasMessageContaining(field);
                        });
    }
}
