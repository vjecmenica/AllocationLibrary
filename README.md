# AllocationLibrary

AllocationLibrary is a Java 17 Maven project for resource allocation experiments and API access.

## Modules

- `allocation-core`: core allocation models, constraints, algorithms, benchmark helpers, and the public library API.
- `allocation-api`: Spring Boot REST API that exposes stateless allocation execution and comparison endpoints.

## Build

Run all tests from the repository root:

```bash
mvn clean test
```

## REST API

See [docs/rest-api.md](docs/rest-api.md) for endpoint documentation and JSON examples.
