package study.locker.edge.bootstrap;

import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import study.locker.common.*;
import study.locker.edge.adapter.grpc.GrpcLockerOperations;
import study.locker.edge.adapter.observability.ObservedLockerOperations;
import study.locker.edge.adapter.security.*;
import study.locker.edge.application.LockerOperations;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Import({RpcClientConfiguration.class, ObservedLockerOperations.class, QuotaOperations.class})
public class GatewayOperationsConfiguration {
    @Bean
    RpcTarget rpcTarget(@Value("${locker.reservations.deadline:4s}") Duration deadline,
                        @Value("${locker.rpc.capacity:8}") int capacity) {
        return new RpcTarget("reservations", deadline, capacity);
    }

    @Bean
    LockerOperations gateway(ManagedChannel channel, RpcCalls calls) {
        return new GrpcLockerOperations(channel, calls, CurrentCaller::tokenValue);
    }
}
