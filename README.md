# AllocationLibrary

AllocationLibrary is a Java 17 Maven project for resource allocation experiments and API access.

## Modules

- `allocation-core`: core allocation models, constraints, algorithms, benchmark helpers, and the public library API.
- `allocation-api`: Spring Boot REST API that exposes stateless allocation execution and comparison endpoints.
- `allocation-web`: Angular frontend for running the sample allocation scenario through the REST API.

## Build

Run all tests from the repository root:

```bash
mvn clean test
```

## Continuous Integration

The GitHub Actions CI workflow automatically verifies:

- Maven backend tests for `allocation-core` and `allocation-api`;
- Angular frontend tests;
- the Angular production build.

## REST API

See [docs/rest-api.md](docs/rest-api.md) for endpoint documentation and JSON examples.

## Benchmarking

The reproducible benchmark CLI in `allocation-core` runs GREEDY, BACKTRACKING, and CP_SAT over deterministic,
seeded scenario profiles. It writes raw measurements, aggregate statistics, and environment metadata without
starting the REST API. See [docs/benchmarking.md](docs/benchmarking.md) for profiles, metrics, CLI options, and
result interpretation. Benchmark schema version 2 records the scenario fingerprint, rotated execution position,
and source provenance; existing result files are protected unless `--overwrite` is explicitly supplied.

## Frontend

Install and run the Angular development server:

```bash
cd allocation-web
npm install
npm start
```

The backend runs on [http://localhost:8080](http://localhost:8080/).
The frontend development server runs on [http://localhost:4200](http://localhost:4200/).
The Angular development proxy forwards `/api` requests to the backend.

The frontend initially loads the Greedy Trap example. Resources and allocation requests can be edited directly in
the scenario editor, and EXPLICIT, AUTO, and COMPARE all use the currently entered scenario. The scenario is kept
only in memory for the current browser session.

In COMPARE mode, the frontend shows both aggregate algorithm metrics and each algorithm's decision for every
allocation request in the original scenario order.

Scenarios can be exported as readable JSON files and imported again later. Exports use the versioned
`schemaVersion: 1` format. Scenarios are not saved automatically in the browser or in a backend database.

Successful execution and comparison results can be exported as JSON or CSV. Result JSON files include both the
request and response for reproducibility, while CSV exports provide a flat format for spreadsheet analysis. Result
import and run history are not currently supported.

## Run Locally

1. Start `allocation-api`.
2. In another terminal, start `allocation-web`.
