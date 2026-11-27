# locker-security responsibility review

[All modules and counting conventions](README.md)

4 Java files. 6 types, 7 methods and 2 explicit constructors. 1 types and 1 members have multiple reasons to change.

## JwtAuthentication

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class JwtAuthentication`](../../locker-security/src/main/java/study/locker/security/JwtAuthentication.java#L10) | 1 | Conversion of validated JWT subject, verified email and scopes into Spring authentication. |
| [`JwtAuthentication()`](../../locker-security/src/main/java/study/locker/security/JwtAuthentication.java#L11) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |
| [`from(Jwt)`](../../locker-security/src/main/java/study/locker/security/JwtAuthentication.java#L14) | 1 | Conversion of validated JWT subject, verified email and scopes into Spring authentication. |

## LockerJwt

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LockerJwt`](../../locker-security/src/main/java/study/locker/security/LockerJwt.java#L13) | 1 | Accepted JWT signature, issuer, audience and mandatory identity claims. |
| [`lockerDecoder(String, String, String)`](../../locker-security/src/main/java/study/locker/security/LockerJwt.java#L15) | 1 | Accepted JWT signature, issuer, audience and mandatory identity claims. |

## RpcIdentity

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class RpcIdentity`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L12) | 3 | gRPC bearer-token authentication · Binding and restoring Spring Security identity for one call · Mapping authorization denial to public gRPC status. |
| [`RpcIdentity(JwtDecoder)`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L17) | 1 | gRPC bearer-token authentication. |
| [`asAuthenticated(Supplier<T>)`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L29) | 2 | Binding and restoring Spring Security identity for one call · Mapping authorization denial to public gRPC status. Restores the prior thread identity even when the operation is denied. **Suggested change.** Separate temporary context binding from the mapping of a denied authorization result. |
| [`interceptCall(ServerCall<Q, R>, Metadata, ServerCallHandler<Q, R>)`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L48) | 1 | gRPC bearer-token authentication. Validates the bearer token, installs it for the call and closes unauthenticated calls. |

**Refactor to consider.** Move scoped Spring context execution and its denial translation out of the gRPC token interceptor.

## RpcIdentity.Identity

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Identity`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L21) | 1 | Authenticated principal fields available to reservation authorization. |
| [`getName()`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L22) | 1 | Authenticated principal fields available to reservation authorization. |

## RpcIdentity.anonymous@61

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@61`](../../locker-security/src/main/java/study/locker/security/RpcIdentity.java#L61) | 1 | Terminal gRPC listener after an unauthenticated call has been closed. |

## ScopeDecision

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record ScopeDecision`](../../locker-security/src/main/java/study/locker/security/ScopeDecision.java#L6) | 1 | Representation and evaluation of a required locker scope. |
| [`require(Authentication, String)`](../../locker-security/src/main/java/study/locker/security/ScopeDecision.java#L7) | 1 | Representation and evaluation of a required locker scope. |
| [`isGranted()`](../../locker-security/src/main/java/study/locker/security/ScopeDecision.java#L15) | 1 | Representation and evaluation of a required locker scope. |
