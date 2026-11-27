package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

@Testcontainers
class ManualAuditIT {
    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:19.0-alpine")
                    .withEnv("LAB_READER_PASSWORD", "reader-fixture")
                    .withEnv("LAB_RETENTION_HOURS", "24");

    @Test
    void existingDatabaseCanInstallTwiceAndOnlyCommittedSafeRowsBecomeVisible() throws Exception {
        Path repo = Path.of(System.getProperty("basedir", ".")).toAbsolutePath().normalize();
        if (!repo.resolve("deploy/manual").toFile().isDirectory()) repo = repo.getParent();
        copy(repo, "locker-directory-service/src/main/resources/db/changelog.sql", "directory.sql");
        copy(repo, "reservation-service/src/main/resources/db/changelog.sql", "reservations.sql");
        copy(repo, "deploy/manual/audit/install.sql", "audit.sql");
        var setup =
                psql(
                        "-c",
                        "create schema directory; create schema reservations; set search_path to"
                                + " directory",
                        "-f",
                        "/tmp/directory.sql",
                        "-c",
                        "set search_path to reservations",
                        "-f",
                        "/tmp/reservations.sql",
                        "-f",
                        "/tmp/audit.sql",
                        "-f",
                        "/tmp/audit.sql");
        assertThat(setup.getExitCode()).as(setup.getStderr()).isZero();

        var rollback =
                psql(
                        "-c",
                        """
                        begin;
                        select set_config('locker.trace_parent','00-11111111111111111111111111111111-1111111111111111-01',true);
                        insert into reservations.booking (id,compartment,owner_sub,email,state)
                            values (gen_random_uuid(),'A','private-owner','private@example.test','RESERVED');
                        rollback;
                        """,
                        "-c",
                        "select count(*) from lab_inspect.changes");
        assertThat(rollback.getExitCode()).as(rollback.getStderr()).isZero();
        assertThat(rollback.getStdout().trim()).endsWith("0");

        var commit =
                psql(
                        "-c",
                        """
                        insert into reservations.booking (id,compartment,owner_sub,email,state)
                            values (gen_random_uuid(),'A','private-owner','private@example.test','RESERVED');
                        update reservations.booking set state='CANCELLED' where compartment='A';
                        """,
                        "-c",
                        "set role lab_reader",
                        "-c",
                        "select before_row,after_row from lab_inspect.changes order by id");
        assertThat(commit.getExitCode()).as(commit.getStderr()).isZero();
        assertThat(commit.getStdout())
                .contains("RESERVED", "CANCELLED")
                .doesNotContain("private-owner", "private@example.test", "verifier");
        var retention =
                psql(
                        "-c",
                        """
                        update lab_inspect.row_change set at=now() - interval '25 hours' where action='INSERT';
                        select count(*) from lab_inspect.changes;
                        """);
        assertThat(retention.getExitCode()).as(retention.getStderr()).isZero();
        assertThat(retention.getStdout().trim()).endsWith("1");
        var cleanup =
                psql(
                        "-c",
                        """
                        update reservations.booking set state='RESERVED' where compartment='A';
                        select count(*) from lab_inspect.row_change where at < now() - interval '24 hours';
                        """);
        assertThat(cleanup.getExitCode()).as(cleanup.getStderr()).isZero();
        assertThat(cleanup.getStdout().trim()).endsWith("0");
        var forbidden =
                psql("-c", "set role lab_reader", "-c", "select email from reservations.booking");
        assertThat(forbidden.getExitCode()).isNotZero();
    }

    private static void copy(Path repo, String source, String target) {
        postgres.copyFileToContainer(
                MountableFile.forHostPath(repo.resolve(source)), "/tmp/" + target);
    }

    private static ExecResult psql(String... args) throws Exception {
        var command =
                new java.util.ArrayList<>(
                        java.util.List.of(
                                "psql",
                                "-X",
                                "-At",
                                "-v",
                                "ON_ERROR_STOP=1",
                                "-U",
                                postgres.getUsername(),
                                "-d",
                                postgres.getDatabaseName()));
        command.addAll(java.util.List.of(args));
        return postgres.execInContainer(command.toArray(String[]::new));
    }
}
