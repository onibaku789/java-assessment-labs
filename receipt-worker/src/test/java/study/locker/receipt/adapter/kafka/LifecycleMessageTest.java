package study.locker.receipt.adapter.kafka;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import study.locker.delivery.EventDecoder;
import study.locker.delivery.InvalidEventFields;

import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.util.*;

class LifecycleMessageTest {
    @Test
    void rejectsWrongJsonTypesBeforeListenerValidation() {

        var json = new ObjectMapper();
        var decoder = new EventDecoder(json);
        Map<String, Object> fields =
                new HashMap<>(
                        Map.of(
                                "eventId",
                                UUID.randomUUID().toString(),
                                "reservationId",
                                UUID.randomUUID().toString(),
                                "compartmentId",
                                "Central",
                                "type",
                                "RESERVED",
                                "occurredAt",
                                "2026-01-01T01:00:00.000+01:00"));
        for (Object version : List.of(1.5, "1", new BigInteger("900000000000000000000000"))) {
            fields.put("version", version);
            assertThatThrownBy(
                            () ->
                                    decoder.decode(
                                            json.writeValueAsString(fields),
                                            LifecycleMessage.class))
                    .as("version %s", version)
                    .isInstanceOf(InvalidEventFields.class)
                    .hasMessage("Invalid event fields: version");
        }
        fields.put("version", 1);
        fields.put("compartmentId", 42);
        assertThatThrownBy(
                        () ->
                                decoder.decode(
                                        json.writeValueAsString(fields), LifecycleMessage.class))
                .hasMessage("Invalid event fields: compartmentId")
                .hasNoCause();
    }
}
