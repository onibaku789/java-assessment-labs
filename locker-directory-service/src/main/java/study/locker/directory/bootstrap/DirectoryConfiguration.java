package study.locker.directory.bootstrap;

import io.grpc.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

import study.locker.common.Rpc;
import study.locker.directory.adapter.grpc.DirectoryEndpoint;
import study.locker.directory.application.LockerDirectory;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
class DirectoryConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    Server directoryServer(
            LockerDirectory.Catalog catalog, @Value("${locker.grpc.port:9090}") int port)
            throws IOException {
        return Rpc.serve(port, new DirectoryEndpoint(new LockerDirectory(catalog)));
    }
}
