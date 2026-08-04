import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
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
import { ComparisonResultComponent } from './comparison-result.component';

const downloadTextFileMock = vi.fn<(content: string, fileName: string, mimeType: string) => void>();

describe('ComparisonResultComponent', () => {
  let fixture: ComponentFixture<ComparisonResultComponent>;
  let component: ComparisonResultComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ComparisonResultComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: DOWNLOAD_TEXT_FILE, useValue: downloadTextFileMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ComparisonResultComponent);
    component = fixture.componentInstance;
    downloadTextFileMock.mockReset();
  });

  it('should show JSON and CSV export actions for a complete result pair', () => {
    renderCompleteResult();

    expect(button('comparison-export-json').textContent?.trim()).toBe('EXPORT JSON');
    expect(button('comparison-export-csv').textContent?.trim()).toBe('EXPORT CSV');
  });

  it('should export comparison JSON with the expected filename', () => {
    renderCompleteResult();

    button('comparison-export-json').click();

    expect(downloadTextFileMock).toHaveBeenCalledOnce();
    expect(downloadTextFileMock.mock.calls[0][1]).toBe('allocation-comparison-result.json');
    expect(downloadTextFileMock.mock.calls[0][2]).toBe('application/json');
    expect(JSON.parse(downloadTextFileMock.mock.calls[0][0]) as unknown).toMatchObject({
      exportType: 'COMPARISON_RESULT',
      request: comparisonRequest(),
      response: comparisonResponse(),
    });
  });

  it('should export comparison CSV with the expected filename', () => {
    renderCompleteResult();

    button('comparison-export-csv').click();

    expect(downloadTextFileMock).toHaveBeenCalledOnce();
    expect(downloadTextFileMock.mock.calls[0][1]).toBe('allocation-comparison-result.csv');
    expect(downloadTextFileMock.mock.calls[0][2]).toBe('text/csv;charset=utf-8');
    expect(downloadTextFileMock.mock.calls[0][0]).toContain('algorithm,isBestScore');
  });

  it('should hide export actions without a request', () => {
    component.result = comparisonResponse();
    fixture.detectChanges();

    expect(query('[data-testid="comparison-export-json"]')).toBeNull();
    expect(query('[data-testid="comparison-export-csv"]')).toBeNull();
    expect(query('[data-testid="request-level-comparison"]')).toBeNull();
  });

  it('should hide export actions without a result', () => {
    component.request = comparisonRequest();
    fixture.detectChanges();

    expect(query('[data-testid="comparison-export-json"]')).toBeNull();
    expect(query('[data-testid="comparison-export-csv"]')).toBeNull();
  });

  it('should keep the summary table and render all request-level algorithms', () => {
    renderCompleteResult();

    const tables = fixture.nativeElement.querySelectorAll('table') as NodeListOf<HTMLTableElement>;
    const requestLevelText = query('[data-testid="request-level-comparison"]')?.textContent ?? '';

    expect(tables.length).toBe(2);
    expect(tables[0].textContent).toContain('Total priority score');
    expect(requestLevelText).toContain('GREEDY');
    expect(requestLevelText).toContain('BACKTRACKING');
    expect(requestLevelText).toContain('CP_SAT');
  });

  it('should follow the original request order in the request-level table', () => {
    renderCompleteResult();

    const rows = requestRows();

    expect(rows.map((row) => row.querySelector('td')?.textContent?.trim())).toEqual([
      'REQ_SECOND',
      'REQ_FIRST',
      'REQ_UNKNOWN',
    ]);
  });

  it('should render accepted resources, rejected reasons, and unknown outcomes', () => {
    renderCompleteResult();

    const rows = requestRows();
    const secondGreedy = outcome(rows[0], 'GREEDY');
    const firstGreedy = outcome(rows[1], 'GREEDY');
    const unknownGreedy = outcome(rows[2], 'GREEDY');

    expect(secondGreedy.textContent).toContain('REJECTED');
    expect(secondGreedy.textContent).toContain('No suitable large room.');
    expect(firstGreedy.textContent).toContain('ACCEPTED');
    expect(firstGreedy.textContent).toContain('ROOM_SMALL - Small room');
    expect(unknownGreedy.textContent).toContain('UNKNOWN');
    expect(unknownGreedy.textContent).toContain('No consistent result data');
  });

  it('should change algorithm details without mutating the inputs', async () => {
    renderCompleteResult();
    const requestBefore = structuredClone(component.request);
    const resultBefore = structuredClone(component.result);

    expect(query('[data-testid="accepted-detail-list"]')?.textContent).toContain('REQ_FIRST');
    expect(query('[data-testid="accepted-detail-list"]')?.textContent).toContain(
      'ROOM_SMALL - Small room',
    );
    expect(query('[data-testid="rejected-detail-list"]')?.textContent).toContain('REQ_SECOND');

    button('algorithm-detail-CP_SAT').click();
    await fixture.whenStable();

    const details = query('.algorithm-details')?.textContent ?? '';
    expect(component.selectedAlgorithm()).toBe('CP_SAT');
    expect(details).toContain('REQ_SECOND');
    expect(details).toContain('REQ_FIRST');
    expect(component.request).toEqual(requestBefore);
    expect(component.result).toEqual(resultBefore);
  });

  it('should describe an accepted allocation without assigned resources', () => {
    const response = comparisonResponse();
    response.results.GREEDY.allocationResult.allocations[0].assignedResources = [];
    component.request = comparisonRequest();
    component.result = response;
    fixture.detectChanges();

    expect(query('[data-testid="accepted-detail-list"]')?.textContent).toContain(
      'No assigned resources',
    );
  });

  it('should update selected algorithm details through zoneless signal change detection', async () => {
    renderCompleteResult();

    button('algorithm-detail-BACKTRACKING').click();
    await fixture.whenStable();

    expect(query('#algorithm-details-heading')?.textContent?.trim()).toBe('BACKTRACKING');
    expect(button('algorithm-detail-BACKTRACKING').getAttribute('aria-pressed')).toBe('true');
  });

  function renderCompleteResult(): void {
    component.request = comparisonRequest();
    component.result = comparisonResponse();
    fixture.detectChanges();
  }

  function query(selector: string): Element | null {
    return (fixture.nativeElement as HTMLElement).querySelector(selector);
  }

  function button(testId: string): HTMLButtonElement {
    const element = query(`[data-testid="${testId}"]`);
    if (!(element instanceof HTMLButtonElement)) {
      throw new Error(`Expected button ${testId}.`);
    }
    return element;
  }

  function requestRows(): HTMLTableRowElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLTableRowElement>(
        '[data-testid="request-comparison-row"]',
      ),
    );
  }

  function outcome(row: HTMLTableRowElement, algorithm: AllocationAlgorithmType): HTMLElement {
    const element = row.querySelector(`[data-testid="request-outcome-${algorithm}"]`);
    if (!(element instanceof HTMLElement)) {
      throw new Error(`Expected ${algorithm} request outcome.`);
    }
    return element;
  }
});

