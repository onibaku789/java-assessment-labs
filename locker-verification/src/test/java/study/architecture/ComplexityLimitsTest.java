package study.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexityLimitsTest {
    @TempDir
    Path source;

    @Test
    void cyclomaticLimit() throws Exception {
        boundary("CyclomaticComplexity", "void run(boolean ok) { %s }", "if (ok) {} ", 6);
    }

    @Test
    void couplingLimit() throws Exception {
        StringBuilder fields = new StringBuilder();
        for (int i = 1; i <= 15; i++) fields.append("Type").append(i).append(" field").append(i).append("; ");
        verify(fields.toString(), 0, "ClassFanOutComplexity");
        verify(fields + "Type16 field16;", 1, "ClassFanOutComplexity");
    }

    @Test
    void statementLimits() throws Exception {
        boundary("JavaNCSS", "void run() {int value=0; %s}", "value++; ", 28);
        boundary("JavaNCSS", "%s", "void m%d() {} ", 149);
    }

    private void boundary(String rule, String template, String unit, int atLimit) throws Exception {
        var body = new StringBuilder();
        for (int i = 0; i < atLimit; i++) body.append(unit.formatted(i));
        verify(template.formatted(body), 0, rule);
        verify(template.formatted(body.append(unit.formatted(atLimit))), 1, rule);
    }

    private void verify(String members, int expectedErrors, String rule) throws Exception {
        var file = Files.writeString(source.resolve("Sample.java"), "class Sample { " + members + " }");
        var root = Path.of(ComplexityLimitsTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getParent().getParent().getParent();
        var result = ComplexityTest.audit(root, List.of(file.toFile()));
        assertThat(result.errors()).as("%s", result.log()).isEqualTo(expectedErrors);
        if (expectedErrors > 0) assertThat(result.log()).contains(rule);
    }
}
