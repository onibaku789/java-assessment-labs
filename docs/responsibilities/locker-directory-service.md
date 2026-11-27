# locker-directory-service responsibility review

[All modules and counting conventions](README.md)

8 Java files. 10 types, 13 methods and 3 explicit constructors. 0 types and 0 members have multiple reasons to change.

## DirectoryApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DirectoryApp`](../../locker-directory-service/src/main/java/study/locker/directory/DirectoryApp.java#L6) | 1 | Spring Boot startup of the directory service. |
| [`main(String[])`](../../locker-directory-service/src/main/java/study/locker/directory/DirectoryApp.java#L8) | 1 | Spring Boot startup of the directory service. |

## DirectoryEndpoint

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DirectoryEndpoint`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/grpc/DirectoryEndpoint.java#L10) | 1 | Directory gRPC search and lookup contract, including protobuf representation and missing-compartment status. |
| [`DirectoryEndpoint(LockerDirectory)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/grpc/DirectoryEndpoint.java#L13) | 1 | Directory gRPC search and lookup contract, including protobuf representation and missing-compartment status. |
| [`searchCompartments(Search, StreamObserver<Compartments>)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/grpc/DirectoryEndpoint.java#L17) | 1 | Directory gRPC search and lookup contract, including protobuf representation and missing-compartment status. |
| [`getCompartment(Key, StreamObserver<Compartment>)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/grpc/DirectoryEndpoint.java#L30) | 1 | Directory gRPC search and lookup contract, including protobuf representation and missing-compartment status. |
| [`wire(study.locker.directory.domain.model.Compartment)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/grpc/DirectoryEndpoint.java#L45) | 1 | Directory gRPC search and lookup contract, including protobuf representation and missing-compartment status. |

## CompartmentRow

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class CompartmentRow`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/CompartmentRow.java#L7) | 1 | Mapping a database compartment into the catalog domain model. |
| [`CompartmentRow()`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/CompartmentRow.java#L10) | 1 | Mapping a database compartment into the catalog domain model. |
| [`toDomain()`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/CompartmentRow.java#L16) | 1 | Mapping a database compartment into the catalog domain model. |

## CompartmentRows

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface CompartmentRows`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/CompartmentRows.java#L8) | 1 | Catalog search query predicates and ordering. |
| [`search(String, String)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/CompartmentRows.java#L9) | 1 | Catalog search query predicates and ordering. |

## JpaCatalog

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class JpaCatalog`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/JpaCatalog.java#L10) | 1 | Adapting catalog searches and identity lookups to the JPA repository. |
| [`JpaCatalog(CompartmentRows)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/JpaCatalog.java#L14) | 1 | Adapting catalog searches and identity lookups to the JPA repository. |
| [`search(String, String)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/JpaCatalog.java#L18) | 1 | Adapting catalog searches and identity lookups to the JPA repository. |
| [`find(String)`](../../locker-directory-service/src/main/java/study/locker/directory/adapter/persistence/JpaCatalog.java#L22) | 1 | Adapting catalog searches and identity lookups to the JPA repository. |

## LockerDirectory

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record LockerDirectory`](../../locker-directory-service/src/main/java/study/locker/directory/application/LockerDirectory.java#L8) | 1 | Directory use-case behavior for listing and looking up compartments. |
| [`compartmentsAt(String, String)`](../../locker-directory-service/src/main/java/study/locker/directory/application/LockerDirectory.java#L15) | 1 | Directory use-case behavior for listing and looking up compartments. |
| [`find(String)`](../../locker-directory-service/src/main/java/study/locker/directory/application/LockerDirectory.java#L19) | 1 | Directory use-case behavior for listing and looking up compartments. |

## LockerDirectory.Catalog

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`interface Catalog`](../../locker-directory-service/src/main/java/study/locker/directory/application/LockerDirectory.java#L9) | 1 | Domain-facing catalog lookup contract. |
| [`search(String, String)`](../../locker-directory-service/src/main/java/study/locker/directory/application/LockerDirectory.java#L10) | 1 | Domain-facing catalog lookup contract. |
| [`find(String)`](../../locker-directory-service/src/main/java/study/locker/directory/application/LockerDirectory.java#L12) | 1 | Domain-facing catalog lookup contract. |

## DirectoryConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class DirectoryConfiguration`](../../locker-directory-service/src/main/java/study/locker/directory/bootstrap/DirectoryConfiguration.java#L14) | 1 | Binding the Directory gRPC listener to its configured port. |
| [`directoryServer(LockerDirectory.Catalog, int)`](../../locker-directory-service/src/main/java/study/locker/directory/bootstrap/DirectoryConfiguration.java#L16) | 1 | Binding the Directory gRPC listener to its configured port. |

## Compartment

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Compartment`](../../locker-directory-service/src/main/java/study/locker/directory/domain/model/Compartment.java#L3) | 1 | Identity, site and capacity category of a catalog compartment. |

## Compartment.Size

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Size`](../../locker-directory-service/src/main/java/study/locker/directory/domain/model/Compartment.java#L4) | 1 | Supported catalog size categories. |
