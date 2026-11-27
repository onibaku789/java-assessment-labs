# locker-verification responsibility review

[All modules and counting conventions](README.md)

12 Java files. 17 types, 90 methods and 2 explicit constructors. 9 types and 17 members have multiple reasons to change.

## ReceiptArchive

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ReceiptArchive`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L18) | 3 | CLI arguments and reservation-prefix selection · On-disk manifest format and checksum verification · Conditional S3 write and replay semantics. |
| [`ReceiptArchive()`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L23) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |
| [`main(String[])`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L26) | 2 | CLI arguments and reservation-prefix selection · On-disk manifest format and checksum verification. Accepts backup or restore arguments and checks the reservation prefix before restore. **Suggested change.** Move manifest ownership checks into the archive reader and keep argument interpretation at the entry point. |
| [`backup(S3Client, String, Path)`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L45) | 2 | On-disk manifest format and checksum verification · Conditional S3 write and replay semantics. Copies receipt objects and writes their names and digests to a new manifest. **Suggested change.** Let a manifest reader/writer handle files and verification while an S3 transfer component handles conditional writes. |
| [`restore(S3Client, String, Path)`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L60) | 2 | On-disk manifest format and checksum verification · Conditional S3 write and replay semantics. Verifies the complete manifest before conditional writes and compares an object already at its key. **Suggested change.** Let a manifest reader/writer handle files and verification while an S3 transfer component handles conditional writes. |
| [`verifiedReceipts(Path)`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L76) | 1 | On-disk manifest format and checksum verification. Rejects invalid filenames, checksums and repeated names before any restore writes. |
| [`digest(byte[])`](../../locker-verification/src/main/java/study/locker/verification/ReceiptArchive.java#L91) | 1 | On-disk manifest format and checksum verification. |

**Refactor to consider.** Separate the local archive codec from the S3 transfer operations and retain the command line as their coordinator.

## BoundaryViolationTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class BoundaryViolationTest`](../../locker-verification/src/test/java/study/architecture/BoundaryViolationTest.java#L19) | 1 | Adversarial fixtures proving that the onion architecture rules reject forbidden edges. |
| [`rejectsAnAdapterAndJdbcDependencyInDomainTestBytecode(Path)`](../../locker-verification/src/test/java/study/architecture/BoundaryViolationTest.java#L20) | 1 | Adversarial fixtures proving that the onion architecture rules reject forbidden edges. |
| [`rejectsSecurityAndTelemetryInBusinessCode(String, Path)`](../../locker-verification/src/test/java/study/architecture/BoundaryViolationTest.java#L58) | 1 | Adversarial fixtures proving that the onion architecture rules reject forbidden edges. |

## ComplexityLimitsTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ComplexityLimitsTest`](../../locker-verification/src/test/java/study/architecture/ComplexityLimitsTest.java#L11) | 1 | Executable boundaries of the selected Checkstyle rules, including an input exactly at each limit. |
| [`cyclomaticLimit()`](../../locker-verification/src/test/java/study/architecture/ComplexityLimitsTest.java#L15) | 1 | Executable boundaries of the selected Checkstyle rules, including an input exactly at each limit. |
| [`couplingLimit()`](../../locker-verification/src/test/java/study/architecture/ComplexityLimitsTest.java#L20) | 1 | Executable boundaries of the selected Checkstyle rules, including an input exactly at each limit. |
| [`statementLimits()`](../../locker-verification/src/test/java/study/architecture/ComplexityLimitsTest.java#L28) | 1 | Executable boundaries of the selected Checkstyle rules, including an input exactly at each limit. Exercises both the method and class statement budgets at their exact boundaries. |
| [`boundary(String, String, String, int)`](../../locker-verification/src/test/java/study/architecture/ComplexityLimitsTest.java#L34) | 1 | Executable boundaries of the selected Checkstyle rules, including an input exactly at each limit. |
| [`verify(String, int, String)`](../../locker-verification/src/test/java/study/architecture/ComplexityLimitsTest.java#L41) | 1 | Executable boundaries of the selected Checkstyle rules, including an input exactly at each limit. |

## LabTelemetry

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LabTelemetry`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L14) | 3 | Grafana data-source access and query response format · Expected reservation log outcomes and safe fields · Expected metrics, labels and consumer lag. |
| [`data(String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L17) | 1 | Grafana data-source access and query response format. |
| [`logs(String)`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L24) | 1 | Grafana data-source access and query response format. |
| [`query(String)`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L28) | 1 | Grafana data-source access and query response format. |
| [`sample(String)`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L32) | 1 | Grafana data-source access and query response format. |
| [`assertPickup(String, String, String, String, double)`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L37) | 2 | Expected reservation log outcomes and safe fields · Expected metrics, labels and consumer lag. **Suggested change.** Keep independent log and Prometheus expectations under the bounded wait. |
| [`assertLogs(String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/LabTelemetry.java#L51) | 1 | Expected reservation log outcomes and safe fields. |

**Refactor to consider.** Separate data-source transport from log and metric expectations so each can be exercised independently.

## LockerJourneyIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LockerJourneyIT`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L23) | 3 | Equivalent reservation behavior and validation across protocols · Authentication and browser-origin policy · Cross-protocol lifecycle delivery and receipt recovery. |
| [`key()`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L30) | 1 | Equivalent reservation behavior and validation across protocols. |
| [`setup()`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L34) | 2 | Authentication and browser-origin policy · Cross-protocol lifecycle delivery and receipt recovery. **Suggested change.** Split caller-token creation from connection setup for checking stored receipts. |
| [`close()`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L62) | 1 | Equivalent reservation behavior and validation across protocols. |
| [`availableSlot(Protocol)`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L67) | 1 | Equivalent reservation behavior and validation across protocols. |
| [`equivalentOperationsAndFaults(Protocol)`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L73) | 1 | Equivalent reservation behavior and validation across protocols. |
| [`commandValidationHasTheSamePublicFaultsAtEveryGateway(Protocol)`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L105) | 1 | Equivalent reservation behavior and validation across protocols. |
| [`rejectsInvalidBearerClaimsAtEveryGateway(Protocol)`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L119) | 1 | Authentication and browser-origin policy. |
| [`browserPreflightAllowsOnlyConfiguredOrigins()`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L147) | 1 | Authentication and browser-origin policy. |
| [`reserveViaRestDepositViaSoapCollectViaGraphqlAndRestoreReceipt(java.nio.file.Path)`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L188) | 1 | Cross-protocol lifecycle delivery and receipt recovery. |
| [`pickupMail(String, String)`](../../locker-verification/src/test/java/study/locker/verification/LockerJourneyIT.java#L265) | 1 | Cross-protocol lifecycle delivery and receipt recovery. |

**Refactor to consider.** Separate protocol-contract checks, security cases and the receipt recovery journey while sharing the stack client.

## ManualBrowser

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ManualBrowser`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L13) | 2 | Selenium controls and request history on the guided journey · Reservation fixture cleanup through real commands. |
| [`trace()`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L16) | 1 | Selenium controls and request history on the guided journey. |
| [`assertFlow(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L20) | 1 | Selenium controls and request history on the guided journey. |
| [`assertAcrossPages(String, String[])`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L25) | 1 | Selenium controls and request history on the guided journey. |
| [`keyIn(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L37) | 1 | Selenium controls and request history on the guided journey. |
| [`element(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L41) | 1 | Selenium controls and request history on the guided journey. |
| [`select(String, String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L45) | 1 | Selenium controls and request history on the guided journey. |
| [`role(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L49) | 1 | Selenium controls and request history on the guided journey. |
| [`feedback()`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L53) | 1 | Selenium controls and request history on the guided journey. |
| [`state()`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L57) | 1 | Selenium controls and request history on the guided journey. |
| [`firstDetail()`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L61) | 1 | Selenium controls and request history on the guided journey. |
| [`press(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L65) | 1 | Selenium controls and request history on the guided journey. |
| [`releaseFixture(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualBrowser.java#L72) | 1 | Reservation fixture cleanup through real commands. |

**Refactor to consider.** Move cleanup into a reservation fixture so it can run independently of the page driver.

## ManualLabIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ManualLabIT`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L17) | 3 | Browser workflow, window navigation and displayed reservation state · Contents and privacy guarantees of inspection endpoints · Correlation between browser actions and telemetry. |
| [`browserReceivesOnlyPublicSettings()`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L22) | 1 | Contents and privacy guarantees of inspection endpoints. |
| [`guidedPickupIsVisibleInLogsAndMetrics()`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L33) | 3 | Browser workflow, window navigation and displayed reservation state · Contents and privacy guarantees of inspection endpoints · Correlation between browser actions and telemetry. **Suggested change.** Have the browser return a journey result consumed by focused privacy and telemetry assertions. |
| [`openFlow(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L63) | 1 | Browser workflow, window navigation and displayed reservation state. |
| [`checkDirectory()`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L75) | 1 | Browser workflow, window navigation and displayed reservation state. |
| [`reserveAndReplay(String, String)`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L87) | 2 | Browser workflow, window navigation and displayed reservation state · Correlation between browser actions and telemetry. **Suggested change.** Return reservation and trace identifiers from page actions and assert evidence using a dedicated flow observer. |
| [`deposit(String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L110) | 2 | Browser workflow, window navigation and displayed reservation state · Correlation between browser actions and telemetry. **Suggested change.** Return reservation and trace identifiers from page actions and assert evidence using a dedicated flow observer. |
| [`collect()`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L135) | 1 | Browser workflow, window navigation and displayed reservation state. |
| [`assertInspectionPrivacy(String, PickupCodes)`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L156) | 1 | Contents and privacy guarantees of inspection endpoints. |
| [`checkTraceOutage()`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L172) | 2 | Browser workflow, window navigation and displayed reservation state · Correlation between browser actions and telemetry. **Suggested change.** Keep page navigation in the driver and put trace/Grafana assertions in an observer. |
| [`cancelAndCheckDashboard(String)`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L194) | 2 | Browser workflow, window navigation and displayed reservation state · Correlation between browser actions and telemetry. **Suggested change.** Keep page navigation in the driver and put trace/Grafana assertions in an observer. |

**Refactor to consider.** Keep one end-to-end journey and give privacy and telemetry verification their own focused scenarios.

## ManualLabIT.PickupCodes

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record PickupCodes`](../../locker-verification/src/test/java/study/locker/verification/ManualLabIT.java#L132) | 1 | Test data for a previous, current and deliberately incorrect pickup code. |

## ProtocolQuotaIT

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ProtocolQuotaIT`](../../locker-verification/src/test/java/study/locker/verification/ProtocolQuotaIT.java#L19) | 1 | Cross-protocol quota accounting for one identity across real gateway replicas. |
| [`restGraphqlSoapAndAnotherReplicaEnforceOneBudget()`](../../locker-verification/src/test/java/study/locker/verification/ProtocolQuotaIT.java#L21) | 1 | Cross-protocol quota accounting for one identity across real gateway replicas. |
| [`gateway(String, Network, String)`](../../locker-verification/src/test/java/study/locker/verification/ProtocolQuotaIT.java#L90) | 1 | Cross-protocol quota accounting for one identity across real gateway replicas. |
| [`url(GenericContainer<?>)`](../../locker-verification/src/test/java/study/locker/verification/ProtocolQuotaIT.java#L103) | 1 | Cross-protocol quota accounting for one identity across real gateway replicas. |
| [`signed(String, KeyPair)`](../../locker-verification/src/test/java/study/locker/verification/ProtocolQuotaIT.java#L107) | 1 | Cross-protocol quota accounting for one identity across real gateway replicas. |

## StackClient

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class StackClient`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L21) | 3 | Test token claims and RSA signing · Protocol-specific serialization and normalization of gateway replies · Stack endpoint selection and HTTP transport. |
| [`local(int)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L33) | 1 | Stack endpoint selection and HTTP transport. |
| [`token(String, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L37) | 1 | Test token claims and RSA signing. |
| [`sign(Map<String, Object>)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L46) | 1 | Test token claims and RSA signing. |
| [`sign(Map<String, Object>, PrivateKey)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L54) | 1 | Test token claims and RSA signing. |
| [`send(String, String, String, String, String, Map<String, String>)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L64) | 1 | Stack endpoint selection and HTTP transport. |
| [`call(Protocol, String, String, String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L75) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`rest(String, String, String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L84) | 2 | Protocol-specific serialization and normalization of gateway replies · Stack endpoint selection and HTTP transport. **Suggested change.** Hand an encoded request to the common HTTP transport and decode its result in the protocol adapter. |
| [`restPath(String, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L94) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`restResult(String, HttpResponse<String>)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L103) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`graph(String, String, String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L117) | 2 | Protocol-specific serialization and normalization of gateway replies · Stack endpoint selection and HTTP transport. **Suggested change.** Hand an encoded request to the common HTTP transport and decode its result in the protocol adapter. |
| [`mutation(String, String, String, String, Map<String, String>)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L138) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`soap(String, String, String, String, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L149) | 2 | Protocol-specific serialization and normalization of gateway replies · Stack endpoint selection and HTTP transport. **Suggested change.** Hand an encoded request to the common HTTP transport and decode its result in the protocol adapter. |
| [`soapResult(String, HttpResponse<String>)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L163) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`field(Document, String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L179) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`slots(JsonNode)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L184) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`booking(JsonNode)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L190) | 1 | Protocol-specific serialization and normalization of gateway replies. |
| [`error(String)`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L195) | 1 | Protocol-specific serialization and normalization of gateway replies. |

**Refactor to consider.** Separate token issuance and HTTP transport from the three protocol adapters, retaining the common result shape.

## StackClient.Protocol

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`enum Protocol`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L22) | 1 | Supported gateway protocols in black-box tests. |

## StackClient.Result

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Result`](../../locker-verification/src/test/java/study/locker/verification/StackClient.java#L26) | 1 | Normalized fields used for assertions across different public protocols. |

## ArchitectureTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ArchitectureTest`](../../locker-verification/src/architecture-test/java/study/architecture/ArchitectureTest.java#L12) | 2 | Module package ownership · Enforcement of onion layers and dependency boundaries on compiled application and test classes. |
| [`mainAndTestBytecodeFollowTheSameArchitecture()`](../../locker-verification/src/architecture-test/java/study/architecture/ArchitectureTest.java#L22) | 2 | Module package ownership · Enforcement of onion layers and dependency boundaries on compiled application and test classes. **Suggested change.** Keep separate assertions for source ownership and dependency violations against the same imported bytecode. |

**Refactor to consider.** Use separate ownership and dependency tests with a shared import of the module bytecode.

## ComplexityTest

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class ComplexityTest`](../../locker-verification/src/architecture-test/java/study/architecture/ComplexityTest.java#L13) | 2 | Discovery of handwritten Java in the current Maven module · Execution and diagnostics of the shared Checkstyle rules. |
| [`handwrittenJavaMeetsComplexityLimits()`](../../locker-verification/src/architecture-test/java/study/architecture/ComplexityTest.java#L14) | 2 | Discovery of handwritten Java in the current Maven module · Execution and diagnostics of the shared Checkstyle rules. Finds the module’s source roots and fails the JUnit test with the reported violations. **Suggested change.** Give source discovery a dedicated helper and invoke the configured checker on the resulting list. |
| [`audit(Path, List<File>)`](../../locker-verification/src/architecture-test/java/study/architecture/ComplexityTest.java#L35) | 1 | Execution and diagnostics of the shared Checkstyle rules. Loads the shared XML rules, collects diagnostics and destroys the Checker after use. |

**Refactor to consider.** Keep discovery in the module test and extract the reusable ruleset invocation when another source layout is introduced.

## ComplexityTest.Result

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`record Result`](../../locker-verification/src/architecture-test/java/study/architecture/ComplexityTest.java#L32) | 1 | Checkstyle error total and diagnostic output carried back to JUnit. |

## OnionRules

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class OnionRules`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L17) | 3 | Which packages form each onion layer and composition root · Permitted cross-context and cross-adapter dependencies · Framework, telemetry and fixture isolation rules. |
| [`onion()`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L32) | 1 | Which packages form each onion layer and composition root. |
| [`boundaries()`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L40) | 3 | Which packages form each onion layer and composition root · Permitted cross-context and cross-adapter dependencies · Framework, telemetry and fixture isolation rules. **Suggested change.** Compose named framework-isolation and cross-context conditions, retaining the violating edge in diagnostics. |
| [`telemetryInWrongAdapter(JavaClass, JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L60) | 1 | Framework, telemetry and fixture isolation rules. |
| [`violatesBoundary(JavaClass, JavaClass, boolean)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L64) | 3 | Which packages form each onion layer and composition root · Permitted cross-context and cross-adapter dependencies · Framework, telemetry and fixture isolation rules. Enforces core framework restrictions before classifying dependencies within locker packages. **Suggested change.** Compose named framework-isolation and cross-context conditions, retaining the violating edge in diagnostics. |
| [`violatesLockerBoundary(JavaClass, JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L70) | 1 | Permitted cross-context and cross-adapter dependencies. |
| [`crossesAdapter(JavaClass, JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L77) | 1 | Permitted cross-context and cross-adapter dependencies. |
| [`coreDependency(JavaClass, JavaClass, boolean)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L82) | 1 | Framework, telemetry and fixture isolation rules. Checks Java/runtime exceptions and allowed test dependencies for the business core. |
| [`isCoreInstrumentation(JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L91) | 1 | Framework, telemetry and fixture isolation rules. |
| [`allowedJavaPackage(String)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L96) | 1 | Framework, telemetry and fixture isolation rules. |
| [`isTelemetryDependency(JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L100) | 1 | Framework, telemetry and fixture isolation rules. |
| [`adapter(JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L107) | 1 | Which packages form each onion layer and composition root. |
| [`context(JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L113) | 1 | Permitted cross-context and cross-adapter dependencies. |
| [`mayDependOnContext(JavaClass, JavaClass, String, String)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L118) | 1 | Permitted cross-context and cross-adapter dependencies. |
| [`edgeApiDependency(JavaClass, JavaClass)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L126) | 1 | Permitted cross-context and cross-adapter dependencies. Allows gateway protocol modules to depend on the edge API or composition root in the proper location. |
| [`reject(ConditionEvents, JavaClass, String)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L131) | 1 | Which packages form each onion layer and composition root. |
| [`OnionRules()`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L135) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |

**Refactor to consider.** Group predicates into package ownership, cross-context references and framework isolation, retaining negative fixtures for each.

## OnionRules.anonymous@41

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class anonymous@41`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L41) | 1 | Inspection of dependency edges and reporting of architecture violations for one imported class. |
| [`check(JavaClass, ConditionEvents)`](../../locker-verification/src/architecture-test/java/study/architecture/OnionRules.java#L42) | 1 | Inspection of dependency edges and reporting of architecture violations for one imported class. |
