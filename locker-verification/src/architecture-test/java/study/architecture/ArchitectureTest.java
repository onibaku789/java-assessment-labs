package study.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

class ArchitectureTest {
    private static final Map<String, List<String>> PACKAGES = Map.ofEntries(
            Map.entry("locker-contracts", List.of("common", "wire")), Map.entry("locker-security", List.of("security")),
            Map.entry("locker-delivery", List.of("delivery")), Map.entry("locker-edge", List.of("edge")),
            Map.entry("locker-directory-service", List.of("directory")),
            Map.entry("reservation-service", List.of("reservation")), Map.entry("locker-rest-api", List.of("rest")),
            Map.entry("locker-graphql-api", List.of("graphql")), Map.entry("locker-soap-api", List.of("soap")),
            Map.entry("notification-worker", List.of("notification")), Map.entry("receipt-worker", List.of("receipt")),
            Map.entry("locker-verification", List.of("verification")));

    @Test
    void mainAndTestBytecodeFollowTheSameArchitecture() throws Exception {
        Path testOutput = Path.of(ArchitectureTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path mainOutput = testOutput.resolveSibling("classes");
        assertThat(mainOutput).isDirectory();
        var imported = new ClassFileImporter().importPaths(mainOutput, testOutput);
        var contexts = PACKAGES.get(mainOutput.getParent().getParent().getFileName().toString());
        assertThat(contexts).as("declared Maven module ownership").isNotNull();
        for (var cls : imported) {
            if (cls.getPackageName().equals("study.architecture")) {
                assertThat(cls.getName().split("[$]")[0]).isIn("study.architecture.ArchitectureTest",
                        "study.architecture.OnionRules", "study.architecture.ComplexityTest",
                        "study.architecture.ComplexityLimitsTest", "study.architecture.BoundaryViolationTest");
            } else {
                String p = cls.getPackageName() + ".";
                assertThat(contexts).as("owner of " + cls.getName())
                        .anyMatch(c -> p.startsWith("study.locker." + c + "."));
            }
        }
        OnionRules.onion().check(imported);
        OnionRules.boundaries().check(imported);
    }
}
