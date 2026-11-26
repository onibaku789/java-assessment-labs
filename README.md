# Java assessment labs

Build a parcel tracking API, make shipment processing safe to retry, and operate a Java service on Kubernetes. The eight-week path takes about 5–7 hours per week. Each project starts with working code and two baseline tests. The checkboxes below are the work to do.

## Run your first exercise test

Run the first contract from the folder containing this README.

```bash
./check.sh 1
```

Start with `createReturns201AndLocationThatRetrievesThePersistedParcel`. On the starter it expects **201 Created** and receives **405 Method Not Allowed**. Implement `POST /parcels` in [ParcelController.java](parcel-api/src/main/java/study/parcel/ParcelController.java), then rerun the same command. The test sends this JSON and follows the URL in the response’s `Location` header.

```json
{"trackingId":"P-201","status":"IN_TRANSIT","destination":"Leeds"}
```

Run one week at a time. A failing assertion names the behavior you still need to implement or demonstrate. The weekly suites run explicitly through `check.sh`. The ordinary `mvn test` and `package` lifecycle continues to run the starter smoke tests. To rerun just that first method from this folder, use the following command.

```bash
cd parcel-api
method=createReturns201AndLocationThatRetrievesThePersistedParcel
../toolchain.sh mvn "-Dtest=Week01ParcelContract#$method" test
```

| Command        | What the automated check expects                                                                                                                                                                                                                                                                                                                                                  |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `./check.sh 1` | [Creation contract](parcel-api/src/test/java/study/parcel/Week01ParcelContract.java). Persist a parcel, follow its location, reject invalid data, handle a replay and preserve the original read contract.                                                                                                                                                                        |
| `./check.sh 2` | [Security contract](parcel-api/src/test/java/study/parcel/Week02SecurityContract.java). Accept a reader, deny an observer, validate the signature and claims, and allow the browser’s preflight.                                                                                                                                                                                  |
| `./check.sh 3` | [Carrier contract](parcel-api/src/test/java/study/parcel/Week03CarrierContract.java). Implement the [Java adapter](parcel-api/src/main/java/study/parcel/CarrierClient.java). Return the status on success, an empty optional for the declared fault, and an I/O error for a stalled service within five seconds. The suite starts the supplied Python carrier on a private port. |
| `./check.sh 4` | [Delivery contract](shipment-worker/src/test/java/study/parcel/Week04DeliveryContract.java). One receipt per event under concurrent delivery or replay, separate receipts for separate events, and eventual recovery after a failed write.                                                                                                                                        |
| `./check.sh 5` | [Restore contract](shipment-worker/src/test/java/study/parcel/Week05RestoreContract.java). Restore two dataset sizes three times each, verify independent SHA-256 digests, print timings and preserve an existing receipt. The supplied restore should already pass.                                                                                                              |
| `./check.sh 6` | [Readiness checks](release-lab/check_release.py). Use the release quick start below, change the region, and pause one running replica. Run the check while it is unready, then remove the pause file.                                                                                                                                                                             |
| `./check.sh 7` | [Operations checks](release-lab/check_release.py). Enable the HPA, generate sustained load, deploy a revision with `accepting=false`, and roll it back. Run the check after rollback while scaling events are still available.                                                                                                                                                    |
| `./check.sh 8` | [SQL contract](parcel-api/src/test/java/study/parcel/Week08SqlContract.java). Return the same 100 Bath parcels while touching fewer PostgreSQL shared buffers than the sequential-scan baseline. Apply your index through the application’s schema or migration so it works on a fresh database.                                                                                  |

Weeks 1 and 2 use in-memory H2. Week 1 exercises HTTP and persistence with security filters disabled so it remains runnable after Week 2. Week 2 runs the real filter chain and gives the application an ephemeral RSA public key via `spring.security.oauth2.resourceserver.jwt.public-key-location`. Use that property in your decoder. Test tokens are signed for issuer `urn:parcel:study`, audience `parcel-api`, and scope `parcel.read` or `parcel.observe`.

Weeks 4, 5 and 8 need a running Docker engine. Their tests start and remove isolated containers on temporary ports. Weeks 6 and 7 inspect the dedicated kind cluster you deploy and send a short traffic sample through its Service. The weekly checkboxes below also identify evidence to collect yourself, including the browser trace, recovery objective, protocol choices, JVM profile and licence decision.

## Start with the parcel API

Use macOS or Linux with Docker Compose, Bash, curl, tar and shasum. The shared launcher downloads Java 25 LTS and Maven on first use. The browser, token and carrier exercises also use Python 3.15.2 and OpenSSL. All services run locally.

Run these commands from the folder containing this README.

```bash
cd parcel-api
docker compose up -d --wait
../toolchain.sh mvn clean package
../toolchain.sh java -jar target/parcel-api-1.0.0.jar
```

In a second terminal, run `curl -i http://127.0.0.1:18081/parcels/P-100`. Expect HTTP 200 with status `IN_TRANSIT`. Your first change is to let a caller create another parcel and retrieve it at the URL returned by the API.

## Weeks 1–3 · Parcel Tracking API

Start in the [parcel controller](parcel-api/src/main/java/study/parcel/ParcelController.java) and its [HTTP tests](parcel-api/src/test/java/study/parcel/ParcelApiTest.java).

