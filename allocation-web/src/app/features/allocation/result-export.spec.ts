import {
  AllocationAlgorithmType,
  AllocationApiRequest,
  AllocationApiResponse,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
  AllocationComparisonEntry,
  AllocationStatistics,
  ResourceDto,
} from '../../core/models/allocation-api.models';
import {
  comparisonResultToCsv,
  escapeCsvValue,
  executionResultToCsv,
  serializeComparisonResult,
  serializeExecutionResult,
} from './result-export';

describe('result export utilities', () => {
  it('should serialize execution schema version 1', () => {
    expect(executionJson()).toMatchObject({ schemaVersion: 1 });
  });

  it('should serialize the execution export type', () => {
    expect(executionJson()).toMatchObject({ exportType: 'EXECUTION_RESULT' });
  });

  it('should include the original execution request', () => {
    expect(executionJson()).toMatchObject({ request: executionRequest() });
  });

  it('should include the complete execution response', () => {
    expect(executionJson()).toMatchObject({ response: executionResponse() });
  });

  it('should serialize comparison schema version 1', () => {
    expect(comparisonJson()).toMatchObject({ schemaVersion: 1 });
  });

  it('should serialize the comparison export type', () => {
    expect(comparisonJson()).toMatchObject({ exportType: 'COMPARISON_RESULT' });
  });

  it('should include the original comparison request', () => {
    expect(comparisonJson()).toMatchObject({ request: comparisonRequest() });
  });

  it('should include all three comparison algorithms', () => {
    const value = comparisonJson() as { response: AllocationComparisonApiResponse };

    expect(Object.keys(value.response.results).sort()).toEqual([
      'BACKTRACKING',
      'CP_SAT',
      'GREEDY',
    ]);
    expect(value.response).toEqual(comparisonResponse());
  });

  it('should use the expected execution CSV header', () => {
    expect(executionCsvLines()[0]).toBe(
      'requestId,requestName,status,priority,startTime,durationMinutes,assignedResourceIds,assignedResourceNames,rejectionReason,selectionMode,requestedAlgorithm,executedAlgorithm,goal,totalPriorityScore,measuredExecutionTimeMs,algorithmExecutionTimeMs,algorithmStatus,exploredStates,stoppedByLimit,objectiveValue',
    );
  });

  it('should export an accepted request row', () => {
    const accepted = executionCsvLines()[2].split(',');

    expect(accepted[0]).toBe('REQ_ACCEPTED');
    expect(accepted[2]).toBe('ACCEPTED');
    expect(accepted[8]).toBe('');
  });

  it('should export a rejected request row', () => {
    const rejected = executionCsvLines()[1].split(',');

    expect(rejected[0]).toBe('REQ_REJECTED');
    expect(rejected[2]).toBe('REJECTED');
    expect(rejected[6]).toBe('');
    expect(rejected[7]).toBe('');
    expect(rejected[8]).toBe('No matching room');
  });

  it('should preserve the original execution request order', () => {
    expect(
      executionCsvLines()
        .slice(1, 3)
        .map((line) => line.split(',')[0]),
    ).toEqual(['REQ_REJECTED', 'REQ_ACCEPTED']);
  });

  it('should join multiple assigned resources with a stable separator', () => {
    const accepted = executionCsvLines()[2].split(',');

    expect(accepted[6]).toBe('R_ROOM | R_STAFF');
    expect(accepted[7]).toBe('Main room | Assistant One');
  });

  it('should escape a comma', () => {
    expect(escapeCsvValue('Room, first floor')).toBe('"Room, first floor"');
  });

  it('should escape a quote', () => {
    expect(escapeCsvValue('Room "A"')).toBe('"Room ""A"""');
  });

  it('should escape a newline', () => {
    expect(escapeCsvValue('Line one\nLine two')).toBe('"Line one\nLine two"');
  });

  it('should protect a formula beginning with equals', () => {
    expect(escapeCsvValue('=SUM(A1:A2)')).toBe("'=SUM(A1:A2)");
  });

  it('should protect a formula beginning with plus', () => {
    expect(escapeCsvValue('+SUM(A1:A2)')).toBe("'+SUM(A1:A2)");
  });

  it('should protect a string beginning with minus', () => {
    expect(escapeCsvValue('-danger')).toBe("'-danger");
  });

  it('should protect a formula beginning with at sign', () => {
    expect(escapeCsvValue('@SUM(A1:A2)')).toBe("'@SUM(A1:A2)");
  });

  it('should protect formula markers after leading whitespace', () => {
    expect(escapeCsvValue('  =SUM(A1:A2)')).toBe("'  =SUM(A1:A2)");
  });

  it('should protect an asterisk-prefixed string', () => {
    expect(escapeCsvValue('*danger')).toBe("'*danger");
  });

  it('should keep a negative numeric priority numeric', () => {
    expect(escapeCsvValue(-5)).toBe('-5');
  });

  it('should use CRLF row endings and a final newline', () => {
    const csv = executionResultToCsv(executionRequest(), executionResponse());

    expect(csv).toContain('\r\n');
    expect(csv.endsWith('\r\n')).toBe(true);
  });

  it('should always order comparison rows by algorithm', () => {
    expect(
      comparisonCsvLines()
        .slice(1, 4)
        .map((line) => line.split(',')[0]),
    ).toEqual(['GREEDY', 'BACKTRACKING', 'CP_SAT']);
  });

  it('should mark every best-score comparison algorithm', () => {
    const rows = comparisonRowsByAlgorithm();

    expect(rows.GREEDY[1]).toBe('false');
    expect(rows.BACKTRACKING[1]).toBe('true');
    expect(rows.CP_SAT[1]).toBe('true');
  });

  it('should mark only the fastest measured comparison algorithm', () => {
    const rows = comparisonRowsByAlgorithm();

    expect(rows.GREEDY[2]).toBe('true');
    expect(rows.BACKTRACKING[2]).toBe('false');
    expect(rows.CP_SAT[2]).toBe('false');
  });

  it('should export a null algorithm status as an empty value', () => {
    const rows = comparisonRowsByAlgorithm();

    expect(rows.GREEDY[10]).toBe('');
    expect(rows.CP_SAT[10]).toBe('OPTIMAL');
  });

  function executionJson(): unknown {
    return JSON.parse(serializeExecutionResult(executionRequest(), executionResponse())) as unknown;
  }

  function comparisonJson(): unknown {
    return JSON.parse(
      serializeComparisonResult(comparisonRequest(), comparisonResponse()),
    ) as unknown;
  }

  function executionCsvLines(): string[] {
    return executionResultToCsv(executionRequest(), executionResponse()).split('\r\n');
  }

  function comparisonCsvLines(): string[] {
    return comparisonResultToCsv(comparisonRequest(), comparisonResponse()).split('\r\n');
  }

  function comparisonRowsByAlgorithm(): Record<AllocationAlgorithmType, string[]> {
    const rows = comparisonCsvLines()
      .slice(1, 4)
      .map((line) => line.split(','));
    return Object.fromEntries(rows.map((row) => [row[0], row])) as Record<
      AllocationAlgorithmType,
      string[]
    >;
  }
});

