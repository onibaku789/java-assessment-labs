package study.locker.verification;

import static org.assertj.core.api.Assertions.assertThat;

import org.w3c.dom.*;

import tools.jackson.databind.*;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;

import javax.xml.parsers.DocumentBuilderFactory;

final class StackClient {
    enum Protocol {
        REST, GRAPHQL, SOAP
    }

    record Result(String code, String id, String state, String compartment) {
    }

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static final int OFFSET = Integer.getInteger("locker.portOffset", 0);

    static String local(int port) {
        return "http://127.0.0.1:" + (port + OFFSET);
    }

    static String token(String subject, String scopes) throws Exception {
        var claims = Map.<String, Object>of("sub", subject, "iss",
                System.getenv().getOrDefault("LOCKER_JWT_ISSUER", "urn:locker:lab"), "aud",
                System.getenv().getOrDefault("LOCKER_JWT_AUDIENCE", "locker-api"), "exp",
                Instant.now().plusSeconds(Long.parseLong(System.getenv().getOrDefault("LAB_TOKEN_TTL_SECONDS", "600")))
                        .getEpochSecond(), "scope", scopes, "email", subject + "@example.test", "email_verified", true);
        return sign(claims);
    }

    static String sign(Map<String, Object> claims) throws Exception {
        Path file = Path.of(System.getProperty("locker.privateKey", "../.lab/keys/private.pem"));
        String pem = Files.readString(file).replaceAll("-----[^-]+-----|\\s", "");
        var key = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        return sign(claims, key);
    }

    static String sign(Map<String, Object> claims, PrivateKey key) throws Exception {
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String payload = base64.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "." +
                base64.encodeToString(JSON.writeValueAsBytes(claims));
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(payload.getBytes(StandardCharsets.US_ASCII));
        return payload + "." + base64.encodeToString(signer.sign());
    }

