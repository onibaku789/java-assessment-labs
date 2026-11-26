package study.parcel;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/** Week 3 adapter. An empty result represents the carrier's declared UnknownParcel fault. */
class CarrierClient {
    Optional<String> track(URI carrierEndpoint, String trackingId) throws IOException {
        throw new UnsupportedOperationException("Week 3: call the SOAP carrier and translate its response");
    }
}
