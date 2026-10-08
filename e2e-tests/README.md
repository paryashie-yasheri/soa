# End-to-end tests

Start Docker, then run from the repository root:

```bash
./gradlew e2eTest
```

The task builds the two WildFly WARs, starts `wildfly/compose.yaml` (PostgreSQL 16, WildFly 35 with both
applications and Swagger UI) and runs `WildFlyStackE2ETest` against the running
deployment. The stack is torn down, including its database volume, when the tests finish.

The suite talks to the services over HTTPS on port `62811` (override with `SOA_TEST_HTTPS_PORT`), checks Swagger UI and live OpenAPI documents on the same HTTPS port,
verifies the Liquibase-created tables through JDBC on port `15432`, and confirms that plaintext HTTP
cannot reach the applications. It covers organization CRUD and persistence, filtering, sorting,
pagination, employees, both directory operations, statistics, validation errors, CORS, and the Swagger UI
assets. It does not interact with the browser DOM.

The test report is in `e2e-tests/build/reports/tests/test`.

## OpenAPI examples

```bash
python3 -m pip install -r e2e-tests/requirements.txt
./gradlew :e2e-tests:openapiExamples
```

This reads both source specifications and executes all 18 explicit XML examples.
It checks request payloads, response XML (normalizing generated dates and insignificant
XML formatting), every documented operation, and error responses for 400, 404, 415,
500, 502, 503 and 504. Failure cases use only the disposable local Compose database
and a temporary upstream HTTP stub. The script verifies the local container port
mapping and database identity before making changes; it has no remote-target option.
The report is `e2e-tests/build/reports/openapi-examples.json`. Any new explicit example
fails the coverage check until a corresponding scenario is added.

The live-document checks also require request body schemas and examples to match the
source contracts, preventing raw XML handler signatures from changing object schemas
into strings. `scripts/swagger_examples.cjs` checks the actual Swagger UI editors for
organization create/update and employee create using Playwright with Chromium. It is
read-only: it opens “Try it out” but never clicks “Execute”. Set `SOA_SWAGGER_URL` to
target a local UI; the default uses the Helios SSH tunnel at `https://localhost:61811/ui/`.
