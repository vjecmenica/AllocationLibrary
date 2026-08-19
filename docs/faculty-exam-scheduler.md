# Faculty Exam Scheduler

## Purpose

The faculty exam scheduler is a concrete AllocationLibrary use case for assigning exams to permitted time slots,
rooms, and invigilators. It is stateless: every scheduling request contains all input data and no schedule is stored
after the response is returned.

The implementation lives in the `allocation-faculty` Maven module. The module is a Java 17 JAR with no Spring
dependency. It depends on `allocation-core` for the shared `TimeWindow` model and uses Google OR-Tools CP-SAT for
the domain-specific optimization model.

## Relationship To Generic Allocation

The existing generic allocation problem is fixed-time: every `AllocationRequest` already has a start time and
duration, and GREEDY, BACKTRACKING, or CP_SAT assigns resources for that fixed interval.

Faculty scheduling is variable-time: an `Exam` has a duration but no selected start time, room, or invigilators.
The faculty scheduler chooses one of the supplied discrete `ExamSlot` values and then assigns the required
resources. This different decision model is implemented by `CpSatExamScheduler`; the existing
`CpSatAllocationAlgorithm` and other generic allocation algorithms are unchanged. The benchmark model and campaign
are also unchanged.

The faculty user does not choose an algorithm. CP-SAT is an internal implementation detail.

## Domain Model

- `ExamSlot`: a permitted start time with an ID, start, and end.
- `Exam`: subject identity, student count, duration, required invigilator count, and student groups.
- `FacultyRoom`: room identity, capacity, and availability windows.
- `Invigilator`: invigilator identity and availability windows.
- `ExamScheduleRequest`: the complete lists of slots, exams, rooms, and invigilators.
- `ExamAssignment`: one scheduled exam and its selected slot, room, invigilators, and actual occupied interval.
- `UnscheduledExam`: an exam for which no assignment was selected, with a safe human-readable reason.
- `ExamScheduleStatistics`: total, scheduled and unscheduled counts, solver status, execution time, and limit state.
- `ExamScheduleResult`: assignments, unscheduled exams, and statistics.

Rooms and invigilators use `allocation.model.TimeWindow`. A resource is eligible only when one availability window
fully contains the exam's actual occupied interval. A missing or empty availability list means that the resource is
unavailable.

## Discrete Slot Semantics

An `ExamSlot` is a permitted discrete start, not a range in which any start minute may be chosen. If exam `e` is
assigned to slot `s`, its actual interval is:

```text
[s.start, s.start + e.durationMinutes)
```

The exam always starts at `s.start` and must finish no later than `s.end`. Conflict checks use this actual interval,
not the complete slot window. Different slot IDs can therefore still conflict when their actual intervals overlap.
Touching intervals do not overlap, consistently with the shared `TimeWindow` semantics.

## CP-SAT Model

The model uses Boolean decisions equivalent to:

```text
scheduled[exam]
examAtSlot[exam][slot]
roomAssignment[exam][slot][room]
invigilatorAssignment[exam][slot][invigilator]
```

The scheduler uses one CP-SAT search worker and a default internal time limit of five seconds. The limit can be
changed through the Java scheduler constructor for tests and internal callers, but it is intentionally absent from
the REST request.

### Hard Constraints

1. A scheduled exam is assigned to exactly one slot; an unscheduled exam is assigned to none.
2. An exam-slot pair is disabled when the exam would finish after the slot end.
3. Every scheduled exam receives exactly one room.
4. The selected room must have sufficient capacity and contain the complete actual interval in its availability.
5. A room cannot serve overlapping exams, including exams assigned to different overlapping slot IDs.
6. Every scheduled exam receives exactly its required number of eligible invigilators.
7. Exams requiring zero invigilators receive none.
8. Each selected invigilator must be available for the complete actual interval.
9. An invigilator cannot supervise overlapping exams.
10. Exams sharing at least one student-group identifier cannot overlap.

### Objective

V1 maximizes only the number of scheduled exams:

```text
maximize sum(scheduled[exam])
```

There are no priorities, preferred slots, fairness terms, workload balancing, or other soft constraints.

## Solver Results

`OPTIMAL` and `FEASIBLE` results contain assignments selected by the solver. `FEASIBLE` means that a schedule was
found but optimality was not proven. Exams not selected are returned as `UnscheduledExam` entries.

For `INFEASIBLE`, `UNKNOWN`, or another status without a solution, assignment values are not read and all exams are
returned as unscheduled. `stoppedByLimit` is true for `FEASIBLE` or `UNKNOWN`, and false for proven `OPTIMAL` or
`INFEASIBLE` results.

## REST API

The Spring Boot API exposes:

```text
POST /api/faculty/exam-schedule
```

The request contains only domain data:

```json
{
  "slots": [
    {
      "id": "S1",
      "start": "2026-07-01T10:00:00",
      "end": "2026-07-01T12:00:00"
    }
  ],
  "exams": [
    {
      "id": "E1",
      "code": "CS101",
      "name": "Algorithms",
      "studentCount": 30,
      "durationMinutes": 90,
      "requiredInvigilators": 1,
      "studentGroups": ["SI2"]
    }
  ],
  "rooms": [
    {
      "id": "R1",
      "name": "Large room",
      "capacity": 50,
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    }
  ],
  "invigilators": [
    {
      "id": "I1",
      "name": "Professor One",
      "availability": [
        {
          "start": "2026-07-01T08:00:00",
          "end": "2026-07-01T18:00:00"
        }
      ]
    }
  ]
}
```

The request has no selection mode, algorithm, optimization goal, solver workers, or time-limit fields.

The response contains scheduled assignments, unscheduled exams, and statistics. Each assignment includes the exam
identity and student count, slot start and end, actual exam end, selected room, and selected invigilators. It does
not expose algorithm selection. `solverStatus` remains available inside statistics for technical diagnostics.

Invalid domain values and structurally incomplete requests return the existing HTTP 400 error format used by the
rest of the API.

## V1 Limitations

- Exactly one room is assigned to each scheduled exam.
- Capacities from multiple rooms cannot be combined.
- There are no soft constraints or preferred slots.
- Exams can start only at the start of a supplied discrete `ExamSlot`.
- There is no arbitrary start minute inside a slot.
- There is no database or schedule persistence.
- There is no authentication.
- There are no student accounts or exam registration workflows.
- This is not a complete faculty information system.
- Generic allocation algorithms and the validated benchmark remain unchanged.
