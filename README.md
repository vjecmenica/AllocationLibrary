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

## REST API

See [docs/rest-api.md](docs/rest-api.md) for endpoint documentation and JSON examples.

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

Scenarios can be exported as readable JSON files and imported again later. Exports use the versioned
`schemaVersion: 1` format. Scenarios are not saved automatically in the browser or in a backend database.

Successful execution and comparison results can be exported as JSON or CSV. Result JSON files include both the
request and response for reproducibility, while CSV exports provide a flat format for spreadsheet analysis. Result
import and run history are not currently supported.

## Run Locally

1. Start `allocation-api`.
2. In another terminal, start `allocation-web`.
