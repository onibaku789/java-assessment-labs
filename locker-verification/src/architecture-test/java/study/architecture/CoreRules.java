package study.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

import java.nio.file.Path;
import java.util.Scanner;
import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.*;
import static com.tngtech.archunit.core.domain.AccessTarget.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.conditions.ArchConditions.accessTargetWhere;
import static com.tngtech.archunit.lang.conditions.ArchConditions.dependOnClassesThat;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;
import static study.architecture.OnionRules.CORE;

final class CoreRules {
    private static final DescribedPredicate<JavaClass> TEST_LIBRARIES =
            resideInAnyPackage("org.junit..", "org.assertj..", "org.awaitility..", "org.mockito..");
    private static final DescribedPredicate<JavaClass> TELEMETRY =
            resideInAnyPackage("io.micrometer..", "io.opentelemetry..")
                    .or(name("study.locker.common.EventLog")).or(name("study.locker.delivery.DeliveryEvents"));
    private static final DescribedPredicate<JavaClass> LOGGERS =
            resideInAnyPackage("org.slf4j..", "java.util.logging..").or(belongToAnyOf(System.Logger.class, System.LoggerFinder.class));
    private static final DescribedPredicate<JavaClass> OUTSIDE_OBSERVABILITY = CORE.or(
            resideInAnyPackage("..adapter..").and(not(resideInAnyPackage("..adapter.observability.."))));
    private static final Set<String> FILE_TYPES = Set.of("java.io.File", "java.io.FileDescriptor",
            "java.io.FileInputStream", "java.io.FileOutputStream", "java.io.FileReader", "java.io.FileWriter",
            "java.io.RandomAccessFile", "java.nio.channels.FileChannel", "java.nio.channels.AsynchronousFileChannel",
            "java.nio.file.Files", "java.nio.file.FileSystem", "java.nio.file.FileSystems", "java.nio.file.FileStore",
            "java.nio.file.WatchService", "java.util.zip.ZipFile", "java.util.jar.JarFile");
    private static final Set<String> FILENAME_CONSTRUCTORS =
            Set.of("java.io.PrintWriter", "java.io.PrintStream", "java.util.Formatter");

    private CoreRules() {}

    static ArchRule dependencies(String context, Path output, boolean test) {
        var ownCore = OnionRules.inContext(context).and(CORE);
        var java = resideInAnyPackage("java..").and(not(resideInAnyPackage("java.net..", "java.sql..")));
        var allowed = ownCore.or(java).or(describe("primitive types", JavaClass::isPrimitive));
        if (test) allowed = allowed.or(TEST_LIBRARIES);
        return classes().that(ownCore.and(OnionRules.fromOutput(output))).should()
                .onlyDependOnClassesThat(OnionRules.componentType(allowed)).allowEmptyShould(true)
                .as(context + (test ? " core tests" : " core classes") + " use only permitted core dependencies");
    }

    static ArchRule telemetry() {
        return noClasses().that(OUTSIDE_OBSERVABILITY).should().dependOnClassesThat(TELEMETRY)
                .allowEmptyShould(true).as("Metrics, tracing and structured events belong in the observability adapter");
    }

    static ArchRule businessLogging() {
        return noClasses().that(CORE).should(dependOnClassesThat(LOGGERS)
                        .or(accessTargetWhere(target(declaredIn(System.class).and(name("getLogger"))))))
                .allowEmptyShould(true).as("Domain and application code are independent of logging");
    }

    static ArchRule filesystemTypes(Path mainOutput) {
        var files = resideInAnyPackage("java.nio.file.spi..")
                .or(describe("file-backed JDK types", t -> FILE_TYPES.contains(t.getName())));
        return noClasses().that(CORE.and(OnionRules.fromOutput(mainOutput))).should().dependOnClassesThat(files)
                .allowEmptyShould(true).as("Production business code keeps filesystem dependencies behind ports");
    }

    static ArchRule filesystemCalls(Path mainOutput) {
        return noClasses().that(CORE.and(OnionRules.fromOutput(mainOutput))).should()
                .accessTargetWhere(describe("a filesystem operation", CoreRules::accessesFile))
                .allowEmptyShould(true).as("Production business code cannot open files through general-purpose APIs");
    }

    private static boolean accessesFile(JavaAccess<?> access) {
        if (!(access.getTarget() instanceof CodeUnitAccessTarget target)) return false;
        if (target.getOwner().isEquivalentTo(Path.class)) {
            return Set.of("toRealPath", "register").contains(target.getName());
        }
        if (!target.getName().equals("<init>") || target.getRawParameterTypes().isEmpty()) return false;
        var input = target.getRawParameterTypes().getFirst();
        if (target.getOwner().isEquivalentTo(Scanner.class)) return input.isEquivalentTo(Path.class);
        return FILENAME_CONSTRUCTORS.contains(target.getOwner().getName()) && input.isEquivalentTo(String.class);
    }
}
