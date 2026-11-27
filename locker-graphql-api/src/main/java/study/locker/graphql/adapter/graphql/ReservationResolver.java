package study.locker.graphql.adapter.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Controller;

import study.locker.edge.application.*;
import study.locker.edge.application.LockerOperations.*;

import java.util.*;

@Controller
public class ReservationResolver {
    private final LockerOperations gateway;

    public ReservationResolver(LockerOperations gateway) {
        this.gateway = gateway;
    }

    @QueryMapping
    List<Slot> available(@Argument String site, @Argument String size) {
        return gateway.available(site, size);
    }

    @QueryMapping
    Reservation reservation(@Argument String id) {
        return gateway.get(id);
    }

    @MutationMapping
    Reservation reserve(@Argument String requestId, @Argument String compartmentId) {
        return gateway.reserve(requestId, compartmentId);
    }

    @MutationMapping
    Reservation cancel(@Argument String requestId, @Argument String id) {
        return gateway.cancel(requestId, id);
    }

    @MutationMapping
    Reservation deposit(@Argument String requestId, @Argument String id) {
        return gateway.deposit(requestId, id);
    }

    @MutationMapping
    Reservation reissue(@Argument String requestId, @Argument String id) {
        return gateway.reissue(requestId, id);
    }

    @MutationMapping
    Reservation collect(@Argument String requestId, @Argument String id, @Argument String code) {
        return gateway.collect(requestId, id, code);
    }

    @GraphQlExceptionHandler
    GraphQLError failure(GatewayFailure ex, DataFetchingEnvironment env) {
        String code = ex.code();
        return GraphqlErrorBuilder.newError(env).message(Objects.toString(ex.getMessage(), code))
                .extensions(Map.of("code", code)).build();
    }
}
