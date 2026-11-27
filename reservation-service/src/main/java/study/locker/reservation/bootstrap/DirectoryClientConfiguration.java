package study.locker.reservation.bootstrap;

import io.grpc.ManagedChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import study.locker.common.*;
import study.locker.reservation.adapter.grpcclient.GrpcDirectory;
import study.locker.reservation.application.Directory;
import study.locker.wire.DirectoryGrpc;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@Import(RpcClientConfiguration.class)
class DirectoryClientConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel directoryChannel(@Value("${locker.directory:directory:9090}") String target) {
        return Rpc.channel(target);
    }

    @Bean
    RpcTarget rpcTarget(@Value("${locker.directory.deadline}") Duration deadline,
                        @Value("${locker.rpc.capacity:4}") int capacity) {
        return new RpcTarget("directory", deadline, capacity);
    }

    @Bean
    Directory directory(ManagedChannel channel, RpcCalls calls) {
        return new GrpcDirectory(DirectoryGrpc.newBlockingStub(channel), calls);
    }
}
