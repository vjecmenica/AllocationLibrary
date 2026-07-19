import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
import {
  AllocationApiRequest,
  AllocationApiResponse,
} from '../../core/models/allocation-api.models';
import { ExecutionResultComponent } from './execution-result.component';

const downloadTextFileMock = vi.fn<(content: string, fileName: string, mimeType: string) => void>();

describe('ExecutionResultComponent', () => {
  let fixture: ComponentFixture<ExecutionResultComponent>;
  let component: ExecutionResultComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ExecutionResultComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: DOWNLOAD_TEXT_FILE, useValue: downloadTextFileMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ExecutionResultComponent);
    component = fixture.componentInstance;
    downloadTextFileMock.mockReset();
  });

  it('should show JSON and CSV export actions for a complete result pair', () => {
    renderCompleteResult();

    expect(button('execution-export-json').textContent?.trim()).toBe('EXPORT JSON');
    expect(button('execution-export-csv').textContent?.trim()).toBe('EXPORT CSV');
  });

  it('should export execution JSON with the expected filename', () => {
    renderCompleteResult();

    button('execution-export-json').click();

    expect(downloadTextFileMock).toHaveBeenCalledOnce();
    expect(downloadTextFileMock.mock.calls[0][1]).toBe('allocation-execution-result.json');
    expect(downloadTextFileMock.mock.calls[0][2]).toBe('application/json');
    expect(JSON.parse(downloadTextFileMock.mock.calls[0][0]) as unknown).toMatchObject({
      exportType: 'EXECUTION_RESULT',
      request: executionRequest(),
      response: executionResponse(),
    });
  });

  it('should export execution CSV with the expected filename', () => {
    renderCompleteResult();

    button('execution-export-csv').click();

    expect(downloadTextFileMock).toHaveBeenCalledOnce();
    expect(downloadTextFileMock.mock.calls[0][1]).toBe('allocation-execution-result.csv');
    expect(downloadTextFileMock.mock.calls[0][2]).toBe('text/csv;charset=utf-8');
    expect(downloadTextFileMock.mock.calls[0][0]).toContain('requestId,requestName');
  });

  it('should hide export actions without a request', () => {
    component.result = executionResponse();
    fixture.detectChanges();

    expect(query('[data-testid="execution-export-json"]')).toBeNull();
    expect(query('[data-testid="execution-export-csv"]')).toBeNull();
  });

  it('should hide export actions without a result', () => {
    component.request = executionRequest();
    fixture.detectChanges();

    expect(query('[data-testid="execution-export-json"]')).toBeNull();
    expect(query('[data-testid="execution-export-csv"]')).toBeNull();
  });

  function renderCompleteResult(): void {
    component.request = executionRequest();
    component.result = executionResponse();
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

function executionRequest(): AllocationApiRequest {
  return {
    selectionMode: 'EXPLICIT',
    algorithm: 'GREEDY',
    resources: [],
    requests: [request()],
  };
}

function executionResponse(): AllocationApiResponse {
  return {
    selectionMode: 'EXPLICIT',
    requestedAlgorithm: 'GREEDY',
    executedAlgorithm: 'GREEDY',
    goal: null,
    selectionReason: 'Greedy was selected explicitly.',
    measuredExecutionTimeMs: 0.25,
    allocations: [],
    rejectedRequests: [{ request: request(), reason: 'No resource is available.' }],
    statistics: {
      totalRequests: 1,
      allocatedRequests: 0,
      rejectedRequests: 1,
      algorithmExecutionTimeMs: 0,
      totalPriorityScore: 0,
      exploredStates: 0,
      stoppedByLimit: false,
      algorithmStatus: null,
      objectiveValue: 0,
    },
  };
}

function request() {
  return {
    id: 'REQ_1',
    name: 'Request one',
    startTime: '2026-07-01T10:00:00',
    durationMinutes: 60,
    priority: 5,
    resourceRequirements: [
      {
        resourceType: 'ROOM',
        quantity: 1,
        requiredCapacities: {},
      },
    ],
  };
}
