package study.locker.soap.adapter.soap;

import jakarta.xml.bind.JAXBElement;
import org.springframework.ws.server.endpoint.annotation.*;
import study.locker.edge.application.LockerOperations;
import study.locker.soap.wire.*;

@Endpoint
public class LockerSoap {
    static final String NAMESPACE = "urn:lockers:v1";
    private final LockerOperations gateway;
    private final ObjectFactory xml = new ObjectFactory();

    public LockerSoap(LockerOperations gateway) {
        this.gateway = gateway;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "available")
    @ResponsePayload
    public JAXBElement<Compartments> available(@RequestPayload JAXBElement<SearchInput> request) {
        var input = request.getValue();
        var response = new Compartments();
        gateway.available(input.getSite(), input.getSize()).stream()
                .map(LockerSoap::compartment)
                .forEachOrdered(response.getItems()::add);
        return xml.createAvailableResponse(response);
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "get")
    @ResponsePayload
    public JAXBElement<Booking> get(@RequestPayload JAXBElement<KeyInput> request) {
        return xml.createGetResponse(booking(gateway.get(request.getValue().getId())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "reserve")
    @ResponsePayload
    public JAXBElement<Booking> reserve(@RequestPayload JAXBElement<CommandInput> request) {
        var input = request.getValue();
        return xml.createReserveResponse(booking(gateway.reserve(input.getRequestId(), input.getId())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "cancel")
    @ResponsePayload
    public JAXBElement<Booking> cancel(@RequestPayload JAXBElement<CommandInput> request) {
        var input = request.getValue();
        return xml.createCancelResponse(booking(gateway.cancel(input.getRequestId(), input.getId())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "deposit")
    @ResponsePayload
    public JAXBElement<Booking> deposit(@RequestPayload JAXBElement<CommandInput> request) {
        var input = request.getValue();
        return xml.createDepositResponse(booking(gateway.deposit(input.getRequestId(), input.getId())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "reissue")
    @ResponsePayload
    public JAXBElement<Booking> reissue(@RequestPayload JAXBElement<CommandInput> request) {
        var input = request.getValue();
        return xml.createReissueResponse(booking(gateway.reissue(input.getRequestId(), input.getId())));
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "collect")
    @ResponsePayload
    public JAXBElement<Booking> collect(@RequestPayload JAXBElement<CommandInput> request) {
        var input = request.getValue();
        return xml.createCollectResponse(
                booking(gateway.collect(input.getRequestId(), input.getId(), input.getCode())));
    }

    private static Compartment compartment(LockerOperations.Slot slot) {
        var item = new Compartment();
        item.setId(slot.id());
        item.setSite(slot.site());
        item.setSize(slot.size());
        return item;
    }

    private static Booking booking(LockerOperations.Reservation value) {
        var response = new Booking();
        response.setId(value.id());
        response.setState(value.state());
        response.setCompartmentId(value.compartmentId());
        return response;
    }
}
