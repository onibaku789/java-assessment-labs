package study.parcel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

class Week03CarrierIT {
    private static final String SOAP = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String CARRIER = "urn:parcel:carrier";
    private static HttpServer carrier;
    private static URI endpoint;
    private final CarrierClient client = new CarrierClient();

    @Test
    void readTheStatusFromTheSoapEnvelope() throws Exception {
        assertThat(client.track(endpoint, "P-100")).contains("IN_TRANSIT");
    }

    @Test
    void mapTheDeclaredUnknownParcelFaultToAnEmptyResult() throws Exception {
        assertThat(client.track(endpoint, "P-404")).isEmpty();
    }

    @Test
    void useTheSoap11ContentTypeAndWsdlAction() throws Exception {
        assertThat(client.track(endpoint, "P-HEADERS")).contains("COLLECTED");
    }

    @Test
    void sendTrackingIdsAsXmlText() throws Exception {
        assertThat(client.track(endpoint, "BOX & <fragile>")).contains("LABEL_PRINTED");
    }

    @Test
    void readTheResponseByNamespaceEvenWhenPrefixesChange() throws Exception {
        assertThat(client.track(endpoint, "P-DELIVERED")).contains("DELIVERED");
    }

    @ParameterizedTest(name = "{0} produces an I/O error")
    @CsvSource({
            "undeclared SOAP fault, P-OTHER-FAULT",
            "UnknownParcel in another namespace, P-WRONG-FAULT",
            "HTTP 503 without a SOAP body, P-UNAVAILABLE",
            "malformed XML, P-MALFORMED",
            "SOAP body without TrackResponse, P-NO-STATUS"
    })
    void reportCarrierErrorsSeparatelyFromAnUnknownParcel(String scenario, String id) {
        assertThatIOException().as(scenario).isThrownBy(() -> client.track(endpoint, id));
    }

    @Test
    @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void reportAnUnresponsiveCarrierAsATransportFailureWithinFiveSeconds() throws Exception {
        try (var silent = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            var uri = URI.create("http://127.0.0.1:" + silent.getLocalPort() + "/carrier");
            assertThatIOException().as("A stalled carrier must produce a transport failure")
                    .isThrownBy(() -> client.track(uri, "P-100"));
        }
    }

    @BeforeAll
    static void startCarrier() throws Exception {
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
        switch (track.getTextContent()) {
            case "P-100" -> soapReply(exchange, 200, "<c:TrackResponse>IN_TRANSIT</c:TrackResponse>");
            case "P-HEADERS" -> {
                var headers = exchange.getRequestHeaders();
                var type = headers.getFirst("Content-Type");
                var action = headers.getFirst("SOAPAction");
                if (type == null || !"text/xml".equalsIgnoreCase(type.split(";", 2)[0].strip())) {
                    exchange.sendResponseHeaders(415, -1);
                } else if (!"\"urn:parcel:Track\"".equals(action) && !"urn:parcel:Track".equals(action)) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    soapReply(exchange, 200, "<c:TrackResponse>COLLECTED</c:TrackResponse>");
                }
            }
            case "BOX & <fragile>" -> soapReply(exchange, 200, "<c:TrackResponse>LABEL_PRINTED</c:TrackResponse>");
            case "P-DELIVERED" -> reply(exchange, 200, """
                    <env:Envelope xmlns:env="http://schemas.xmlsoap.org/soap/envelope/">
                      <env:Body><TrackResponse xmlns="urn:parcel:carrier">DELIVERED</TrackResponse></env:Body>
                    </env:Envelope>""");
            case "P-OTHER-FAULT" -> soapReply(exchange, 500, """
                    <s:Fault><faultcode>s:Server</faultcode><faultstring>Carrier offline</faultstring></s:Fault>""");
            case "P-WRONG-FAULT" -> soapReply(exchange, 500, """
                    <s:Fault><faultcode>s:Client</faultcode><faultstring>Unknown parcel</faultstring>
                    <detail><UnknownParcel xmlns="urn:other">missing</UnknownParcel></detail></s:Fault>""");
            case "P-UNAVAILABLE" -> exchange.sendResponseHeaders(503, -1);
            case "P-MALFORMED" -> reply(exchange, 200, "<incomplete>");
            case "P-NO-STATUS" -> soapReply(exchange, 200, "");
            default -> soapReply(exchange, 500, """
                    <s:Fault><faultcode>s:Client</faultcode><faultstring>Unknown parcel</faultstring>
                    <detail><c:UnknownParcel>missing</c:UnknownParcel></detail></s:Fault>""");
        }
    }

    private static void soapReply(HttpExchange exchange, int status, String body) throws IOException {
        reply(exchange, status, "<s:Envelope xmlns:s=\"" + SOAP + "\" xmlns:c=\"" + CARRIER + "\"><s:Body>"
                + body + "</s:Body></s:Envelope>");
    }

    private static void reply(HttpExchange exchange, int status, String xml) throws IOException {
        byte[] bytes = xml.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @AfterAll
    static void stopCarrier() {
        if (carrier != null) carrier.stop(0);
    }
}
