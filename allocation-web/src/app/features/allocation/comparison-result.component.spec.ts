import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
import {
  AllocationAlgorithmType,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
  AllocationComparisonEntry,
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
  });

  it('should hide export actions without a result', () => {
    component.request = comparisonRequest();
    fixture.detectChanges();

    expect(query('[data-testid="comparison-export-json"]')).toBeNull();
    expect(query('[data-testid="comparison-export-csv"]')).toBeNull();
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
});

function comparisonRequest(): AllocationComparisonApiRequest {
  return {
    resources: [],
    requests: [],
    backtrackingTimeLimitMs: 1000,
    cpSatTimeLimitSeconds: 1,
  };
}

function comparisonResponse(): AllocationComparisonApiResponse {
  return {
    results: {
      GREEDY: entry('GREEDY', 10, null),
      BACKTRACKING: entry('BACKTRACKING', 19, null),
      CP_SAT: entry('CP_SAT', 19, 'OPTIMAL'),
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
): AllocationComparisonEntry {
  return {
    algorithm,
    measuredExecutionTimeMs: 1,
    allocationResult: {
      allocations: [],
      rejectedRequests: [],
      statistics: {
        totalRequests: 2,
        allocatedRequests: score === 10 ? 1 : 2,
        rejectedRequests: score === 10 ? 1 : 0,
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
