package study.locker.edge.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import study.locker.edge.adapter.security.*;
import study.locker.security.LockerJwt;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@Import(LockerJwt.class)
class EdgeWebSecurity {
    @Bean
    SecurityFilterChain webSecurity(HttpSecurity http) {
        return http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/actuator/health/**", "/openapi.yaml", "/actuator/prometheus")
                                .permitAll().anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())).build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${locker.cors.origins:http://localhost:18081,http://localhost:18082,http://localhost:18083}") List<String> origins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "SOAPAction"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean(destroyMethod = "close")
    CallerQuota callerQuota(CallerQuota.Settings settings, MeterRegistry metrics) {
        return new CallerQuota(settings,
                outcome -> metrics.counter("locker.quota.events", "outcome", outcome).increment());
    }

}
