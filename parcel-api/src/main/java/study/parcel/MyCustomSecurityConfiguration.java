package study.parcel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.security.oauth2.core.authorization.OAuth2AuthorizationManagers.hasScope;

@Configuration
@EnableWebSecurity
public class MyCustomSecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        return http
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/parcels/**").access(hasScope("parcel.read"))
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        var cors = new CorsConfiguration();
        cors.addAllowedOrigin("http://127.0.0.1:18082");
        cors.addAllowedMethod(HttpMethod.GET);
        cors.addAllowedHeader("Authorization");

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/parcels/**", cors);
        return source;
    }

    @Bean
    OAuth2TokenValidator<Jwt> parcelIssuerValidator() {
        return new JwtIssuerValidator("urn:parcel:study");
    }

    @Bean
    JwtTimestampValidator parcelTimestamps() {
        var validator = new JwtTimestampValidator();
        validator.setAllowEmptyExpiryClaim(false);
        return validator;
    }
}