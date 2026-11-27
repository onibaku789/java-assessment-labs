package study.locker.reservation.bootstrap;

import org.springframework.vault.core.VaultOperations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import study.locker.common.*;
import study.locker.reservation.adapter.crypto.*;
import study.locker.reservation.application.*;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PickupProperties.class)
class ReservationConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PickupTransit secrets(VaultOperations vault) {
        return new PickupTransit(vault);
    }

    @Bean
    ReservationQueries queries(ReservationStore store, Directory directory) {
        return new ReservationQueries(store, directory);
    }

    @Bean
    PickupCodes pickupCodes(PickupTransit secrets, PickupProperties pickup) {
        return new SecurePickupCodes(secrets, pickup.ttl(), pickup.maxAttempts());
    }

    @Bean
    ReservationCommands businessCommands(ReservationStore store, Directory directory, PickupCodes codes,
                                         ReservationEvents events, Clock clock) {
        return new ReservationCommands(store, directory, codes, events, clock);
    }

}
