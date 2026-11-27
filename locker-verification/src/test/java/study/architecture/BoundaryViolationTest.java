package study.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

class BoundaryViolationTest {
    @Test
    void rejectsAnAdapterAndJdbcDependencyInDomainTestBytecode(@TempDir Path root)
            throws Exception {
        Path repo = root.resolve("Repository.java"),
                test = root.resolve("BadDomainTest.java"),
                other = root.resolve("HiddenFixture.java"),
                out = root.resolve("test-classes");
        Files.createDirectory(out);
        Files.writeString(
                repo,
                "package study.locker.reservation.adapter.persistence; public interface Repository"
                        + " {}");
        Files.writeString(
                test,
                "package study.locker.reservation.domain.model; class BadDomainTest {"
                        + " study.locker.reservation.adapter.persistence.Repository repo;"
                        + " java.sql.Connection sql; }");
        Files.writeString(
                other, "package study.locker.reservation.testsupport; class HiddenFixture {}");
        assertThat(
                        ToolProvider.getSystemJavaCompiler()
                                .run(
                                        null,
                                        null,
                                        null,
                                        "-d",
                                        out.toString(),
                                        repo.toString(),
                                        test.toString(),
                                        other.toString()))
                .isZero();
        var fixture = new ClassFileImporter().importPath(out);
        var onion = OnionRules.onion().evaluate(fixture).getFailureReport().toString();
        assertThat(onion).contains("BadDomainTest", "Repository", "HiddenFixture");
        assertThat(OnionRules.boundaries().evaluate(fixture).getFailureReport().toString())
                .contains("BadDomainTest", "java.sql.Connection");
    }

    @ParameterizedTest
    @ValueSource(strings = {"classes", "test-classes"})
    void rejectsSecurityAndTelemetryInBusinessCode(String output, @TempDir Path root)
            throws Exception {
        var sources =
                Map.of(
                        "Authentication",
                                "package org.springframework.security.core; public interface"
                                    + " Authentication {}",
                        "MeterRegistry",
                                "package io.micrometer.core.instrument; public interface"
                                    + " MeterRegistry {}",
                        "Logger", "package org.slf4j; public interface Logger {}",
                        "BadRule",
                                "package study.locker.reservation.domain.model; class BadRule {"
                                    + " org.springframework.security.core.Authentication actor; }",
                        "BadUseCase",
                                "package study.locker.reservation.application; class BadUseCase {"
                                    + " org.slf4j.Logger log;"
                                    + " io.micrometer.core.instrument.MeterRegistry metrics; }",
                        "BadTransport",
                                "package study.locker.reservation.adapter.grpcserver; class"
                                    + " BadTransport { io.micrometer.core.instrument.MeterRegistry"
                                    + " metrics; }");
        Path classes = Files.createDirectory(root.resolve(output));
        var args = new ArrayList<>(List.of("-d", classes.toString()));
        for (var source : sources.entrySet()) {
            args.add(
                    Files.writeString(root.resolve(source.getKey() + ".java"), source.getValue())
                            .toString());
        }
        assertThat(
                        ToolProvider.getSystemJavaCompiler()
                                .run(null, null, null, args.toArray(String[]::new)))
                .isZero();
        var report =
                OnionRules.boundaries()
                        .evaluate(new ClassFileImporter().importPath(classes))
                        .getFailureReport()
                        .getDetails();
        assertThat(report)
                .anyMatch(
                        line ->
                                line.contains("BadRule")
                                        && line.contains(
                                                "org.springframework.security.core.Authentication"))
                .anyMatch(line -> line.contains("BadUseCase") && line.contains("org.slf4j.Logger"))
                .anyMatch(
                        line ->
                                line.contains("BadUseCase")
                                        && line.contains(
                                                "io.micrometer.core.instrument.MeterRegistry"))
                .anyMatch(
                        line ->
                                line.contains("BadTransport")
                                        && line.contains(
                                                "io.micrometer.core.instrument.MeterRegistry"));
    }
}
