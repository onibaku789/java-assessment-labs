package study.locker.graphql.adapter.graphql;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.Coercing;
import graphql.schema.idl.RuntimeWiring;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Validate with the UUID scalar, retaining the original command text for idempotency fingerprints. */
@Component
final class UuidInput implements RuntimeWiringConfigurer, Coercing<String, String> {
    private static final Coercing<?, ?> UUID = ExtendedScalars.UUID.getCoercing();

    @Override
    public void configure(RuntimeWiring.Builder wiring) {
        wiring.scalar(ExtendedScalars.UUID.transform(type -> type.coercing(this)));
    }

    @Override
    public String parseValue(Object input, GraphQLContext context, Locale locale) {
        UUID.parseValue(input, context, locale);
        return input.toString();
    }

    @Override
    public String parseLiteral(Value<?> input, CoercedVariables variables, GraphQLContext context, Locale locale) {
        UUID.parseLiteral(input, variables, context, locale);
        return ((StringValue) input).getValue();
    }

    @Override
    public String serialize(Object value, GraphQLContext context, Locale locale) {
        return UUID.serialize(value, context, locale).toString();
    }

    @Override
    public Value<?> valueToLiteral(Object input, GraphQLContext context, Locale locale) {
        return StringValue.of(parseValue(input, context, locale));
    }
}
