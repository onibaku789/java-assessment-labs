package study.locker.reservation.integration;

import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.server.lifecycle.GrpcServerStartedEvent;
import study.locker.wire.ReservationsGrpc;

@TestConfiguration(proxyBeanMethods = false)
class ReservationTestServer {
    private Server server;

    @EventListener
    void started(GrpcServerStartedEvent event) {
        server = event.getServer();
    }

    int port() {
        return server.getPort();
    }

    ServerServiceDefinition reservations() {
        return server.getServices().stream()
                .filter(service -> ReservationsGrpc.SERVICE_NAME.equals(service.getServiceDescriptor().getName()))
                .findFirst().orElseThrow();
    }
}