    static HttpResponse<String> send(String method, String url, String token, String type, String body,
                                     Map<String, String> headers) throws Exception {
        var q = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8));
        if (token != null) q.header("Authorization", "Bearer " + token);
        headers.forEach(q::header);
        if (body != null) q.header("Content-Type", type);
        return HTTP.send(q.method(method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    static Result call(Protocol protocol, String op, String actor, String id, String request,
                       String code) throws Exception {
        return switch (protocol) {
            case REST -> rest(op, actor, id, request, code);
            case GRAPHQL -> graph(op, actor, id, request, code);
            case SOAP -> soap(op, actor, id, request, code);
        };
    }

    private static Result rest(String op, String actor, String id, String request, String code) throws Exception {
        var path = restPath(op, id);
        boolean read = op.equals("available") || op.equals("get");
        String body = op.equals("reserve") ? JSON.writeValueAsString(Map.of("compartmentId", id)) : (op.equals(
                "collect") ? JSON.writeValueAsString(Map.of("code", code)) : null);
        var r = send(read ? "GET" : "POST", local(18081) + path, actor, "application/json", body,
                read ? Map.of() : Map.of("Idempotency-Key", request));
        return restResult(op, r);
    }

    private static String restPath(String op, String id) {
        return switch (op) {
            case "available" -> "/v1/compartments?site=Central&size=SMALL";
            case "reserve" -> "/v1/reservations";
            case "get" -> "/v1/reservations/" + id;
            default -> "/v1/reservations/" + id + "/" + op;
        };
    }

    private static Result restResult(String op, HttpResponse<String> r) {
        if (r.statusCode() == 401) return error("UNAUTHENTICATED");
        var obj = JSON.readTree(r.body());
        if (r.statusCode() >= 400) {
            String fault = obj.path("code").asString("");
            if (fault.equals("INVALID_ARGUMENT")) assertThat(r.statusCode()).isEqualTo(400);
            return error(fault);
        }
        if (op.equals("available")) return slots(obj);
        if (op.equals("reserve"))
            assertThat(r.headers().firstValue("Location")).contains("/v1/reservations/" + obj.path("id").asString());
        return booking(obj);
    }

    private static Result graph(String op, String actor, String id, String request, String code) throws Exception {
        String field = op.equals("get") ? "reservation" : op;
        String query;
        Map<String, String> vars = new HashMap<>();
        if (op.equals("available")) query = "{available(site:\"Central\",size:\"SMALL\"){id site size}}";
        else if (op.equals("get")) {
            query = "query($id:ID!){reservation(id:$id){id state compartmentId}}";
            vars.put("id", id);
        } else {
            query = mutation(op, id, request, code, vars);
        }
        var r = send("POST", local(18082) + "/graphql", actor, "application/json",
                JSON.writeValueAsString(Map.of("query", query, "variables", vars)), Map.of());
        if (r.statusCode() == 401) return error("UNAUTHENTICATED");
        var result = JSON.readTree(r.body());
        if (result.has("errors"))
            return error(result.get("errors").get(0).path("extensions").path("code").asString(""));
        var obj = result.path("data").path(field);
        return op.equals("available") ? slots(obj) : booking(obj);
    }

    private static String mutation(String op, String id, String request, String code, Map<String, String> vars) {
        boolean collect = op.equals("collect");
        String arg = op.equals("reserve") ? "compartmentId" : "id";
        String query = "mutation($id:ID!,$key:ID!" + (collect ? ",$code:String!" : "") + "){" + op + "(" + arg +
                ":$id,requestId:$key" + (collect ? ",code:$code" : "") + "){id state compartmentId}}";
        vars.put("id", id);
        vars.put("key", request);
        if (collect) vars.put("code", code);
        return query;
    }

    private static Result soap(String op, String actor, String id, String request, String code) throws Exception {
        String args = switch (op) {
            case "available" -> "<site>Central</site><size>SMALL</size>";
            case "get" -> "<id>" + id + "</id>";
            default -> "<requestId>" + request + "</requestId><id>" + id + "</id>" +
                    (op.equals("collect") ? "<code>" + code + "</code>" : "");
        };
        String body = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><" + op +
                " xmlns=\"urn:lockers:v1\">" + args + "</" + op + "></s:Body></s:Envelope>";
        var r = send("POST", local(18083) + "/soap/lockers", actor, "text/xml", body,
                Map.of("SOAPAction", "\"urn:lockers:" + op + "\""));
        return soapResult(op, r);
    }

    private static Result soapResult(String op, HttpResponse<String> r) throws Exception {
        if (r.statusCode() == 401) return error("UNAUTHENTICATED");
        var parser = DocumentBuilderFactory.newDefaultNSInstance();
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = parser.newDocumentBuilder()
                .parse(new ByteArrayInputStream(r.body().getBytes(StandardCharsets.UTF_8)));
        if (r.statusCode() >= 400) return error(field(doc, "code"));
        if (op.equals("available")) {
            var fields = doc.getElementsByTagNameNS("urn:lockers:v1", "id");
            var ids = new ArrayList<String>();
            for (int i = 0; i < fields.getLength(); i++) ids.add(fields.item(i).getTextContent());
            return new Result("OK", String.join(",", ids), "", "");
        }
        return new Result("OK", field(doc, "id"), field(doc, "state"), field(doc, "compartmentId"));
    }

    private static String field(Document doc, String name) {
        var node = doc.getElementsByTagNameNS("urn:lockers:v1", name).item(0);
        return node == null ? "" : node.getTextContent();
    }

    private static Result slots(JsonNode obj) {
        var ids = new ArrayList<String>();
        obj.forEach(s -> ids.add(s.path("id").asString()));
        return new Result("OK", String.join(",", ids), "", "");
    }

    private static Result booking(JsonNode obj) {
        return new Result("OK", obj.path("id").asString(), obj.path("state").asString(),
                obj.path("compartmentId").asString());
    }

    private static Result error(String reason) {
        return new Result(reason, "", "", "");
    }
}
