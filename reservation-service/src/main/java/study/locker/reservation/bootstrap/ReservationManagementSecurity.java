package study.locker.reservation.bootstrap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ReservationManagementSecurity {
    @Bean
    SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(
                auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus")
                        .permitAll().anyRequest().denyAll()).build();
    }
}
