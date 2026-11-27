package study.locker.common;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerConfiguration;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
@EnableResilientMethods
@EnableConfigurationProperties(RpcSettings.class)
@Import({CircuitBreakerConfiguration.class, CircuitBreakerConfigurationProperties.class,
        RpcCalls.class, RpcAttempt.class, RpcMetrics.class, RpcRetry.class})
public class RpcClientConfiguration {
    @Bean
    CircuitBreaker rpcBreaker(CircuitBreakerRegistry registry, RpcTarget target, RpcSettings settings) {
        return registry.circuitBreaker(target.name(), settings.breakerConfig());
    }
}
