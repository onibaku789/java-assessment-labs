package study.locker.soap.adapter.soap;

import jakarta.jws.WebService;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import study.locker.edge.application.*;
import study.locker.soap.wire.*;

@WebService(endpointInterface = "study.locker.soap.wire.LockerPortType", targetNamespace = "urn:lockers:v1", serviceName = "LockerService", portName = "LockerPort")
public class LockerSoap implements LockerPortType {
    private final LockerOperations gateway;

    public LockerSoap(LockerOperations gateway) {
        this.gateway = gateway;
    }

    private String actor() {
        return ((Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getTokenValue();
    }

    public Compartments available(SearchInput q) throws ProblemMessage {
        return SoapFaults.call(() -> {
            var response = new Compartments();
            for (var slot : gateway.available(actor(), q.getSite(), q.getSize())) {
                var item = new Compartment();
                item.setId(slot.id());
                item.setSite(slot.site());
                item.setSize(slot.size());
                response.getItems().add(item);
            }
            return response;
        });
    }

    public Booking get(KeyInput q) throws ProblemMessage {
        return SoapFaults.call(() -> map(gateway.get(actor(), q.getId())));
    }

    public Booking reserve(CommandInput q) throws ProblemMessage {
        return command(LockerOperations.Action.RESERVE, q);
    }

    public Booking cancel(CommandInput q) throws ProblemMessage {
        return command(LockerOperations.Action.CANCEL, q);
    }

    public Booking deposit(CommandInput q) throws ProblemMessage {
        return command(LockerOperations.Action.DEPOSIT, q);
    }

    public Booking reissue(CommandInput q) throws ProblemMessage {
        return command(LockerOperations.Action.REISSUE, q);
    }

    public Booking collect(CommandInput q) throws ProblemMessage {
        return command(LockerOperations.Action.COLLECT, q);
    }

    private Booking command(LockerOperations.Action action, CommandInput q) throws ProblemMessage {
        return SoapFaults.call(() -> map(gateway.command(actor(), action, q.getRequestId(), q.getId(), q.getCode())));
    }

    private Booking map(LockerOperations.Reservation value) {
        var b = new Booking();
        b.setId(value.id());
        b.setState(value.state());
        b.setCompartmentId(value.compartmentId());
        return b;
    }

}
