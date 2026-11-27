package study.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class ArchitectureTest {
    private static final Map<String, List<String>> PACKAGES = Map.ofEntries(
            Map.entry("locker-contracts", List.of("common", "wire")), Map.entry("locker-security", List.of("security")),
            Map.entry("locker-delivery", List.of("delivery")), Map.entry("locker-edge", List.of("edge")),
            Map.entry("locker-directory-service", List.of("directory")),
            Map.entry("reservation-service", List.of("reservation")), Map.entry("locker-rest-api", List.of("rest")),
            Map.entry("locker-graphql-api", List.of("graphql")), Map.entry("locker-soap-api", List.of("soap")),
            Map.entry("notification-worker", List.of("notification")), Map.entry("receipt-worker", List.of("receipt")),
            Map.entry("locker-verification", List.of("verification")));

    @TestFactory
    List<DynamicTest> architectureRules() throws Exception {
        Path tests = Path.of(ArchitectureTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path main = tests.resolveSibling("classes");
        assertThat(main).isDirectory();
        var imported = new ClassFileImporter().importPaths(main, tests);
        String module = main.getParent().getParent().getFileName().toString();
        var contexts = PACKAGES.get(module);
        assertThat(contexts).as("declared Maven module %s", module).isNotNull();
        return rules(main, tests, contexts).stream()
                .map(rule -> dynamicTest(rule.getDescription(), () -> rule.check(imported))).toList();
    }

    private static List<ArchRule> rules(Path main, Path tests, List<String> contexts) {
        var rules = new ArrayList<>(List.of(OnionRules.moduleOwnership(contexts, tests), OnionRules.onion(),
                OnionRules.independentAdapters(), OnionRules.platformIsIndependent(), OnionRules.publicGateway(),
                OnionRules.integrationTestsOnly(main), OnionRules.assemblyIsPrivate(), CoreRules.telemetry(),
                CoreRules.businessLogging(), CoreRules.filesystemTypes(main), CoreRules.filesystemCalls(main)));
        for (String context : contexts) {
            rules.add(OnionRules.contextDependencies(context));
            rules.add(CoreRules.dependencies(context, main, false));
            rules.add(CoreRules.dependencies(context, tests, true));
        }
        return rules;
    }
}
