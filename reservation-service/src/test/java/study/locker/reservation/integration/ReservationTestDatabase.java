package study.locker.reservation.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
class ReservationTestDatabase {
    @Bean(initMethod = "start", destroyMethod = "stop")
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:19.0-alpine");
    }

    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgres) {
        return registry -> {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        };
    }

    static TransactionTemplate independentTransaction(TransactionTemplate tx) {
        var independent = new TransactionTemplate(tx.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent;
    }
}
