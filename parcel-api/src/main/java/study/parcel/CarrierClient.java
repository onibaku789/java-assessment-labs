package study.parcel;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceException;
import org.apache.cxf.binding.soap.interceptor.Soap11FaultInInterceptor;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.interceptor.ClientFaultConverter;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.HTTPConduit;
import org.springframework.core.NestedExceptionUtils;
import study.parcel.carrier.Carrier_Service;
import study.parcel.carrier.Missing;

/** Week 3 adapter. An empty result represents the carrier's declared UnknownParcel fault. */
class CarrierClient {
    private static final long TIMEOUT_MILLIS = 2_000;
    private final Carrier_Service service = new Carrier_Service();

    Optional<String> track(URI carrierEndpoint, String trackingId) throws IOException {
        var port = service.getCarrierPort();
        var client = ClientProxy.getClient(port);
        try {
            ((BindingProvider) port).getRequestContext()
                    .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, carrierEndpoint.toString());
            var policy = ((HTTPConduit) client.getConduit()).getClient();
            policy.setConnectionTimeout(TIMEOUT_MILLIS);
            policy.setReceiveTimeout(TIMEOUT_MILLIS);
            client.getInFaultInterceptors().add(new CarrierFaultNamespaceInterceptor());

            String status = port.track(trackingId);
            if (status == null) {
                throw new IOException("Carrier response is missing TrackResponse");
            }
            return Optional.of(status);
        } catch (Missing unknownParcel) {
            return Optional.empty();
        } catch (WebServiceException failure) {
            if (NestedExceptionUtils.getMostSpecificCause(failure) instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("Carrier lookup failed", failure);
        } finally {
            client.destroy();
        }
    }

    private static final class CarrierFaultNamespaceInterceptor extends AbstractPhaseInterceptor<Message> {
        CarrierFaultNamespaceInterceptor() {
            super(Phase.UNMARSHAL);
            addAfter(Soap11FaultInInterceptor.class.getName());
            addBefore(ClientFaultConverter.class.getName());
        }

        @Override
        public void handleMessage(Message message) {
            if (message.getContent(Exception.class) instanceof Fault fault && fault.hasDetails()) {
                var detail = DOMUtils.getFirstElement(fault.getDetail());
                // CXF otherwise falls back to matching UnknownParcel by local name alone.
                if (detail != null && !Carrier_Service.SERVICE.getNamespaceURI().equals(detail.getNamespaceURI())) {
                    message.put(ClientFaultConverter.DISABLE_FAULT_MAPPING, true);
                }
            }
        }
    }
}