- [ ] **Week 1 — Create and version a resource.** Implement `POST /parcels` with a caller-supplied tracking ID. Validate the input, enforce uniqueness in PostgreSQL, and decide how the API handles duplicates. Add an optional response field and run the original client test again. **Done when** a test creates a parcel, follows its `201 Created` response’s `Location` header, and reads the same parcel. Cover invalid input and a repeated ID. Explain the API’s Richardson maturity level and one change that would need a version migration.
- [ ] **Week 2 — Secure a lookup.** Add Spring Security JWT validation for signature, issuer, audience, expiry and read scope. Use the [token fixture](parcel-api/fixtures/token.py) to generate reader and observer tokens. From `parcel-api`, serve the [browser client](parcel-api/browser/index.html) with `python3 -m http.server 18082 --bind 127.0.0.1 --directory browser`. Allow that origin and inspect the preflight in DevTools. **Done when** tests show 200 for a reader, 401 for a missing or invalid token, and 403 for an authenticated observer. Save a redacted browser trace that explains a denied origin and map two controls to OWASP risks.
- [ ] **Week 3 — Integrate a carrier.** From `parcel-api`, run `python3 fixtures/carrier.py`. Use the [WSDL](parcel-api/fixtures/carrier.wsdl) to write a Java client with a timeout. Also run `./grpc-demo.sh`. **Done when** the Java client tests distinguish a successful lookup, the declared unknown-parcel SOAP fault, and an unavailable carrier. Give one concrete reason to choose SOAP, REST, GraphQL, gRPC or messaging for different clients.

## Weeks 4–5 · Shipment Event Processor

Open a terminal at the project root. Start the emulators and follow one event through the [worker](shipment-worker/src/main/java/study/parcel/Worker.java).

```bash
cd shipment-worker
docker compose up -d
../toolchain.sh mvn clean package
./worker.sh init
./worker.sh send fixtures/delivered.json
./worker.sh once
./worker.sh list
```

On a fresh stack, expect one receipt under `receipts/evt-7/`. If the emulators are still starting, repeat `./worker.sh init`.

- [ ] **Week 4 — Make redelivery safe.** Send the fixture again, run `./worker.sh once --interrupt`, wait six seconds, then run `./worker.sh once`. Inspect the extra receipt keys. Change the worker so replaying the same business event is safe, including after a successful write followed by a crash. Decide how to handle conflicting payloads for one ID. **Done when** two deliveries of the same event produce one business result, two different IDs produce two, and a failed write leaves the message available for retry.
- [ ] **Week 5 — Prove recovery and choose cloud services.** Set a recovery-time objective and a tolerable data-loss window. Run `./worker.sh restore fixtures/delivered.json`, then repeat with a larger synthetic file. Compare at least three runs. Sketch an AWS and an Azure design covering compute, queue, database, object/block/shared-file storage, identity and backup. **Done when** you can show matching checksums, measured recovery times, and whether you met your objective. Trace one access requirement to a control and name the live-provider test that would prove it.

## Weeks 6–7 · Kubernetes Release and Recovery

Open a terminal at the project root. Deploy the supplied [Helm chart](release-lab/chart/) and sample traffic from inside the cluster.

```bash
cd release-lab
./install-tools.sh
source ./tools.sh
./cluster.sh up
./deploy.sh
./load.sh track 3
```

Run `source ./tools.sh` from `release-lab` in each additional terminal so Helm and kubectl use this lab’s cluster.

- [ ] **Week 6 — Build and inspect a release.** Compare the [multistage Dockerfile](release-lab/Dockerfile) with a Spring Boot buildpack image built from the same source. Inspect size, layers and runtime UID. Change the Helm `region` value and check `/track`. On one pod, create `/tmp/parcel-study-paused`, wait for readiness to turn false, sample traffic, then remove the file. **Done when** you can show the region changed with the same application image and the live, unready pod stopped receiving new connections. Explain the three probes and identify which Twelve-Factor practices the service demonstrates.
- [ ] **Week 7 — Measure scaling and roll back.** Run `./cluster.sh metrics`, then `helm upgrade parcel chart --reuse-values --set autoscale=true`. Run `./load.sh work 110 6` while watching `kubectl get hpa -w`. Compare Service traffic with a single port-forward. Record the healthy Helm revision, set `accepting=false` in a new revision, inspect pod readiness and events, and roll back. **Done when** you can show replica counts, p95 latency and errors during load, plus successful requests after rollback. Explain when request rate or queue age would be a better scaling signal than CPU.

## Week 8 · Diagnose and defend

- [ ] **Tune a measured bottleneck.** Return to `parcel-api`, load the [SQL fixture](parcel-api/fixtures/profile.sql), and exercise `GET /parcels?destination=Bath`. Capture a Java Flight Recorder profile and an `EXPLAIN (ANALYZE, BUFFERS)` plan. Change one index or query and repeat with the same data and request count. **Done when** you can explain the before-and-after numbers and the trade-off your change introduced.
- [ ] **Make the work reproducible.** Document a shared IDE run configuration and verify the build from a clean checkout. For a customer distribution of this application, inspect the Spring Boot dependency’s licence and notices and record the obligations. **Done when** another developer can reproduce your run and follow your licence decision.
- [ ] **Prepare the assessment.** For each required skill (Cloud Fundamentals, Java Security, Java Web Development, Java Cloud, and Java Containerization & Orchestration), record one decision, the test or measurement that supports it, and what still needs live-cloud evidence. Do the same for the recommended Software Engineering Knowledge & Experience skill.

## Evidence and cleanup

Keep the test output, redacted trace or measurements that support each checked box. If you use AI, first predict the outcome yourself. Then ask it to challenge your reasoning or suggest a missing test. Finish by reproducing the result and explaining it unaided.

Stop foreground services with Ctrl-C. From either Compose project, `docker compose down -v` removes that project’s containers and disposable data. From `release-lab`, `./cluster.sh down` removes the local cluster. Save your evidence first.
