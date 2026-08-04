import {
  AllocationAlgorithmType,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
  AllocationComparisonEntry,
  AllocationDto,
  AllocationRequestDto,
  RejectedRequestDto,
  ResourceDto,
} from '../../core/models/allocation-api.models';
import { buildRequestComparisonRows } from './comparison-analysis';

describe('comparison analysis', () => {
  it('preserves the original request order', () => {
    const second = allocationRequest('REQ_SECOND', 'Second request');
    const first = allocationRequest('REQ_FIRST', 'First request');
    const request = comparisonRequest([second, first]);

    const rows = buildRequestComparisonRows(request, comparisonResponse());

    expect(rows.map((row) => row.request.id)).toEqual(['REQ_SECOND', 'REQ_FIRST']);
  });

  it('recognizes an accepted request and carries all assigned resources', () => {
    const requestDto = allocationRequest('REQ_1', 'Accepted request');
    const room = resource('ROOM_1', 'Room one');
    const staff = resource('STAFF_1', 'Staff one', 'STAFF');
    const response = comparisonResponse({
      GREEDY: entry('GREEDY', [allocation(requestDto, [room, staff])]),
    });

    const outcome = buildRequestComparisonRows(comparisonRequest([requestDto]), response)[0]
      .outcomes.GREEDY;

    expect(outcome.status).toBe('ACCEPTED');
    expect(outcome.assignedResources.map(({ id }) => id)).toEqual(['ROOM_1', 'STAFF_1']);
    expect(outcome.rejectionReason).toBeNull();
  });

  it('recognizes a rejected request and carries its reason', () => {
    const requestDto = allocationRequest('REQ_1', 'Rejected request');
    const response = comparisonResponse({
      GREEDY: entry('GREEDY', [], [rejection(requestDto, 'No suitable room.')]),
    });

    const outcome = buildRequestComparisonRows(comparisonRequest([requestDto]), response)[0]
      .outcomes.GREEDY;

    expect(outcome.status).toBe('REJECTED');
    expect(outcome.assignedResources).toEqual([]);
    expect(outcome.rejectionReason).toBe('No suitable room.');
  });

  it('allows algorithms to have different outcomes for the same request', () => {
    const requestDto = allocationRequest('REQ_1', 'Different outcomes');
    const response = comparisonResponse({
      GREEDY: entry('GREEDY', [allocation(requestDto, [resource('ROOM_1', 'Room')])]),
      BACKTRACKING: entry('BACKTRACKING', [], [rejection(requestDto, 'Rejected by backtracking.')]),
    });

    const outcomes = buildRequestComparisonRows(comparisonRequest([requestDto]), response)[0]
      .outcomes;

    expect(outcomes.GREEDY.status).toBe('ACCEPTED');
    expect(outcomes.BACKTRACKING.status).toBe('REJECTED');
    expect(outcomes.CP_SAT.status).toBe('UNKNOWN');
  });

  it('marks a request missing from both result lists as UNKNOWN', () => {
    const rows = buildRequestComparisonRows(
      comparisonRequest([allocationRequest('REQ_1', 'Missing request')]),
      comparisonResponse(),
    );

    expect(rows[0].outcomes.GREEDY.status).toBe('UNKNOWN');
  });

  it('marks conflicting accepted and rejected data as UNKNOWN', () => {
    const requestDto = allocationRequest('REQ_1', 'Conflicting request');
    const response = comparisonResponse({
      GREEDY: entry(
        'GREEDY',
        [allocation(requestDto, [resource('ROOM_1', 'Room')])],
        [rejection(requestDto, 'Conflicting rejection.')],
      ),
    });

    const outcome = buildRequestComparisonRows(comparisonRequest([requestDto]), response)[0]
      .outcomes.GREEDY;

    expect(outcome).toEqual({
      status: 'UNKNOWN',
      assignedResources: [],
      rejectionReason: null,
    });
  });

  it('returns UNKNOWN outcomes for empty allocation and rejection lists', () => {
    const row = buildRequestComparisonRows(
      comparisonRequest([allocationRequest('REQ_1', 'Unresolved request')]),
      comparisonResponse(),
    )[0];

    expect(Object.values(row.outcomes).map(({ status }) => status)).toEqual([
      'UNKNOWN',
      'UNKNOWN',
      'UNKNOWN',
    ]);
  });

  it('does not mutate the input request or response', () => {
    const requestDto = allocationRequest('REQ_1', 'Immutable request');
    const request = comparisonRequest([requestDto]);
    const response = comparisonResponse({
      CP_SAT: entry('CP_SAT', [allocation(requestDto, [resource('ROOM_1', 'Room')])]),
    });
    const requestSnapshot = structuredClone(request);
    const responseSnapshot = structuredClone(response);

    buildRequestComparisonRows(request, response);

    expect(request).toEqual(requestSnapshot);
    expect(response).toEqual(responseSnapshot);
  });
});

function comparisonRequest(requests: AllocationRequestDto[]): AllocationComparisonApiRequest {
  return {
    resources: [],
    requests,
  };
}

function comparisonResponse(
  entries: Partial<Record<AllocationAlgorithmType, AllocationComparisonEntry>> = {},
): AllocationComparisonApiResponse {
  return {
    results: {
      GREEDY: entries.GREEDY ?? entry('GREEDY'),
      BACKTRACKING: entries.BACKTRACKING ?? entry('BACKTRACKING'),
      CP_SAT: entries.CP_SAT ?? entry('CP_SAT'),
    },
    bestTotalPriorityScore: 0,
    bestScoreAlgorithms: ['GREEDY', 'BACKTRACKING', 'CP_SAT'],
    fastestAlgorithm: 'GREEDY',
  };
}

function entry(
  algorithm: AllocationAlgorithmType,
  allocations: AllocationDto[] = [],
  rejectedRequests: RejectedRequestDto[] = [],
): AllocationComparisonEntry {
  return {
    algorithm,
    measuredExecutionTimeMs: 1,
    allocationResult: {
      allocations,
      rejectedRequests,
      statistics: {
        totalRequests: allocations.length + rejectedRequests.length,
        allocatedRequests: allocations.length,
        rejectedRequests: rejectedRequests.length,
        algorithmExecutionTimeMs: 1,
        totalPriorityScore: allocations.reduce((sum, item) => sum + item.request.priority, 0),
        exploredStates: 0,
        stoppedByLimit: false,
        algorithmStatus: algorithm === 'CP_SAT' ? 'OPTIMAL' : null,
        objectiveValue: 0,
      },
    },
  };
}

function allocation(
  request: AllocationRequestDto,
  assignedResources: ResourceDto[],
): AllocationDto {
  return { request, assignedResources };
}

function rejection(request: AllocationRequestDto, reason: string): RejectedRequestDto {
  return { request, reason };
}

function allocationRequest(id: string, name: string): AllocationRequestDto {
  return {
    id,
    name,
    startTime: '2026-07-01T10:00:00',
    durationMinutes: 60,
    priority: 10,
    resourceRequirements: [],
  };
}

function resource(id: string, name: string, type = 'ROOM'): ResourceDto {
  return {
    id,
    name,
    type,
    capacities: {},
    availability: [],
  };
}
