package study.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.*;
import com.tngtech.archunit.library.Architectures;

import java.util.*;

/**
 * Rules shared by every Maven module, applied to all locally compiled main and test classes.
 */
final class OnionRules {
    private static final Set<String> ADAPTERS = Set.of("persistence", "security", "observability", "transaction",
            "grpc", "grpcserver", "grpcclient", "crypto", "kafka", "smtp", "s3", "rest", "graphql", "soap");
    private static final Set<String> LAUNCHERS = Set.of("directory.DirectoryApp", "reservation.ReservationApp",
            "rest.RestApp", "graphql.GraphqlApp", "soap.SoapApp", "notification.NotificationApp", "receipt.ReceiptApp");
    private static final DescribedPredicate<JavaClass> ASSEMBLY = describe(
            "composition roots and service integration tests",
            c -> LAUNCHERS.contains(c.getName().replaceFirst("^study[.]locker[.]", "")) ||
                    resideInAnyPackage("study.locker.*.bootstrap..", "study.locker.*.integration..").test(c));
    private static final DescribedPredicate<JavaClass> SUPPORT = resideInAnyPackage("study.locker.common",
            "study.locker.security", "study.locker.delivery", "study.locker.wire", "study.locker.soap.wire",
            "study.locker.verification..", "study.architecture");
    private static final DescribedPredicate<JavaClass> CORE = resideInAnyPackage("..domain.model..",
            "..domain.service..", "..application..");

    static ArchRule onion() {
        var rule = Architectures.onionArchitecture().domainModels("..domain.model..")
                .domainServices("..domain.service..").applicationServices("..application..").withOptionalLayers(true);
        for (String name : ADAPTERS) rule = rule.adapter(name, "..adapter." + name + "..");
        return rule.ignoreDependency(ASSEMBLY, alwaysTrue())
                .ensureAllClassesAreContainedInArchitectureIgnoring(ASSEMBLY.or(SUPPORT));
    }

    static ArchRule boundaries() {
        return classes().should(new ArchCondition<JavaClass>("respect context, fixture and framework boundaries") {
            public void check(JavaClass from, ConditionEvents events) {
                if (from.getPackageName().equals("study.architecture")) return;
                boolean test = from.getSource().map(source -> source.getUri().toString().contains("/test-classes/"))
                        .orElse(false);
                if (from.getPackageName().contains(".integration") && !test)
                    reject(events, from, "integration packages are test-only");
                for (var edge : from.getDirectDependenciesFromSelf()) {
                    var to = edge.getTargetClass().getBaseComponentType();
                    if (telemetryInWrongAdapter(from, to)) {
                        reject(events, from,
                                "Telemetry belongs in an observability decorator: " + edge.getDescription());
                    }
                    if (violatesBoundary(from, to, test)) reject(events, from, edge.getDescription());
                }
            }
        });
    }

    private static boolean telemetryInWrongAdapter(JavaClass from, JavaClass to) {
        return isTelemetryDependency(to) && !adapter(from).isEmpty() && !adapter(from).equals("observability");
    }

    private static boolean violatesBoundary(JavaClass from, JavaClass to, boolean test) {
        if (CORE.test(from) && !coreDependency(from, to, test)) return true;
        if (!to.getPackageName().startsWith("study.locker.")) return false;
        return violatesLockerBoundary(from, to);
    }

    private static boolean violatesLockerBoundary(JavaClass from, JavaClass to) {
        String own = context(from), other = context(to);
        if (!mayDependOnContext(from, to, own, other)) return true;
        if (ASSEMBLY.test(from) || own.equals("verification")) return false;
        return ASSEMBLY.test(to) || crossesAdapter(from, to);
    }

    private static boolean crossesAdapter(JavaClass from, JavaClass to) {
        String a = adapter(from), b = adapter(to);
        return !a.isEmpty() && !b.isEmpty() && (!a.equals(b) || !context(from).equals(context(to)));
    }

    private static boolean coreDependency(JavaClass from, JavaClass to, boolean test) {
        String p = to.getPackageName();
        if (isCoreInstrumentation(to)) return false;
        if (to.isPrimitive() || allowedJavaPackage(p)) return true;
        if (test && resideInAnyPackage("org.junit..", "org.assertj..", "org.awaitility..", "org.mockito..").test(to))
            return true;
        return context(from).equals(context(to)) && CORE.test(to);
    }

    private static boolean isCoreInstrumentation(JavaClass type) {
        return isTelemetryDependency(type) || type.getPackageName().startsWith("java.util.logging") ||
                type.getName().startsWith("java.lang.System$Logger");
    }

    private static boolean allowedJavaPackage(String p) {
        return p.startsWith("java.") && !p.startsWith("java.sql") && !p.startsWith("java.net");
    }

    private static boolean isTelemetryDependency(JavaClass target) {
        return target.getPackageName().startsWith("io.micrometer.") ||
                target.getPackageName().startsWith("org.slf4j") ||
                target.getName().equals("study.locker.common.EventLog") ||
                target.getName().equals("study.locker.delivery.DeliveryEvents");
    }

    private static String adapter(JavaClass c) {
        String p = c.getPackageName();
        int start = p.indexOf(".adapter.");
        return start < 0 ? "" : p.substring(start + 9).split("[.]")[0];
    }

    private static String context(JavaClass c) {
        String p = c.getPackageName();
        return p.startsWith("study.locker.") ? p.substring(13).split("[.]")[0] : "";
    }

    private static boolean mayDependOnContext(JavaClass from, JavaClass to, String own, String other) {
        if (own.equals(other)) return true;
        if (own.equals("verification")) return false;
        if (Set.of("common", "wire", "security", "delivery").contains(other)) return true;
        if (!Set.of("rest", "graphql", "soap").contains(own) || !other.equals("edge")) return false;
        return edgeApiDependency(from, to);
    }

    private static boolean edgeApiDependency(JavaClass from, JavaClass to) {
        return resideInAnyPackage("study.locker.edge.application..").test(to) ||
                ASSEMBLY.test(from) && resideInAnyPackage("study.locker.edge.bootstrap..").test(to);
    }

    private static void reject(ConditionEvents events, JavaClass c, String message) {
        events.add(SimpleConditionEvent.violated(c, message));
    }

    private OnionRules() {
    }
}
