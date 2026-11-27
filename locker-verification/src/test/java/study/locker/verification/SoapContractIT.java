package study.locker.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static study.locker.verification.StackClient.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Element;

import java.util.Map;

@EnabledIfSystemProperty(named = "locker.stack", matches = "true")
class SoapContractIT {
    private static final String ENDPOINT = local(18083) + "/soap/lockers";
    private static final String CONTRACT = "urn:lockers:v1";

    @Test
    void authenticatedDiscoveryPublishesTheReachableSoap11AddressAndActions() throws Exception {
        String url = ENDPOINT + "?wsdl";
        assertThat(send("GET", url, null, null, null, Map.of()).statusCode()).isEqualTo(401);
        var response = send("GET", url, token("reader", "locker.read"), null, null, Map.of());
        assertThat(response.statusCode()).isEqualTo(200);
        var wsdl = xml(response.body());
        assertThat(wsdl.getDocumentElement().getAttribute("targetNamespace")).isEqualTo(CONTRACT);
        String soap = "http://schemas.xmlsoap.org/wsdl/soap/";
        var address = (Element) wsdl.getElementsByTagNameNS(soap, "address").item(0);
        assertThat(address.getAttribute("location")).isEqualTo(ENDPOINT);
        var actions = elements(wsdl, soap, "operation")
                .map(operation -> operation.getAttribute("soapAction"))
                .toList();
        assertThat(actions).containsExactlyInAnyOrder("urn:lockers:available", "urn:lockers:get", "urn:lockers:reserve",
                "urn:lockers:cancel", "urn:lockers:deposit", "urn:lockers:reissue", "urn:lockers:collect");
    }

    @Test
    void invalidLookupReturnsTheDeclaredLockerFaultDetail() throws Exception {
        var body = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body><get xmlns="urn:lockers:v1"><id>invalid</id></get></s:Body>
                </s:Envelope>
                """;
        var response = send("POST", ENDPOINT, token("reader", "locker.read"), "text/xml", body,
                Map.of("SOAPAction", "\"urn:lockers:get\""));
        assertThat(response.statusCode()).isEqualTo(500);
        var fault = (Element) xml(response.body()).getElementsByTagNameNS("http://schemas.xmlsoap.org/soap/envelope/",
                "Fault").item(0);
        String faultCode = fault.getElementsByTagName("faultcode").item(0).getTextContent();
        assertThat(faultCode.substring(faultCode.indexOf(':') + 1)).isEqualTo("Client");
        var detail = (Element) fault.getElementsByTagName("detail").item(0);
        var problem = (Element) detail.getElementsByTagNameNS(CONTRACT, "LockerFault").item(0);
        assertThat(problem.getElementsByTagNameNS(CONTRACT, "code").item(0).getTextContent()).isEqualTo(
                "INVALID_ARGUMENT");
        assertThat(problem.getElementsByTagNameNS(CONTRACT, "message").item(0).getTextContent()).contains("UUID");
    }
}
