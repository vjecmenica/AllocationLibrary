# Allocation REST API

The `allocation-api` module exposes a stateless REST API for running allocation algorithms from `allocation-core`.
Each request contains the complete resource list, allocation request list, and algorithm options. The API does not store scenarios, requests, results, or execution history after the HTTP request finishes.

## Health

### `GET /api/health`

Returns a simple application health response.

```json
{
  "status": "UP"
}
```

## Execute One Allocation

### `POST /api/allocations`

Runs one allocation algorithm. The endpoint supports explicit algorithm selection and automatic algorithm selection.

Supported `selectionMode` values:

- `EXPLICIT`
- `AUTO`

For `EXPLICIT`, `algorithm` is required and must be one of:

- `GREEDY`
- `BACKTRACKING`
- `CP_SAT`

For `AUTO`, `algorithm` must be omitted or `null`. The optional `goal` field can be:

- `FASTEST`
- `BALANCED`
- `BEST_QUALITY`

If `AUTO` is used without `goal`, the API uses `BALANCED`.

Optional time limits:

- `backtrackingTimeLimitMs`: Backtracking time limit in milliseconds.
- `cpSatTimeLimitSeconds`: CP-SAT time limit in seconds.

If a time limit is omitted, the default from `allocation-core` is used.

### EXPLICIT + CP_SAT Request

```json
{
  "selectionMode": "EXPLICIT",
  "algorithm": "CP_SAT",
  "cpSatTimeLimitSeconds": 1.0,
  "resources": [
    {
      "id": "R_BIG",
      "name": "Large room",
      "type": "ROOM",
      "capacities": {
        "people": 100
      },
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    },
    {
      "id": "R_SMALL",
      "name": "Small room",
      "type": "ROOM",
      "capacities": {
        "people": 30
      },
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    }
  ],
  "requests": [
    {
      "id": "REQ_SMALL",
      "name": "Small exam",
      "startTime": "2026-07-01T10:00:00",
      "durationMinutes": 120,
      "priority": 10,
      "resourceRequirements": [
        {
          "resourceType": "ROOM",
          "quantity": 1,
          "requiredCapacities": {
            "people": 30
          }
        }
      ]
    },
    {
      "id": "REQ_BIG",
      "name": "Large exam",
      "startTime": "2026-07-01T10:00:00",
      "durationMinutes": 120,
      "priority": 9,
      "resourceRequirements": [
        {
          "resourceType": "ROOM",
          "quantity": 1,
          "requiredCapacities": {
            "people": 100
          }
        }
      ]
    }
  ]
}
```

### AUTO + BALANCED Request

```json
{
  "selectionMode": "AUTO",
  "goal": "BALANCED",
  "backtrackingTimeLimitMs": 1000,
  "cpSatTimeLimitSeconds": 1.0,
  "resources": [
    {
      "id": "R_BIG",
      "name": "Large room",
      "type": "ROOM",
      "capacities": {
        "people": 100
      },
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    },
    {
      "id": "R_SMALL",
      "name": "Small room",
      "type": "ROOM",
      "capacities": {
        "people": 30
      },
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    }
  ],
  "requests": [
    {
      "id": "REQ_SMALL",
      "name": "Small exam",
      "startTime": "2026-07-01T10:00:00",
      "durationMinutes": 120,
      "priority": 10,
      "resourceRequirements": [
        {
          "resourceType": "ROOM",
          "quantity": 1,
          "requiredCapacities": {
            "people": 30
          }
        }
      ]
    },
    {
      "id": "REQ_BIG",
      "name": "Large exam",
      "startTime": "2026-07-01T10:00:00",
      "durationMinutes": 120,
      "priority": 9,
      "resourceRequirements": [
        {
          "resourceType": "ROOM",
          "quantity": 1,
          "requiredCapacities": {
            "people": 100
          }
        }
      ]
    }
  ]
}
```

### Abbreviated Successful Response Example

```json
{
  "selectionMode": "EXPLICIT",
  "requestedAlgorithm": "CP_SAT",
  "executedAlgorithm": "CP_SAT",
  "goal": null,
  "selectionReason": "CP-SAT was executed because the caller explicitly selected it.",
  "measuredExecutionTimeMs": 18.3421,
  "statistics": {
    "totalRequests": 2,
    "allocatedRequests": 2,
    "rejectedRequests": 0,
    "algorithmExecutionTimeMs": 18,
    "totalPriorityScore": 19,
    "exploredStates": 0,
    "stoppedByLimit": false,
    "algorithmStatus": "OPTIMAL",
    "objectiveValue": 59.0
  }
}
```

The full response also includes complete `allocations` and `rejectedRequests` arrays.

`measuredExecutionTimeMs` is measured by the public service layer around one algorithm call using high-resolution wall-clock timing.
`statistics.algorithmExecutionTimeMs` is the existing integer millisecond value reported by the core algorithm statistics.

## Compare Algorithms

### `POST /api/allocations/compare`

Runs `GREEDY`, `BACKTRACKING`, and `CP_SAT` on the same resources and requests.

### Request

```json
{
  "backtrackingTimeLimitMs": 1000,
  "cpSatTimeLimitSeconds": 1.0,
  "resources": [
    {
      "id": "R_BIG",
      "name": "Large room",
      "type": "ROOM",
      "capacities": {
        "people": 100
      },
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    },
    {
      "id": "R_SMALL",
      "name": "Small room",
      "type": "ROOM",
      "capacities": {
        "people": 30
      },
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    }
  ],
  "requests": [
    {
      "id": "REQ_SMALL",
      "name": "Small exam",
      "startTime": "2026-07-01T10:00:00",
      "durationMinutes": 120,
      "priority": 10,
      "resourceRequirements": [
        {
          "resourceType": "ROOM",
          "quantity": 1,
          "requiredCapacities": {
            "people": 30
          }
        }
      ]
    },
    {
      "id": "REQ_BIG",
      "name": "Large exam",
      "startTime": "2026-07-01T10:00:00",
      "durationMinutes": 120,
      "priority": 9,
      "resourceRequirements": [
        {
          "resourceType": "ROOM",
          "quantity": 1,
          "requiredCapacities": {
            "people": 100
          }
        }
      ]
    }
  ]
}
```

### Response

```json
{
  "results": {
    "GREEDY": {
      "algorithm": "GREEDY",
      "measuredExecutionTimeMs": 0.1423,
      "allocationResult": {
        "statistics": {
          "totalPriorityScore": 10
        }
      }
    },
    "BACKTRACKING": {
      "algorithm": "BACKTRACKING",
      "measuredExecutionTimeMs": 1.3372,
      "allocationResult": {
        "statistics": {
          "totalPriorityScore": 19
        }
      }
    },
    "CP_SAT": {
      "algorithm": "CP_SAT",
      "measuredExecutionTimeMs": 16.8154,
      "allocationResult": {
        "statistics": {
          "totalPriorityScore": 19,
          "algorithmStatus": "OPTIMAL"
        }
      }
    }
  },
  "bestTotalPriorityScore": 19,
  "bestScoreAlgorithms": [
    "BACKTRACKING",
    "CP_SAT"
  ],
  "fastestAlgorithm": "GREEDY"
}
```

`fastestAlgorithm` is the fastest algorithm measured during that specific compare request. It is not a general performance guarantee.

## Error Response

Invalid requests return HTTP 400 with a stable JSON shape:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/api/allocations"
}
```
