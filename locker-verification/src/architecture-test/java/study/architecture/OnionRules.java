package study.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

final class OnionRules {
    static final DescribedPredicate<JavaClass> CORE = resideInAnyPackage("..domain.model..", "..domain.service..", "..application..");
    private static final DescribedPredicate<JavaClass> ASSEMBLY =
            resideInAnyPackage("study.locker.*.bootstrap..", "study.locker.*.integration..")
                    .or(resideInAnyPackage("study.locker.*")
                            .and(annotatedWith("org.springframework.boot.autoconfigure.SpringBootApplication")));
    private static final DescribedPredicate<JavaClass> SHARED = resideInAnyPackage("study.locker.common",
            "study.locker.wire", "study.locker.security", "study.locker.delivery");
    private static final DescribedPredicate<JavaClass> PLATFORM = SHARED.or(resideInAnyPackage("study.locker.soap.wire"));
    private static final DescribedPredicate<JavaClass> VERIFICATION = resideInAnyPackage("study.locker.verification..", "study.architecture");
    private static final DescribedPredicate<JavaClass> ADAPTER = resideInAnyPackage("study.locker.*.adapter.*..");
    private static final DescribedPredicate<JavaClass> PUBLIC_ADAPTER = resideInAnyPackage(
            "study.locker.rest.adapter..", "study.locker.graphql.adapter..", "study.locker.soap.adapter..");
    private static final DescribedPredicate<? super JavaClass> SHARED_TEST_TYPES = nameMatching(
            "study\\.architecture\\.(ArchitectureTest|OnionRules|CoreRules|ComplexityTest|ComplexityLimitsTest"
                    + "|BoundaryViolationTest)(\\$.*)?");

    private OnionRules() {}

    static ArchRule moduleOwnership(List<String> contexts, Path tests) {
        String[] packages = contexts.stream().map(context -> "study.locker." + context + "..").toArray(String[]::new);
        var sharedTests = fromOutput(tests).and(SHARED_TEST_TYPES);
        return classes().that(not(sharedTests)).should().resideInAnyPackage(packages)
                .as("Module owns only " + String.join(", ", packages) + " and shared architecture tests");
    }

    static ArchRule onion() {
        return Architectures.onionArchitecture().domainModels("..domain.model..").domainServices("..domain.service..")
                .applicationServices("..application..")
                .adapter("infrastructure", ADAPTER.or(ASSEMBLY).or(PLATFORM))
                .adapter("verification", VERIFICATION)
                .withOptionalLayers(true).ensureAllClassesAreContainedInArchitecture();
    }

    static ArchRule independentAdapters() {
        return slices().matching("study.locker.(*).adapter.(*)..").namingSlices("$1 $2 adapter")
                .should().notDependOnEachOther().allowEmptyShould(true);
    }

    static ArchRule platformIsIndependent() {
        return noClasses().that(PLATFORM.or(VERIFICATION)).should().dependOnClassesThat(ADAPTER.or(ASSEMBLY).or(CORE))
                .allowEmptyShould(true).as("Shared libraries, wire contracts and verification tools are independent of service implementations");
    }

    static ArchRule publicGateway() {
        return noClasses().that(PUBLIC_ADAPTER).should().dependOnClassesThat()
                .resideInAnyPackage("io.grpc..", "study.locker.wire..").allowEmptyShould(true)
                .as("Public protocol adapters call Reservations through LockerOperations");
    }

    static ArchRule integrationTestsOnly(Path mainOutput) {
        return noClasses().that(fromOutput(mainOutput)).should().resideInAnyPackage("..integration..")
                .allowEmptyShould(true).as("Integration packages contain test code only");
    }

    static ArchRule assemblyIsPrivate() {
        return noClasses().that(resideInAnyPackage("study.locker..").and(not(ASSEMBLY)))
                .should().dependOnClassesThat(ASSEMBLY).allowEmptyShould(true)
                .as("Only composition roots and integration tests depend on assembly code");
    }

    static ArchRule contextDependencies(String context) {
        var own = inContext(context);
        var allowed = own.or(not(resideInAnyPackage("study.locker..")));
        if (!context.equals("verification")) allowed = allowed.or(SHARED);
        if (Set.of("rest", "graphql", "soap").contains(context)) {
            allowed = allowed.or(resideInAnyPackage("study.locker.edge.application..", "study.locker.edge.bootstrap.."));
        }
        return classes().that(own).should().onlyDependOnClassesThat(componentType(allowed)).allowEmptyShould(true)
                .as(context + " accesses only its own context and permitted shared APIs");
    }

    static DescribedPredicate<JavaClass> inContext(String context) {
        return resideInAnyPackage("study.locker." + context + "..");
    }

    static DescribedPredicate<JavaClass> fromOutput(Path directory) {
        var root = directory.toAbsolutePath().normalize();
        return describe("compiled in " + root.getFileName(), type -> type.getSource()
                .map(source -> Path.of(source.getUri()).normalize().startsWith(root)).orElse(false));
    }

    static DescribedPredicate<JavaClass> componentType(DescribedPredicate<JavaClass> allowed) {
        return describe(allowed.getDescription(), type -> allowed.test(type.getBaseComponentType()));
    }
}