function comparisonRequest(): AllocationComparisonApiRequest {
  return {
    resources: [resource('ROOM_SMALL', 'Small room'), resource('ROOM_LARGE', 'Large room')],
    requests: requestFixtures(),
    backtrackingTimeLimitMs: 1000,
    cpSatTimeLimitSeconds: 1,
  };
}

function comparisonResponse(): AllocationComparisonApiResponse {
  const [second, first, unknown] = requestFixtures();
  const smallRoom = resource('ROOM_SMALL', 'Small room');
  const largeRoom = resource('ROOM_LARGE', 'Large room');

  return {
    results: {
      GREEDY: entry(
        'GREEDY',
        10,
        null,
        [allocation(first, [smallRoom])],
        [rejection(second, 'No suitable large room.')],
      ),
      BACKTRACKING: entry(
        'BACKTRACKING',
        19,
        null,
        [allocation(second, [largeRoom]), allocation(first, [smallRoom])],
        [rejection(unknown, 'No remaining room.')],
      ),
      CP_SAT: entry(
        'CP_SAT',
        19,
        'OPTIMAL',
        [allocation(second, [largeRoom])],
        [rejection(first, 'Lower objective contribution.')],
      ),
    },
    bestTotalPriorityScore: 19,
    bestScoreAlgorithms: ['BACKTRACKING', 'CP_SAT'],
    fastestAlgorithm: 'GREEDY',
  };
}

function entry(
  algorithm: AllocationAlgorithmType,
  score: number,
  algorithmStatus: string | null,
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
        totalRequests: 3,
        allocatedRequests: allocations.length,
        rejectedRequests: rejectedRequests.length,
        algorithmExecutionTimeMs: 1,
        totalPriorityScore: score,
        exploredStates: 0,
        stoppedByLimit: false,
        algorithmStatus,
        objectiveValue: 0,
      },
    },
  };
}

function requestFixtures(): AllocationRequestDto[] {
  return [
    allocationRequest('REQ_SECOND', 'Second request', 9),
    allocationRequest('REQ_FIRST', 'First request', 10),
    allocationRequest('REQ_UNKNOWN', 'Unknown request', 5),
  ];
}

function allocationRequest(id: string, name: string, priority: number): AllocationRequestDto {
  return {
    id,
    name,
    startTime: '2026-07-01T10:00:00',
    durationMinutes: 60,
    priority,
    resourceRequirements: [],
  };
}

function resource(id: string, name: string): ResourceDto {
  return {
    id,
    name,
    type: 'ROOM',
    capacities: {},
    availability: [],
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
