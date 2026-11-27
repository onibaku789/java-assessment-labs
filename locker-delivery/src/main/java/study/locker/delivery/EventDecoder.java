package study.locker.delivery;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.converter.MessagingMessageConverter;

import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.exc.MismatchedInputException;

import java.lang.reflect.Type;

public class EventDecoder extends MessagingMessageConverter {
    private final ObjectMapper json;

    public EventDecoder(ObjectMapper json) {
        this.json =
                json.rebuild()
                        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .withCoercionConfig(
                                String.class,
                                config -> {
                                    config.setCoercion(
                                            CoercionInputShape.Integer, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.Float, CoercionAction.Fail);
                                    config.setCoercion(
                                            CoercionInputShape.Boolean, CoercionAction.Fail);
                                })
                        .build();
    }

    @Override
    protected Object extractAndConvertValue(ConsumerRecord<?, ?> record, Type type) {
        if (record.value() instanceof String payload && type instanceof Class<?> eventType) {
            return decode(payload, eventType);
        }
        return super.extractAndConvertValue(record, type);
    }

    public <T> T decode(String payload, Class<T> type) {
        // Finish parsing before classifying field errors. Syntax errors use the existing recovery
        // path.
        var node = json.readTree(payload);
        if (node == null || node.isMissingNode())
            throw new IllegalArgumentException("Empty event payload");
        if (!node.isObject()) throw new InvalidEventFields("payload");
        try {
            return json.treeToValue(node, type);
        } catch (MismatchedInputException | InputCoercionException invalid) {
            String field =
                    invalid.getPath().isEmpty()
                            ? null
                            : invalid.getPath().getFirst().getPropertyName();
            throw new InvalidEventFields(field == null ? "payload" : field);
        }
    }
}
