package study.parcel;

import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class Week03CarrierContract {
    private static Process carrier;
    private static URI endpoint;
    private final CarrierClient client = new CarrierClient();

    @BeforeAll static void startCarrierOnAnUnusedPort() throws Exception {
        // Run the supplied carrier on a private port, even if the learner has a copy running.
        carrier = new ProcessBuilder("python3", "-u", "-c", """
                import sys
                sys.path.insert(0, "fixtures")
                from carrier import Carrier, HTTPServer
                server = HTTPServer(("127.0.0.1", 0), Carrier)
                print(server.server_port, flush=True)
                server.serve_forever()
                """).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        var port = CompletableFuture.supplyAsync(() -> {
            try { return carrier.inputReader().readLine(); }
            catch (IOException error) { throw new UncheckedIOException(error); }
        }).get(10, TimeUnit.SECONDS);
        endpoint = URI.create("http://127.0.0.1:" + Integer.parseInt(port) + "/carrier");
    }

    @AfterAll static void stopCarrier() throws InterruptedException {
        if (carrier != null) {
            carrier.destroyForcibly();
            carrier.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test void readTheStatusFromTheSoapEnvelope() throws Exception {
        assertEquals(Optional.of("IN_TRANSIT"), client.track(endpoint, "P-100"));
    }

    @Test void mapTheDeclaredUnknownParcelFaultToAnEmptyResult() throws Exception {
        assertEquals(Optional.empty(), client.track(endpoint, "P-404"));
    }

    @Test void reportAnUnresponsiveCarrierAsATransportFailureWithinFiveSeconds() throws Exception {
        try (var silent = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            var uri = URI.create("http://127.0.0.1:" + silent.getLocalPort() + "/carrier");
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(IOException.class, () -> client.track(uri, "P-100"),
                            "Use a bounded request timeout and distinguish transport failure from UnknownParcel"));
        }
    }
}
