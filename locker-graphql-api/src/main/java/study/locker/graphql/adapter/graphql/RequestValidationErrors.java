package study.locker.graphql.adapter.graphql;

import graphql.ErrorType;
import graphql.GraphQLError;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
final class RequestValidationErrors implements WebGraphQlInterceptor {
    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        return chain.next(request).map(response -> {
            var result = response.getExecutionResult();
            if (result.isDataPresent()) return response;
            var errors = result.getErrors().stream().map(RequestValidationErrors::withApplicationCode).toList();
            return response.transform(builder -> builder.errors(errors));
        });
    }

    private static GraphQLError withApplicationCode(GraphQLError error) {
        if (error.getErrorType() != ErrorType.ValidationError) return error;
        Map<String, Object> extensions = new LinkedHashMap<>();
        if (error.getExtensions() != null) extensions.putAll(error.getExtensions());
        extensions.putIfAbsent("code", "INVALID_ARGUMENT");
        return GraphQLError.newError().message("%s", error.getMessage()).errorType(error.getErrorType())
                .locations(error.getLocations()).path(error.getPath()).extensions(extensions).build();
    }
}
