# End-to-end tests

Start Docker, then run from the repository root:

```bash
./gradlew e2eTest
```

The task builds the three application JARs and runs each in a separate JVM with the
production profile. It starts a PostgreSQL 16 container with schema `s389491` and
generates a temporary self-signed TLS certificate. Test requests go through the
client proxy over HTTPS. The services also use HTTPS for upstream calls and trust
the generated certificate through their JVM trust stores.

Tests cover organization CRUD and persistence, filtering, sorting, pagination,
employees, both directory operations, statistics, validation errors, static client
assets, rejection of plaintext HTTP, and recovery after a directory service outage.
The tests exercise the stack over HTTP; they do not interact with the browser DOM.

The suite uses temporary ports and a database separate from the development Compose
database. It stops the application processes and container when tests finish or
startup fails. Application logs and the generated certificate are in
`e2e-tests/build/stack`. The test report is in `e2e-tests/build/reports/tests/test`.
You can also run the suite as part of `./gradlew build`.
