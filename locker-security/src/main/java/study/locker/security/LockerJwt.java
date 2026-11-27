package study.locker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
public class LockerJwt {
    @Bean
    public JwtDecoder lockerDecoder(
            @Value("${locker.public-key:/run/locker/public.pem}") String path,
            @Value("${locker.jwt.issuer:urn:locker:lab}") String issuer,
            @Value("${locker.jwt.audience:locker-api}") String audience)
            throws Exception {
        RSAPublicKey key;
        try (var stream = Files.newInputStream(Path.of(path))) {
            key = RsaKeyConverters.x509().convert(stream);
        }
        var decoder = NimbusJwtDecoder.withPublicKey(key).build();
        OAuth2TokenValidator<Jwt> claims =
                jwt ->
                        jwt.getExpiresAt() != null
                                        && jwt.getSubject() != null
                                        && !jwt.getSubject().isBlank()
                                        && jwt.getAudience() != null
                                        && jwt.getAudience().contains(audience)
                                ? OAuth2TokenValidatorResult.success()
                                : OAuth2TokenValidatorResult.failure(
                                        new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer), claims));
        return decoder;
    }
}
