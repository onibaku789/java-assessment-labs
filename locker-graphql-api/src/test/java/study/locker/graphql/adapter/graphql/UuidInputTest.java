package study.locker.graphql.adapter.graphql;

import graphql.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import study.locker.edge.application.LockerOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@GraphQlTest
@ContextConfiguration(classes = {ReservationResolver.class, UuidInput.class})
class UuidInputTest {
    private static final String ID = "2e5771eD-aC25-4c1a-8b9e-1abcd0123456";
    private static final String DEPOSIT = """
            mutation($id:UUID!, $key:UUID!) { deposit(id:$id, requestId:$key) { id } }
            """;

    @Autowired GraphQlTester graph;
    @MockitoBean LockerOperations gateway;

    @ParameterizedTest
    @ValueSource(strings = {"id", "key"})
    void invalidVariableStopsBeforeGateway(String invalidVariable) {
        var response = graph.document(DEPOSIT)
                .variable("id", invalidVariable.equals("id") ? "yxc" : ID)
                .variable("key", invalidVariable.equals("key") ? "yxc" : ID).execute();
        assertValidationError(response);
    }

    @Test
    void invalidLiteralStopsBeforeGateway() {
        assertValidationError(graph.document("{ reservation(id:\"yxc\") { id } }").execute());
    }

    private void assertValidationError(GraphQlTester.Response response) {
        response.errors().satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error.getErrorType()).isEqualTo(ErrorType.ValidationError);
            assertThat(error.getMessage()).contains("UUID");
        }));
        verifyNoInteractions(gateway);
    }

    @Test
    void literalAndVariableKeepTheSubmittedSpelling() {
        when(gateway.get(ID)).thenReturn(new LockerOperations.Reservation(ID, "CENTRAL-S1", "RESERVED"));
        var variable = graph.document("query($id:UUID!){reservation(id:$id){id}}").variable("id", ID);
        var literal = graph.document("{reservation(id:\"" + ID + "\"){id}}");
        assertThat(variable.execute().path("reservation.id").entity(String.class).get()).isEqualTo(ID);
        assertThat(literal.execute().path("reservation.id").entity(String.class).get()).isEqualTo(ID);
    }
}
