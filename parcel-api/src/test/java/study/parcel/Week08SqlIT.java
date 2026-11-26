package study.parcel;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@Disabled("Enable when starting Week 8")
class Week08SqlIT extends ParcelTestKeys {
    @Container static final GenericContainer<?> DB = new GenericContainer<>("postgres:19.0-alpine")
            .withEnv("POSTGRES_USER", "learner").withEnv("POSTGRES_PASSWORD", "study")
            .withEnv("POSTGRES_DB", "parcels").withExposedPorts(5432)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));
    @Autowired JdbcTemplate db;

    @DynamicPropertySource static void connection(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",
                () -> "jdbc:postgresql://" + DB.getHost() + ":" + DB.getMappedPort(5432) + "/parcels");
        properties.add("spring.datasource.username", () -> "learner");
        properties.add("spring.datasource.password", () -> "study");
    }

    private static final String SEARCH = "SELECT tracking_id FROM parcel WHERE destination = 'Bath' LIMIT 200";

    private int blocks() {
        String json = db.queryForObject("EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + SEARCH, String.class);
        var plan = new ObjectMapper().readTree(json).get(0).get("Plan");
        return plan.get("Shared Hit Blocks").asInt() + plan.get("Shared Read Blocks").asInt();
    }

    @Test @Transactional @Sql("file:fixtures/profile.sql")
    void lookupFindsTheSame100ParcelsWithFewerBuffersThanASequentialScan() {
        var ids = db.queryForList(SEARCH, String.class);
        assertThat(ids).hasSize(100).doesNotHaveDuplicates();
        int tuned = blocks();
        db.execute("SET LOCAL enable_indexscan = off");
        db.execute("SET LOCAL enable_indexonlyscan = off");
        db.execute("SET LOCAL enable_bitmapscan = off");
        int sequential = blocks();
        assertThat(db.queryForList(SEARCH, String.class)).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(tuned).as("Bath query shared buffers with your schema (%d) versus a forced sequential scan (%d). "
                + "Apply your index in the application's schema or migration so a fresh database uses it.", tuned, sequential)
                .isLessThan(sequential);
    }
}
