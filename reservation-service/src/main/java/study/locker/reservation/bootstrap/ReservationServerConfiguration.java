package study.locker.reservation.bootstrap;

import org.springframework.context.annotation.*;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.Customizer;

import study.locker.security.LockerJwt;

@EnableMethodSecurity
@Import(LockerJwt.class)
@Configuration(proxyBeanMethods = false)
class ReservationServerConfiguration {
    @Bean
    @GlobalServerInterceptor
    AuthenticationProcessInterceptor reservationAuthentication(GrpcSecurity security) throws Exception {
        return security.authorizeRequests(requests -> requests.allRequests().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())).build();
    }
}
