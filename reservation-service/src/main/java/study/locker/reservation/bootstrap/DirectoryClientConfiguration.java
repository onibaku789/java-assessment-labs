package study.locker.reservation.bootstrap;

import io.grpc.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import study.locker.common.*;
import study.locker.reservation.adapter.grpcclient.GrpcDirectory;
import study.locker.reservation.application.*;
import study.locker.wire.DirectoryGrpc;

import java.time.Duration;

@EnableConfigurationProperties(RpcGuard.Settings.class)
@Configuration(proxyBeanMethods = false)
class DirectoryClientConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel directoryChannel(@Value("${locker.directory:directory:9090}") String target) {
        return Rpc.channel(target);
    }

    @Bean
    Directory directory(ManagedChannel channel, @Value("${locker.directory.deadline}") Duration deadline,
                        RpcGuard.Settings settings, MeterRegistry metrics) {
        return new GrpcDirectory(DirectoryGrpc.newBlockingStub(channel),
                new RpcGuard("directory", deadline, settings, 4, metrics));
    }
}
