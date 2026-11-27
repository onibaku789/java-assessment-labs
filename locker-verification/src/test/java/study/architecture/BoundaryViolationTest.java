package study.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class BoundaryViolationTest {
    @TempDir Path root;

    // Exercise output roots independently of Maven's conventional directory names.
    private Path main() { return root.resolve("test-classes/compiled-main"); }
    private Path tests() { return root.resolve("compiled-tests"); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moduleOwnsItsClassesAndIntegrationFixturesBelongInTestOutput(boolean test) throws Exception {
        var types = compile(test ? tests() : main(), Map.of(
                "study.locker.rest.integration.Fixture", "class Fixture {}",
                "study.locker.soap.adapter.soap.Stray", "class Stray {}",
                "study.architecture.OnionRules", "class OnionRules { static class Nested {} }",
                "study.architecture.Unknown", "class Unknown {}"));
        var ownership = details(OnionRules.moduleOwnership(List.of("rest"), tests()), types);
        assertThat(ownership).anyMatch(line -> line.contains(".Stray")).anyMatch(line -> line.contains(".Unknown"))
                .noneMatch(line -> line.contains(".Fixture"));
        var integration = details(OnionRules.integrationTestsOnly(main()), types);
        if (test) {
            assertThat(ownership).noneMatch(line -> line.contains(".OnionRules"));
            assertThat(integration).isEmpty();
        } else {
            assertThat(ownership).anyMatch(line -> line.contains(".OnionRules$Nested"));
            assertThat(integration).singleElement().asString().contains(".Fixture");
        }
    }

    @Test
    void onlyCompositionRootsCanWireTheEdgeAndAdaptersCannotReachAcrossContexts() throws Exception {
        var types = compile(main(), Map.of(
                "study.locker.edge.bootstrap.Wiring", "public class Wiring {}",
                "study.locker.rest.bootstrap.Composition", "class Composition { study.locker.edge.bootstrap.Wiring wiring; }",
                "study.locker.reservation.adapter.rest.Internal", "public class Internal {}",
                "study.locker.rest.adapter.rest.Endpoint", """
                        class Endpoint { study.locker.edge.bootstrap.Wiring wiring;
                                         study.locker.reservation.adapter.rest.Internal other; }
                        """));
        var assembly = details(OnionRules.assemblyIsPrivate(), types);
        assertEdge(assembly, "Endpoint", "Wiring");
        assertThat(assembly).noneMatch(line -> line.contains("Composition"));
        var contexts = details(OnionRules.contextDependencies("rest"), types);
        assertEdge(contexts, "Endpoint", "Internal");
        assertThat(contexts).noneMatch(line -> line.contains("Composition"));
        assertEdge(details(OnionRules.independentAdapters(), types), "Endpoint", "Internal");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void wiringCanUseConcreteAdaptersAndNewAdapterPackagesAreIsolated(boolean test) throws Exception {
        var types = compile(test ? tests() : main(), Map.of(
                "study.locker.reservation.adapter.archive.Store", "public class Store {}",
                "study.locker.reservation.adapter.archive.Writer", "class Writer { Store store; }",
                "study.locker.reservation.adapter.grpcserver.Endpoint",
                        "class Endpoint { study.locker.reservation.adapter.archive.Store store; }",
                "study.locker.reservation.bootstrap.Wiring",
                        "class Wiring { study.locker.reservation.adapter.archive.Store store; }",
                "study.locker.soap.wire.Binding",
                        "class Binding { study.locker.reservation.adapter.archive.Store store; }"));
        OnionRules.onion().check(types);
        var adapters = details(OnionRules.independentAdapters(), types);
        assertEdge(adapters, "Endpoint", "Store");
        assertThat(adapters).noneMatch(line -> line.contains(".Writer") || line.contains(".Wiring"));
        assertEdge(details(OnionRules.platformIsIndependent(), types), "Binding", "Store");
    }

    @Test
    void onionRejectsAnAdapterInADomainTestAndAnUnassignedFixture() throws Exception {
        var types = compile(tests(), Map.of(
                "study.locker.reservation.adapter.persistence.Repository", "public interface Repository {}",
                "study.locker.reservation.domain.model.BadDomainTest", """
                        class BadDomainTest { study.locker.reservation.adapter.persistence.Repository repo;
                                              java.sql.Connection sql; }
                        """,
                "study.locker.reservation.testsupport.HiddenFixture", "class HiddenFixture {}"));
        var onion = details(OnionRules.onion(), types);
        assertEdge(onion, "BadDomainTest", "Repository");
        assertThat(onion).anyMatch(line -> line.contains("HiddenFixture"));
        assertEdge(details(CoreRules.dependencies("reservation", tests(), true), types), "BadDomainTest", "java.sql.Connection");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publicAdaptersUseTheEdgePortAndSoapCanUseItsOwnBindings(boolean test) throws Exception {
        var sources = new HashMap<String, String>();
        sources.put("study.locker.edge.application.LockerOperations", "public interface LockerOperations {}");
        sources.put("study.locker.wire.ReservationsGrpc",
                "public class ReservationsGrpc { public static class ReservationsBlockingStub { public void get() {} } }");
        sources.put("io.grpc.Channel", "public interface Channel {}");
        sources.put("study.locker.soap.wire.Reserve", "public class Reserve {}");
        for (var protocol : List.of("rest", "graphql", "soap")) {
            var pkg = "study.locker." + protocol + ".adapter." + protocol;
            sources.put(pkg + ".Bypass", """
                    class Bypass { void get(study.locker.wire.ReservationsGrpc.ReservationsBlockingStub stub) { stub.get(); }
                                   io.grpc.Channel channel; }
                    """);
            sources.put(pkg + ".Allowed", "class Allowed { study.locker.edge.application.LockerOperations port; }");
        }
        sources.put("study.locker.soap.adapter.soap.Binding", "class Binding { study.locker.soap.wire.Reserve body; }");
        sources.put("study.locker.edge.adapter.grpc.Client", "class Client { io.grpc.Channel channel; }");
        var report = details(OnionRules.publicGateway(), compile(test ? tests() : main(), sources));
        for (var protocol : List.of("rest", "graphql", "soap")) {
            var offender = "study.locker." + protocol + ".adapter." + protocol + ".Bypass";
            assertEdge(report, offender, "ReservationsBlockingStub");
            assertEdge(report, offender, "io.grpc.Channel");
        }
        assertThat(report).allMatch(line -> line.contains(".Bypass"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void securityAndTelemetryBelongOutsideBusinessCode(boolean test) throws Exception {
        var src = new HashMap<String, String>();
        src.put("org.springframework.security.core.Authentication", "public interface Authentication {}");
        src.put("io.opentelemetry.api.trace.Span", "public interface Span {}");
        src.put("io.micrometer.core.instrument.MeterRegistry", "public class MeterRegistry {}");
        src.put("org.slf4j.Logger", "public interface Logger {}");
        src.put("study.locker.reservation.domain.model.BadRule",
                "class BadRule { org.springframework.security.core.Authentication actor; }");
        String fields = """
                { io.opentelemetry.api.trace.Span span; io.micrometer.core.instrument.MeterRegistry metrics;
                  org.slf4j.Logger log; java.util.logging.Logger jul;
                  void trace() { System.getLogger("lookup"); } }
                """;
        src.put("study.locker.reservation.application.BadUseCase", "class BadUseCase " + fields);
        src.put("study.locker.reservation.adapter.grpcserver.BadTransport", "class BadTransport " + fields);
        src.put("study.locker.reservation.adapter.observability.Allowed", "class Allowed " + fields);
        var output = test ? tests() : main();
        var types = compile(output, src);
        assertEdge(details(CoreRules.dependencies("reservation", output, test), types), "BadRule", "Authentication");
        var telemetry = details(CoreRules.telemetry(), types);
        var loggers = details(CoreRules.businessLogging(), types);
        for (var offender : List.of("BadTransport", "BadUseCase")) {
            for (var api : List.of("io.opentelemetry.api.trace.Span", "MeterRegistry")) {
                assertEdge(telemetry, offender, api);
            }
        }
        for (var api : List.of("org.slf4j.Logger", "java.util.logging.Logger", "getLogger")) {
            assertEdge(loggers, "BadUseCase", api);
        }
        assertThat(telemetry).noneMatch(line -> line.contains(".Allowed"));
        assertThat(telemetry).noneMatch(line -> line.contains("org.slf4j") || line.contains("java.util.logging"));
        assertThat(loggers).noneMatch(line -> line.contains("BadTransport") || line.contains(".Allowed"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void filesystemAccessAndTestLibrariesDependOnTheActualOutputRoot(boolean test) throws Exception {
        var types = compile(test ? tests() : main(), Map.of(
                "study.locker.reservation.domain.model.Disk", """
                        class Disk {
                            String read(java.nio.file.Path path) throws java.io.IOException {
                                return java.nio.file.Files.readString(path);
                            }
                            java.io.FileInputStream stream; java.nio.channels.FileChannel channel;
                        }
                        """,
                "study.locker.reservation.application.Writes", """
                        class Writes {
                            void write() throws Exception { new java.io.PrintWriter("file").close(); }
                            void read(java.nio.file.Path path) throws Exception { new java.util.Scanner(path.toRealPath()).close(); }
                        }
                        """,
                "study.locker.reservation.application.Memory", """
                        class Memory { java.io.ByteArrayInputStream source; java.util.Scanner scanner = new java.util.Scanner("input");
                                       java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.StringWriter()); }
                        """,
                "study.locker.reservation.application.Assertions", "class Assertions { @org.junit.jupiter.api.Test void check() {} }"));
        var files = details(CoreRules.filesystemTypes(main()), types);
        var calls = details(CoreRules.filesystemCalls(main()), types);
        if (test) {
            assertThat(files).isEmpty();
            assertThat(calls).isEmpty();
            CoreRules.dependencies("reservation", tests(), true).check(types);
        } else {
            for (String api : List.of("java.nio.file.Files", "FileInputStream", "FileChannel")) assertEdge(files, "Disk", api);
            assertEdge(calls, "Writes", "PrintWriter");
            assertEdge(calls, "Writes", "Scanner");
            assertEdge(calls, "Writes", "toRealPath");
            assertThat(files).noneMatch(line -> line.contains(".Memory"));
            assertThat(calls).noneMatch(line -> line.contains(".Memory"));
            assertEdge(details(CoreRules.dependencies("reservation", main(), false), types), "Assertions", "org.junit");
        }
    }

    private static JavaClasses compile(Path output, Map<String, String> sources) throws Exception {
        Files.createDirectories(output);
        var args = new ArrayList<>(List.of("-d", output.toString()));
        for (var source : sources.entrySet()) {
            var name = source.getKey();
            var file = output.resolveSibling("src").resolve(name.replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            String pkg = name.substring(0, name.lastIndexOf('.'));
            args.add(Files.writeString(file, "package " + pkg + ";\n" + source.getValue()).toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new))).isZero();
        return new ClassFileImporter().importPath(output);
    }

    private static List<String> details(ArchRule rule, JavaClasses types) {
        return rule.evaluate(types).getFailureReport().getDetails();
    }

    private static void assertEdge(List<String> report, String from, String to) {
        assertThat(report).anySatisfy(line -> assertThat(line).contains(from, to));
    }
}
