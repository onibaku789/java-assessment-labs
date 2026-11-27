# Java responsibility audit

Snapshot of the handwritten Java on 27 November 2026, after the complexity refactor. The build enforces the [Checkstyle rules](../../config/checkstyle/complexity.xml). The responsibility counts below are judgments from source review.

| Module | Files | Named types | Anonymous types | Methods | Explicit constructors | Types with multiple reasons | Members with multiple reasons |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| [locker-contracts](locker-contracts.md) | 6 | 7 | 0 | 30 | 6 | 7 | 11 |
| [locker-delivery](locker-delivery.md) | 13 | 16 | 1 | 28 | 3 | 5 | 4 |
| [locker-directory-service](locker-directory-service.md) | 8 | 10 | 0 | 13 | 3 | 0 | 0 |
| [locker-edge](locker-edge.md) | 11 | 15 | 2 | 39 | 6 | 6 | 10 |
| [locker-graphql-api](locker-graphql-api.md) | 2 | 2 | 0 | 9 | 1 | 1 | 0 |
| [locker-rest-api](locker-rest-api.md) | 3 | 5 | 0 | 9 | 1 | 0 | 0 |
| [locker-security](locker-security.md) | 4 | 5 | 1 | 7 | 2 | 1 | 1 |
| [locker-soap-api](locker-soap-api.md) | 4 | 4 | 0 | 15 | 2 | 1 | 3 |
| [locker-verification](locker-verification.md) | 12 | 16 | 1 | 90 | 2 | 9 | 17 |
| [notification-worker](notification-worker.md) | 11 | 14 | 0 | 26 | 1 | 4 | 1 |
| [receipt-worker](receipt-worker.md) | 10 | 14 | 0 | 21 | 1 | 4 | 3 |
| [reservation-service](reservation-service.md) | 59 | 80 | 4 | 180 | 13 | 21 | 18 |
| **Total** | **143** | **188** | **9** | **467** | **41** | **59** | **68** |

## Reading a count

One reason is one requirement or contract that could change independently. For example, changing which fields belong in a pickup-mail message and changing how an outbox row is persisted are independent decisions in `ReservationEventWriter`. Its two topic contracts and its storage contract give the class three reasons. Its private appender owns one. [Reservation service review](reservation-service.md#reservationeventwriter)

Type counts deduplicate the decisions owned by that type. A method can delegate part of a workflow without owning the delegate’s policy. Related steps, accessors and overloads can therefore share one reason. Empty private utility constructors are listed with zero. A multi-reason finding is a prompt for review. Each affected type and member has a concrete refactoring suggestion.

The inventory uses the Java 25 compiler’s parsed source trees across production, tests and shared architecture tests. It includes interfaces, records, enums, annotations, anonymous classes, abstract methods and explicitly declared constructors. Lambdas and initialization blocks contribute to the enclosing declaration. Compiler-synthesized members and generated protocol sources are outside the inventory. Overloads have separate signatures. Each parsed declaration corresponds to one source-linked row.

Before the refactor, the inventory contained 118 files, 161 named types, 9 anonymous types, 428 methods and 39 explicit constructors. Checkstyle reported 12 cyclomatic, 9 fan-out and 4 statement-count violations. After the refactor all three checks passed. This snapshot can be refreshed when ownership or contracts change.
