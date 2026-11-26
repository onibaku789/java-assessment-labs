package study.parcel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.time.Duration;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("Enable when starting Week 3")
class Week03CarrierIT {
    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String CARRIER = "urn:parcel:carrier";
    private static HttpServer carrier;
    private static URI endpoint;
    private final CarrierClient client = new CarrierClient();

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

    @BeforeAll static void startCarrier() throws Exception {
        var parser = DocumentBuilderFactory.newDefaultNSInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var builder = parser.newDocumentBuilder();
        carrier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        endpoint = URI.create("http://127.0.0.1:" + carrier.getAddress().getPort() + "/carrier");
        var wsdl = new ClassPathResource("carrier.wsdl").getContentAsString(UTF_8)
                .replace("http://127.0.0.1:18083/carrier", endpoint.toString());
        carrier.createContext("/carrier", exchange -> {
            try (exchange) {
                if (!"/carrier".equals(exchange.getRequestURI().getPath())) {
                    exchange.sendResponseHeaders(404, -1);
                } else if ("GET".equals(exchange.getRequestMethod()) && "wsdl".equals(exchange.getRequestURI().getQuery())) {
                    reply(exchange, 200, wsdl);
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    handleTracking(exchange, builder);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            }
        });
        carrier.start();
    }

    private static void handleTracking(HttpExchange exchange, DocumentBuilder parser) throws IOException {
        Node track;
        try {
            var doc = parser.parse(exchange.getRequestBody());
            track = doc.getElementsByTagNameNS(CARRIER, "Track").item(0);
            Node body = track == null ? null : track.getParentNode();
            if (body == null || !SOAP.equals(body.getNamespaceURI()) || !"Body".equals(body.getLocalName())
                    || body.getParentNode() != doc.getDocumentElement()
                    || !SOAP.equals(doc.getDocumentElement().getNamespaceURI())
                    || !"Envelope".equals(doc.getDocumentElement().getLocalName())) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
        } catch (SAXException malformed) {
            exchange.sendResponseHeaders(400, -1);
            return;
        }
        boolean found = "P-100".equals(track.getTextContent());
        String payload = found ? "<c:TrackResponse>IN_TRANSIT</c:TrackResponse>" : """
                <s:Fault><faultcode>s:Client</faultcode><faultstring>Unknown parcel</faultstring>
                <detail><c:UnknownParcel>missing</c:UnknownParcel></detail></s:Fault>""";
        reply(exchange, found ? 200 : 500,
                "<s:Envelope xmlns:s=\"" + SOAP + "\" xmlns:c=\"" + CARRIER + "\"><s:Body>"
                        + payload + "</s:Body></s:Envelope>");
    }

    private static void reply(HttpExchange exchange, int status, String xml) throws IOException {
        byte[] bytes = xml.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @AfterAll static void stopCarrier() {
        if (carrier != null) carrier.stop(0);
    }
}
