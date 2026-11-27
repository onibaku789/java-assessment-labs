package study.locker.edge.bootstrap;

import io.grpc.ManagedChannel;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

import study.locker.common.Rpc;
import study.locker.common.RpcGuard;
import study.locker.edge.adapter.grpc.GrpcLockerOperations;
import study.locker.edge.adapter.observability.ObservedLockerOperations;
import study.locker.edge.adapter.security.*;
import study.locker.edge.application.LockerOperations;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RpcGuard.Settings.class, CallerQuota.Settings.class})
@Import(EdgeWebSecurity.class)
public class EdgeConfig {
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel reservationChannel(@Value("${locker.reservations:reservations:9090}") String target) {
        return Rpc.channel(target);
    }

    @Bean
    public LockerOperations gateway(ManagedChannel channel, MeterRegistry metrics,
                                    @Value("${locker.protocol:gateway}") String protocol,
                                    @Value("${locker.reservations.deadline:4s}") Duration deadline,
                                    RpcGuard.Settings settings, CallerQuota quota) {
        var guard = new RpcGuard("reservations", deadline, settings, 8, metrics);
        return new ObservedLockerOperations(new QuotaOperations(quota, new GrpcLockerOperations(channel, guard)),
                metrics, protocol);
    }

}