function executionRequest(): AllocationApiRequest {
  return {
    selectionMode: 'EXPLICIT',
    algorithm: 'CP_SAT',
    backtrackingTimeLimitMs: 1000,
    cpSatTimeLimitSeconds: 1,
    resources: [
      resource('R_ROOM', 'Main room', 'ROOM'),
      resource('R_STAFF', 'Assistant One', 'STAFF'),
    ],
    requests: [
      request('REQ_REJECTED', 'Rejected exam', 9),
      request('REQ_ACCEPTED', 'Accepted exam', 10),
    ],
  };
}

function comparisonRequest(): AllocationComparisonApiRequest {
  const request = executionRequest();
  return {
    backtrackingTimeLimitMs: request.backtrackingTimeLimitMs,
    cpSatTimeLimitSeconds: request.cpSatTimeLimitSeconds,
    resources: request.resources,
    requests: request.requests,
  };
}

function executionResponse(): AllocationApiResponse {
  const requestValue = executionRequest();
  return {
    selectionMode: 'EXPLICIT',
    requestedAlgorithm: 'CP_SAT',
    executedAlgorithm: 'CP_SAT',
    goal: null,
    selectionReason: 'CP-SAT was selected explicitly.',
    measuredExecutionTimeMs: 12.5,
    allocations: [
      {
        request: requestValue.requests[1],
        assignedResources: requestValue.resources,
      },
    ],
    rejectedRequests: [
      {
        request: requestValue.requests[0],
        reason: 'No matching room',
      },
    ],
    statistics: statistics(19, 1, 1, 'OPTIMAL'),
  };
}

function comparisonResponse(): AllocationComparisonApiResponse {
  return {
    results: {
      CP_SAT: comparisonEntry('CP_SAT', 19, 2.75, 'OPTIMAL'),
      GREEDY: comparisonEntry('GREEDY', 10, 0.25, null),
      BACKTRACKING: comparisonEntry('BACKTRACKING', 19, 1.5, null),
    },
    bestTotalPriorityScore: 19,
    bestScoreAlgorithms: ['BACKTRACKING', 'CP_SAT'],
    fastestAlgorithm: 'GREEDY',
  };
}

function comparisonEntry(
  algorithm: AllocationAlgorithmType,
  score: number,
  measuredExecutionTimeMs: number,
  algorithmStatus: string | null,
): AllocationComparisonEntry {
  return {
    algorithm,
    measuredExecutionTimeMs,
    allocationResult: {
      allocations: [],
      rejectedRequests: [],
      statistics: statistics(score, score === 10 ? 1 : 2, score === 10 ? 1 : 0, algorithmStatus),
    },
  };
}

function statistics(
  score: number,
  allocatedRequests: number,
  rejectedRequests: number,
  algorithmStatus: string | null,
): AllocationStatistics {
  return {
    totalRequests: 2,
    allocatedRequests,
    rejectedRequests,
    algorithmExecutionTimeMs: 2,
    totalPriorityScore: score,
    exploredStates: 7,
    stoppedByLimit: false,
    algorithmStatus,
    objectiveValue: score === 19 ? 59 : 31,
  };
}

function request(id: string, name: string, priority: number) {
  return {
    id,
    name,
    startTime: '2026-07-01T10:00:00',
    durationMinutes: 120,
    priority,
    resourceRequirements: [
      {
        resourceType: 'ROOM',
        quantity: 1,
        requiredCapacities: { people: 30 },
      },
    ],
  };
}

function resource(id: string, name: string, type: string): ResourceDto {
  const capacities: Record<string, number> = type === 'ROOM' ? { people: 100 } : {};

  return {
    id,
    name,
    type,
    capacities,
    availability: [
      {
        start: '2026-07-01T08:00:00',
        end: '2026-07-01T18:00:00',
      },
    ],
  };
}
