# locker-graphql-api responsibility review

[All modules and counting conventions](README.md)

2 Java files. 2 types, 9 methods and 1 explicit constructors. 1 types and 0 members have multiple reasons to change.

## GraphqlApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class GraphqlApp`](../../locker-graphql-api/src/main/java/study/locker/graphql/GraphqlApp.java#L9) | 1 | Spring Boot startup of the GraphQL gateway. |
| [`main(String[])`](../../locker-graphql-api/src/main/java/study/locker/graphql/GraphqlApp.java#L12) | 1 | Spring Boot startup of the GraphQL gateway. |

## ReservationResolver

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationResolver`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L17) | 2 | GraphQL query and mutation argument/result schema · Mapping gateway failures to GraphQL error extensions. |
| [`ReservationResolver(LockerOperations)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L21) | 1 | GraphQL query and mutation argument/result schema. |
| [`available(JwtAuthenticationToken, String, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L25) | 1 | GraphQL query and mutation argument/result schema. |
| [`reservation(JwtAuthenticationToken, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L31) | 1 | GraphQL query and mutation argument/result schema. |
| [`reserve(JwtAuthenticationToken, String, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L36) | 1 | GraphQL query and mutation argument/result schema. |
| [`cancel(JwtAuthenticationToken, String, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L45) | 1 | GraphQL query and mutation argument/result schema. |
| [`deposit(JwtAuthenticationToken, String, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L52) | 1 | GraphQL query and mutation argument/result schema. |
| [`reissue(JwtAuthenticationToken, String, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L59) | 1 | GraphQL query and mutation argument/result schema. |
| [`collect(JwtAuthenticationToken, String, String, String)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L66) | 1 | GraphQL query and mutation argument/result schema. |
| [`failure(GatewayFailure, DataFetchingEnvironment)`](../../locker-graphql-api/src/main/java/study/locker/graphql/adapter/graphql/ReservationResolver.java#L76) | 1 | Mapping gateway failures to GraphQL error extensions. Converts the gateway rejection into a GraphQL error visible to clients. |

**Refactor to consider.** Move error mapping to a GraphQL exception resolver when the fault contract changes.
