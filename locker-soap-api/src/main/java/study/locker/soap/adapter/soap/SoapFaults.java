package study.locker.soap.adapter.soap;

import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.server.endpoint.*;
import study.locker.edge.application.GatewayFailure;
import study.locker.soap.wire.*;

@Component
final class SoapFaults extends AbstractSoapFaultDefinitionExceptionResolver {
    private final Jaxb2Marshaller marshaller;
    private final ObjectFactory xml = new ObjectFactory();

    SoapFaults(Jaxb2Marshaller marshaller) {
        this.marshaller = marshaller;
        setOrder(0);
    }

    @Override
    protected SoapFaultDefinition getFaultDefinition(Object endpoint, Exception error) {
        if (!(error instanceof GatewayFailure)) return null;
        var definition = new SoapFaultDefinition();
        definition.setFaultCode(SoapFaultDefinition.SERVER);
        return definition;
    }

    @Override
    protected void customizeFault(Object endpoint, Exception error, SoapFault fault) {
        var failure = (GatewayFailure) error;
        var problem = new Problem();
        problem.setCode(failure.code());
        problem.setMessage(failure.getMessage());
        marshaller.marshal(xml.createLockerFault(problem), fault.addFaultDetail().getResult());
    }
}
