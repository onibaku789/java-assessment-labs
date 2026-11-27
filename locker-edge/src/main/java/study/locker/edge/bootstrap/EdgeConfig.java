package study.locker.edge.bootstrap;

import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import study.locker.common.Rpc;
import study.locker.edge.adapter.security.CallerQuota;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CallerQuota.Settings.class)
@Import({EdgeWebSecurity.class, GatewayOperationsConfiguration.class})
public class EdgeConfig {
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel reservationChannel(@Value("${locker.reservations:reservations:9090}") String target) {
        return Rpc.channel(target);
    }
}
