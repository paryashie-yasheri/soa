# Project guidance

## Requirements and variants

- Read the assignment before changing a Lab 3 branch. Different student variants have different architecture requirements; do not assume one branch's architecture applies to another.
- Variant summaries are in `docs/lab3-variants/` when present. Check the current branch's `README.md`, `README-lab3-*.md`, `lab2.typ`, and the relevant Compose/deployment files for the authoritative details.
- Preserve the Lab 1/2 API contract across Lab 3 changes: XML payloads, existing paths, HTTP methods/statuses, and documented headers.
- Lab 2 explicitly requires HTTPS with a self-signed certificate and forbids plaintext HTTP. Local certificate warnings are expected. Do not disable browser certificate validation or add project certificates to the machine-wide trust store as a test shortcut.
- HTTPS is a protocol requirement, not a request to configure global browser trust. For local CLI checks, use the test-specific truststore or `curl -k` only when checking reachability; never treat certificate bypass as successful browser validation.
- Use the public entry point and port documented for the active variant. Backend container ports are not necessarily the client/gateway entry point. For example, variant 67322 serves the client via `https://localhost:61811/ui/`; port `62811` is its legacy WildFly backend. In the base Lab 2 stack, `https://127.0.0.1:62811/ui/` is the client endpoint. Plain HTTP is expected to fail.
- Known deployment caveat in the Lab 2 README: both JAX-RS applications currently share one WildFly instance although the assignment asks for a second instance for the caller. Do not claim complete Lab 2 deployment compliance without resolving or explicitly discussing this.

## Build and verification

- The frontend is a static React/Vite app packaged as `build/libs/ui.war` and served at `/ui/`. Build it with `./gradlew uiWar`; locked dependencies and code generation run through Bun.
- Run the tests relevant to the current branch, normally `./gradlew test` and the branch-specific Lab 3 stack task or documented Compose command. `./gradlew e2eTest` verifies the base WildFly API stack, not necessarily the branch's full Lab 3 topology.
- `e2e-tests/README.md` documents OpenAPI example checks. The current E2E suite checks served UI assets but does not exercise the React application through browser DOM interactions.
- Run Docker stacks sequentially: variants reuse host ports such as `62811` and `15432`. Stop each stack and verify cleanup before starting another.
- Local generated TLS keys, truststores, database volumes, and build output belong to disposable local test environments; do not commit them.
- Do not deploy to Helios or alter a remote environment during local verification.

## Frontend conventions

- Keep the deployed frontend same-origin with its APIs unless the active variant explicitly requires a gateway/base URL change.
- OpenAPI contracts are in `openapi/`; generated TypeScript bindings live in `frontend/src/generated/` and are recreated by the frontend build.
- Follow `frontend/DESIGN.md` for visual changes.
