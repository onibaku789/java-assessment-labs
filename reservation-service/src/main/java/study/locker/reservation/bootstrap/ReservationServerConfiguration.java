package study.locker.reservation.bootstrap;

import io.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import study.locker.common.*;
import study.locker.reservation.adapter.grpcserver.ReservationEndpoint;
import study.locker.reservation.application.*;
import study.locker.security.*;

import java.io.IOException;

@EnableMethodSecurity
@Import(LockerJwt.class)
@Configuration(proxyBeanMethods = false)
class ReservationServerConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    Server reservationServer(ReservationAccess access, PickupSecrets secrets, JwtDecoder jwt,
                             @Value("${locker.grpc.port:9090}") int port) throws IOException {
        return Rpc.serve(port, new ReservationEndpoint(access, secrets), new RpcIdentity(jwt));
    }
}
