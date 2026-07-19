import {
  AllocationAlgorithmType,
  AllocationApiRequest,
  AllocationApiResponse,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
} from '../../core/models/allocation-api.models';

export interface ExecutionResultExportFile {
  schemaVersion: 1;
  exportType: 'EXECUTION_RESULT';
  request: AllocationApiRequest;
  response: AllocationApiResponse;
}

export interface ComparisonResultExportFile {
  schemaVersion: 1;
  exportType: 'COMPARISON_RESULT';
  request: AllocationComparisonApiRequest;
  response: AllocationComparisonApiResponse;
}

const EXECUTION_CSV_COLUMNS = [
  'requestId',
  'requestName',
  'status',
  'priority',
  'startTime',
  'durationMinutes',
  'assignedResourceIds',
  'assignedResourceNames',
  'rejectionReason',
  'selectionMode',
  'requestedAlgorithm',
  'executedAlgorithm',
  'goal',
  'totalPriorityScore',
  'measuredExecutionTimeMs',
  'algorithmExecutionTimeMs',
  'algorithmStatus',
  'exploredStates',
  'stoppedByLimit',
  'objectiveValue',
] as const;

const COMPARISON_CSV_COLUMNS = [
  'algorithm',
  'isBestScore',
  'isFastest',
  'totalPriorityScore',
  'allocatedRequests',
  'rejectedRequests',
  'measuredExecutionTimeMs',
  'algorithmExecutionTimeMs',
  'exploredStates',
  'stoppedByLimit',
  'algorithmStatus',
  'objectiveValue',
] as const;

const COMPARISON_ALGORITHMS: AllocationAlgorithmType[] = ['GREEDY', 'BACKTRACKING', 'CP_SAT'];

type CsvValue = string | number | boolean | null | undefined;

export function serializeExecutionResult(
  request: AllocationApiRequest,
  response: AllocationApiResponse,
): string {
  const file: ExecutionResultExportFile = {
    schemaVersion: 1,
    exportType: 'EXECUTION_RESULT',
    request,
    response,
  };

  return JSON.stringify(file, null, 2);
}

export function serializeComparisonResult(
  request: AllocationComparisonApiRequest,
  response: AllocationComparisonApiResponse,
): string {
  const file: ComparisonResultExportFile = {
    schemaVersion: 1,
    exportType: 'COMPARISON_RESULT',
    request,
    response,
  };

  return JSON.stringify(file, null, 2);
}

export function executionResultToCsv(
  request: AllocationApiRequest,
  response: AllocationApiResponse,
): string {
  const allocationsByRequestId = new Map(
    response.allocations.map((allocation) => [allocation.request.id, allocation]),
  );
  const rejectionsByRequestId = new Map(
    response.rejectedRequests.map((rejection) => [rejection.request.id, rejection]),
  );
  const statistics = response.statistics;
  const rows: CsvValue[][] = request.requests.map((allocationRequest) => {
    const allocation = allocationsByRequestId.get(allocationRequest.id);
    const rejection = rejectionsByRequestId.get(allocationRequest.id);

    return [
      allocationRequest.id,
      allocationRequest.name,
      allocation ? 'ACCEPTED' : 'REJECTED',
      allocationRequest.priority,
      allocationRequest.startTime,
      allocationRequest.durationMinutes,
      allocation?.assignedResources.map((resource) => resource.id).join(' | ') ?? '',
      allocation?.assignedResources.map((resource) => resource.name).join(' | ') ?? '',
      rejection?.reason ?? '',
      response.selectionMode,
      response.requestedAlgorithm,
      response.executedAlgorithm,
      response.goal,
      statistics.totalPriorityScore,
      response.measuredExecutionTimeMs,
      statistics.algorithmExecutionTimeMs,
      statistics.algorithmStatus,
      statistics.exploredStates,
      statistics.stoppedByLimit,
      statistics.objectiveValue,
    ];
  });

  return csvDocument(EXECUTION_CSV_COLUMNS, rows);
}

export function comparisonResultToCsv(
  _request: AllocationComparisonApiRequest,
  response: AllocationComparisonApiResponse,
): string {
  const rows: CsvValue[][] = COMPARISON_ALGORITHMS.map((algorithm) => {
    const entry = response.results[algorithm];
    const statistics = entry.allocationResult.statistics;

    return [
      algorithm,
      response.bestScoreAlgorithms.includes(algorithm),
      response.fastestAlgorithm === algorithm,
      statistics.totalPriorityScore,
      statistics.allocatedRequests,
      statistics.rejectedRequests,
      entry.measuredExecutionTimeMs,
      statistics.algorithmExecutionTimeMs,
      statistics.exploredStates,
      statistics.stoppedByLimit,
      statistics.algorithmStatus,
      statistics.objectiveValue,
    ];
  });

  return csvDocument(COMPARISON_CSV_COLUMNS, rows);
}

export function escapeCsvValue(value: CsvValue): string {
  if (value === null || value === undefined) {
    return '';
  }

  let text = String(value);
  if (typeof value === 'string' && /^\s*[=+\-@*]/.test(value)) {
    text = `'${value}`;
  }

  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function csvDocument(columns: readonly string[], rows: CsvValue[][]): string {
  return [columns.map(escapeCsvValue).join(','), ...rows.map(csvRow)].join('\r\n') + '\r\n';
}

function csvRow(values: CsvValue[]): string {
  return values.map(escapeCsvValue).join(',');
}
