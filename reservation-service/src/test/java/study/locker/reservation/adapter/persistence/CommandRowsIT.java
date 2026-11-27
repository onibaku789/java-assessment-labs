package study.locker.reservation.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

@Testcontainers
@SpringBootTest(
        classes = CommandRowsIT.PersistenceConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CommandRowsIT {
    @org.springframework.boot.test.context.TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackageClasses = CommandRow.class)
    static class PersistenceConfig {
    }

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:19.0-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    CommandRows rows;
    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void assignedKeyEntryIsNewUntilInsertedAndIsExistingWhenReloaded() {
        var key = new CommandRow.Key("test-customer", UUID.randomUUID());
        byte[] snapshot = {1, 2, 3};
        var entry = new CommandRow(key, "signature", snapshot, null);
        assertThat(entry.getId()).isEqualTo(key);
        assertThat(entry.isNew()).isTrue();
        assertThat(rows.save(entry)).isSameAs(entry);
        entityManager.flush();
        assertThat(entry.isNew()).isFalse();

        entityManager.clear();
        var replay = rows.findById(key).orElseThrow();
        assertThat(replay).isNotSameAs(entry);
        assertThat(replay.isNew()).isFalse();
        assertThat(replay.result).containsExactly(snapshot);
    }
}
