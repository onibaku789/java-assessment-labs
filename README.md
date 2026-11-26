# Java assessment labs

Build a parcel tracking API, make shipment processing safe to retry, and operate a Java service on Kubernetes. The eight-week path takes about 5–7 hours per week. Each project starts with working code and two baseline tests. The checkboxes below are the work to do.

## Run the tests

Run Maven from the folder containing this README. Surefire runs the six JUnit Jupiter smoke tests. Failsafe discovers the eight weekly JUnit integration suites during `verify`. Week 5 is enabled on the starter, so a full build starts with **seven passing tests**. Start Docker before running the full build. These commands use Java 25 and Maven from your path.

```bash
mvn test       # Smoke tests across all three projects
mvn verify     # Smoke tests plus every enabled weekly suite
```

For the same JDK and Maven versions as CI, use `./toolchain.sh mvn verify`. In IntelliJ IDEA, open the root `pom.xml` as a Maven project, select JDK 25, and run **Lifecycle → verify** in the root Maven project. Each weekly class and its methods also have JUnit run actions.

### Work on a branch and merge into main

Use one branch per exercise. Start Week 1 from the latest merged version of `main`.

```bash
git switch main
git pull --ff-only
git switch -c feat/week-01-parcel-creation
```

Enable the Week 1 suite, implement the exercise, and run the tests. Then publish your branch and open a pull request.

```bash
./toolchain.sh mvn verify
git add parcel-api/
git diff --cached
git commit -m "Implement parcel creation"
git push -u origin HEAD
gh pr create --base main --fill
```

New commits pushed to the same branch update the pull request and rerun CI. Once the **verify** check passes, review **Files changed** on GitHub and choose **Squash and merge**. Then run `git switch main` and `git pull --ff-only` locally before creating the next exercise branch.

### Start Week 1

Open [Week01ParcelIT.java](parcel-api/src/test/java/study/parcel/Week01ParcelIT.java) and remove the class annotation `@Disabled("Enable when starting Week 1")`. Leave completed weeks enabled so they continue to run. Run the Week 1 suite from the repository root.

```bash
mvn -pl parcel-api -Dit.test=Week01ParcelIT verify
```

Start with `createReturns201AndLocationThatRetrievesThePersistedParcel`. On the starter it expects **201 Created** and receives **405 Method Not Allowed**. Implement `POST /parcels` in [ParcelController.java](parcel-api/src/main/java/study/parcel/ParcelController.java). The test sends the JSON below, then retrieves the saved parcel using the response’s `Location` header.

```json
{"trackingId":"P-201","status":"IN_TRANSIT","destination":"Leeds"}
```

To repeat that method, add its name to the Failsafe filter.

```bash
mvn -pl parcel-api '-Dit.test=Week01ParcelIT#createReturns201AndLocationThatRetrievesThePersistedParcel' verify
```

| Week and JUnit suite | What to demonstrate |
| --- | --- |
| 1 · [Parcel creation](parcel-api/src/test/java/study/parcel/Week01ParcelIT.java) | Persist a parcel, follow its location, reject invalid data, handle a replay and preserve the original read behavior. |
| 2 · [API security](parcel-api/src/test/java/study/parcel/Week02SecurityIT.java) | Accept a reader, deny an observer, validate signatures and claims, and allow browser preflight. |
| 3 · [SOAP carrier](parcel-api/src/test/java/study/parcel/Week03CarrierIT.java) | Implement the [carrier adapter](parcel-api/src/main/java/study/parcel/CarrierClient.java). Return the status on success, an empty result for an unknown parcel, and an I/O error within five seconds for a stalled request. JUnit starts a loopback Java HTTP fixture. |
| 4 · [Shipment delivery](shipment-worker/src/test/java/study/parcel/Week04DeliveryIT.java) | Write one receipt per event under concurrent delivery or replay and recover from a failed write. |
| 5 · [Backup and restore](shipment-worker/src/test/java/study/parcel/Week05RestoreIT.java) | Restore two dataset sizes three times each, verify SHA-256 digests, record timings and preserve an existing receipt. |
| 6 · [Kubernetes readiness](release-lab/src/test/java/study/parcel/Week06ReleaseIT.java) | Change the region, pause readiness on one live replica, and verify traffic reaches only ready endpoints. Run locally while the replica is paused. |
| 7 · [Scaling and rollback](release-lab/src/test/java/study/parcel/Week07OperationsIT.java) | Generate CPU load with the HPA enabled, deploy a bad revision, then roll back. Run locally after recovery while scaling events remain available. |
| 8 · [SQL query plan](parcel-api/src/test/java/study/parcel/Week08SqlIT.java) | Return the same 100 Bath parcels with fewer PostgreSQL shared-buffer accesses than the sequential-scan baseline. Apply the index on a fresh database. |

Weeks 1 and 2 use in-memory H2. Week 1 exercises HTTP and persistence with security filters disabled. Week 2 runs the real filter chain and provides an ephemeral RSA public key through `spring.security.oauth2.resourceserver.jwt.public-key-location`. Use that property in your decoder. The issuer is `urn:parcel:study`, the audience is `parcel-api`, and the scopes are `parcel.read` and `parcel.observe`.

Weeks 4, 5 and 8 use Testcontainers with a running Docker engine. Weeks 6 and 7 read the dedicated kind cluster created by the release quick start below. The tests use the Kubernetes API to read the Helm release history and sample traffic through the Service proxy. Remove the weekly `@Disabled` annotation to enable either suite locally. Keep the GitHub Actions condition on both classes.

### Continuous integration

[GitHub Actions](.github/workflows/ci.yml) runs `mvn --batch-mode --no-transfer-progress --fail-at-end verify` for pull requests targeting `main`, pushes to `main`, and manual runs. An enabled failing test fails the build. Open the run’s **junit-reports** artifact for Surefire and Failsafe XML, including failures and skips. Locally the same files are under each module’s `target/surefire-reports/` and `target/failsafe-reports/`.

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
