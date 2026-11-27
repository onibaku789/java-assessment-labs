package study.locker.reservation.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import study.locker.common.*;
import study.locker.reservation.adapter.crypto.*;
import study.locker.reservation.application.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PickupProperties.class)
class ReservationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PickupSecrets secrets(@Value("${locker.secret:/run/locker/pickup.key}") String path) throws IOException {
        return new PickupSecrets(Files.readString(Path.of(path)));
    }

    @Bean
    ReservationQueries queries(ReservationStore store, Directory directory) {
        return new ReservationQueries(store, directory);
    }

    @Bean
    PickupCodes pickupCodes(PickupSecrets secrets, PickupProperties pickup) {
        return new SecurePickupCodes(secrets, pickup.ttl(), pickup.maxAttempts());
    }

    @Bean
    ReservationCommands businessCommands(ReservationStore store, Directory directory, PickupCodes codes,
                                         ReservationEvents events, Clock clock) {
        return new ReservationCommands(store, directory, codes, events, clock);
    }

}
