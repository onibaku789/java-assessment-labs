package study.locker.reservation.integration;

import static org.assertj.core.api.Assertions.*;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import study.locker.reservation.application.*;

import java.util.*;

class ReservationQueryIT extends ReservationFixture {
    @Autowired
    TransactionTemplate tx;
    @Autowired
    ReservationStore store;

    public static class SqlCapture implements StatementInspector {
        private static final ScopedValue<List<String>> statements = ScopedValue.newInstance();

        @Override
        public String inspect(String sql) {
            if (statements.isBound()) statements.get().add(sql);
            return sql;
        }
    }

    @Test
    void scopedAvailabilityUsesFewerBuffersWithTheActiveCompartmentIndex() {
        reserve();
        db.sql("""
                insert into booking(id,compartment,owner_sub,email,state)
                select gen_random_uuid(),'historical-' || n,'fixture','a@example.test','COLLECTED'
                from generate_series(1,40000) n
                """).update();
        db.sql("analyze booking").update();
        tx.executeWithoutResult(transaction -> {
            var ids = List.of("CENTRAL-S1", "CENTRAL-S2");
            var statements = new ArrayList<String>();
            ScopedValue.where(SqlCapture.statements, statements)
                    .run(() -> assertThat(store.occupied(ids)).containsExactly("CENTRAL-S1"));
            assertThat(statements).hasSize(1);
            var sql = statements.getFirst();
            var indexed = plan(sql, ids);
            db.sql("set local enable_indexscan=off").update();
            db.sql("set local enable_indexonlyscan=off").update();
            db.sql("set local enable_bitmapscan=off").update();
            var scanned = plan(sql, ids);
            assertThat(store.occupied(ids)).containsExactly("CENTRAL-S1");
            assertThat(indexed.toString()).contains("one_active_booking");
            assertThat(scanned.toString()).contains("Seq Scan");
            assertThat(buffers(indexed)).isLessThan(buffers(scanned));
        });
    }

    private tools.jackson.databind.JsonNode plan(String sql, List<String> ids) {
        return json.readTree(
                        db.sql("explain (analyze, buffers, format json) " + sql).params(ids).query(String.class).single())
                .get(0).get("Plan");
    }

    private static int buffers(tools.jackson.databind.JsonNode plan) {
        return plan.path("Shared Hit Blocks").asInt() + plan.path("Shared Read Blocks").asInt();
    }
}
