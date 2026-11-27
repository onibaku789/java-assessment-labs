package study.locker.graphql.adapter.graphql;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;

import org.springframework.boot.*;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
    List<Slot> available(
            JwtAuthenticationToken actor, @Argument String site, @Argument String size) {
        return gateway.available(actor.getToken().getTokenValue(), site, size);
    }

    @QueryMapping
    Reservation reservation(JwtAuthenticationToken actor, @Argument String id) {
        return gateway.get(actor.getToken().getTokenValue(), id);
    }

    @MutationMapping
    Reservation reserve(
            JwtAuthenticationToken actor,
            @Argument String requestId,
            @Argument String compartmentId) {
        return gateway.command(
                actor.getToken().getTokenValue(), Action.RESERVE, requestId, compartmentId, null);
    }

    @MutationMapping
    Reservation cancel(
            JwtAuthenticationToken actor, @Argument String requestId, @Argument String id) {
        return gateway.command(
                actor.getToken().getTokenValue(), Action.CANCEL, requestId, id, null);
    }

    @MutationMapping
    Reservation deposit(
            JwtAuthenticationToken actor, @Argument String requestId, @Argument String id) {
        return gateway.command(
                actor.getToken().getTokenValue(), Action.DEPOSIT, requestId, id, null);
    }

    @MutationMapping
    Reservation reissue(
            JwtAuthenticationToken actor, @Argument String requestId, @Argument String id) {
        return gateway.command(
                actor.getToken().getTokenValue(), Action.REISSUE, requestId, id, null);
    }

    @MutationMapping
    Reservation collect(
            JwtAuthenticationToken actor,
            @Argument String requestId,
            @Argument String id,
            @Argument String code) {
        return gateway.command(
                actor.getToken().getTokenValue(), Action.COLLECT, requestId, id, code);
    }

    @GraphQlExceptionHandler
    GraphQLError failure(GatewayFailure ex, DataFetchingEnvironment env) {
        String code = ex.code();
        return GraphqlErrorBuilder.newError(env)
                .message(Objects.toString(ex.getMessage(), code))
                .extensions(Map.of("code", code))
                .build();
    }
}
