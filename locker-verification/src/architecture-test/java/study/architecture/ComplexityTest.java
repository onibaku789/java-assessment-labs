package study.architecture;

import com.puppycrawl.tools.checkstyle.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexityTest {
    @Test
    void handwrittenJavaMeetsComplexityLimits() throws Exception {
        Path module = Path.of(ComplexityTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent().getParent();
        var files = new ArrayList<File>();
        for (String root : List.of("src/main/java", "src/test/java", "src/architecture-test/java")) {
            Path directory = module.resolve(root);
            if (Files.isDirectory(directory)) {
                try (var paths = Files.walk(directory)) {
                    files.addAll(paths.filter(p -> p.toString().endsWith(".java")).sorted().map(Path::toFile).toList());
                }
            }
        }
        assertThat(files).as("handwritten sources in %s", module).isNotEmpty();
        var result = audit(module.getParent(), files);
        assertThat(result.errors()).as("%s", result.log()).isZero();
    }

    record Result(int errors, String log) {
    }

    static Result audit(Path root, List<File> files) throws Exception {
        var rules = ConfigurationLoader.loadConfiguration(root.resolve("config/checkstyle/complexity.xml").toString(),
                new PropertiesExpander(new Properties()));
        var checker = new Checker();
        var output = new ByteArrayOutputStream();
        try {
            checker.setModuleClassLoader(ComplexityTest.class.getClassLoader());
            checker.setBasedir(root.toString());
            checker.configure(rules);
            checker.addListener(new DefaultLogger(output, AbstractAutomaticBean.OutputStreamOptions.NONE));
            int errors = checker.process(files);
            return new Result(errors, output.toString(StandardCharsets.UTF_8));
        } finally {
            checker.destroy();
        }
    }
}
