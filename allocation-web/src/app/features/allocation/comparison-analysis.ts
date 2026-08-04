import {
  AllocationAlgorithmType,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
  AllocationRequestDto,
  ResourceDto,
} from '../../core/models/allocation-api.models';

export const COMPARISON_ALGORITHMS: readonly AllocationAlgorithmType[] = [
  'GREEDY',
  'BACKTRACKING',
  'CP_SAT',
];

export type RequestOutcomeStatus = 'ACCEPTED' | 'REJECTED' | 'UNKNOWN';

export interface AlgorithmRequestOutcome {
  status: RequestOutcomeStatus;
  assignedResources: readonly ResourceDto[];
  rejectionReason: string | null;
}

export interface RequestComparisonRow {
  request: AllocationRequestDto;
  outcomes: Record<AllocationAlgorithmType, AlgorithmRequestOutcome>;
}

/**
 * Builds request outcomes in the original request order. Missing, duplicated, or conflicting
 * allocation data is reported as UNKNOWN rather than being presented as a valid decision.
 */
export function buildRequestComparisonRows(
  request: AllocationComparisonApiRequest,
  response: AllocationComparisonApiResponse,
): RequestComparisonRow[] {
  const outcomesByAlgorithm = new Map(
    COMPARISON_ALGORITHMS.map((algorithm) => [
      algorithm,
      outcomesForAlgorithm(response, algorithm),
    ]),
  );

  return request.requests.map((allocationRequest) => ({
    request: allocationRequest,
    outcomes: {
      GREEDY: outcomeForRequest(outcomesByAlgorithm.get('GREEDY'), allocationRequest.id),
      BACKTRACKING: outcomeForRequest(
        outcomesByAlgorithm.get('BACKTRACKING'),
        allocationRequest.id,
      ),
      CP_SAT: outcomeForRequest(outcomesByAlgorithm.get('CP_SAT'), allocationRequest.id),
    },
  }));
}

interface AlgorithmOutcomeIndex {
  allocations: Map<string, ResourceDto[][]>;
  rejections: Map<string, string[]>;
}

function outcomesForAlgorithm(
  response: AllocationComparisonApiResponse,
  algorithm: AllocationAlgorithmType,
): AlgorithmOutcomeIndex {
  const result = response.results[algorithm].allocationResult;
  const allocations = new Map<string, ResourceDto[][]>();
  const rejections = new Map<string, string[]>();

  for (const allocation of result.allocations) {
    append(allocations, allocation.request.id, [...allocation.assignedResources]);
  }

  for (const rejection of result.rejectedRequests) {
    append(rejections, rejection.request.id, rejection.reason);
  }

  return { allocations, rejections };
}

function outcomeForRequest(
  index: AlgorithmOutcomeIndex | undefined,
  requestId: string,
): AlgorithmRequestOutcome {
  const allocations = index?.allocations.get(requestId) ?? [];
  const rejections = index?.rejections.get(requestId) ?? [];

  if (allocations.length === 1 && rejections.length === 0) {
    return {
      status: 'ACCEPTED',
      assignedResources: allocations[0],
      rejectionReason: null,
    };
  }

  if (allocations.length === 0 && rejections.length === 1) {
    return {
      status: 'REJECTED',
      assignedResources: [],
      rejectionReason: rejections[0],
    };
  }

  return {
    status: 'UNKNOWN',
    assignedResources: [],
    rejectionReason: null,
  };
}

function append<T>(values: Map<string, T[]>, key: string, value: T): void {
  const existing = values.get(key);
  if (existing) {
    existing.push(value);
  } else {
    values.set(key, [value]);
  }
}
