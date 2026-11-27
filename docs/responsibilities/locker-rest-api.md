# locker-rest-api responsibility review

[All modules and counting conventions](README.md)

3 Java files. 5 types, 9 methods and 1 explicit constructors. 0 types and 0 members have multiple reasons to change.

## RestApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class RestApp`](../../locker-rest-api/src/main/java/study/locker/rest/RestApp.java#L9) | 1 | Spring Boot startup of the REST gateway. |
| [`main(String[])`](../../locker-rest-api/src/main/java/study/locker/rest/RestApp.java#L12) | 1 | Spring Boot startup of the REST gateway. |

## ReservationController

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationController`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L14) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`ReservationController(LockerOperations)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L19) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`available(Jwt, String, String)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L29) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`get(Jwt, String)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L35) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`reserve(Jwt, String, Create)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L40) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`cancel(Jwt, String, String)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L47) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`deposit(Jwt, String, String)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L53) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`reissue(Jwt, String, String)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L59) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |
| [`collect(Jwt, String, String, Pickup)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L65) | 1 | HTTP routes, request binding, success codes and resource representations for reservations. |

## ReservationController.Create

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Create`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L23) | 1 | JSON input accepted by the reservation creation route. |

## ReservationController.Pickup

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Pickup`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationController.java#L26) | 1 | JSON code input accepted by the collection route. |

## ReservationErrors

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReservationErrors`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationErrors.java#L9) | 1 | HTTP status, retry header and public error body for a gateway failure. |
| [`failure(GatewayFailure)`](../../locker-rest-api/src/main/java/study/locker/rest/adapter/rest/ReservationErrors.java#L15) | 1 | HTTP status, retry header and public error body for a gateway failure. Selects the HTTP status, conditionally emits Retry-After and writes the public error body. |
