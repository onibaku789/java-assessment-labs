package study.locker.edge.bootstrap;

import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import study.locker.common.Rpc;
import study.locker.edge.adapter.grpc.GrpcLockerOperations;
import study.locker.edge.adapter.observability.ObservedLockerOperations;
import study.locker.edge.application.LockerOperations;
import study.locker.security.LockerJwt;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@Import(LockerJwt.class)
public class EdgeConfig {
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel reservationChannel(
            @Value("${locker.reservations:reservations:9090}") String target) {
        return Rpc.channel(target);
    }

    @Bean
    public LockerOperations gateway(
            ManagedChannel channel,
            MeterRegistry metrics,
            @Value("${locker.protocol:gateway}") String protocol,
            @Value("${locker.reservations.deadline:4s}") Duration deadline) {
        return new ObservedLockerOperations(
                new GrpcLockerOperations(channel, deadline), metrics, protocol);
    }

    @Bean
    SecurityFilterChain webSecurity(HttpSecurity http) {
        return http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health/**",
                                                "/openapi.yaml",
                                                "/actuator/prometheus")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value(
                            "${locker.cors.origins:http://localhost:18081,http://localhost:18082,http://localhost:18083}")
                    List<String> origins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Idempotency-Key", "SOAPAction"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
