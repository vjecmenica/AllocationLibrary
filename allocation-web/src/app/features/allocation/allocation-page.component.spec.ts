import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AllocationApiService } from '../../core/api/allocation-api.service';
import {
  AllocationApiResponse,
  AllocationComparisonApiResponse,
} from '../../core/models/allocation-api.models';
import { AllocationPageComponent } from './allocation-page.component';

describe('AllocationPageComponent', () => {
  let fixture: ComponentFixture<AllocationPageComponent>;
  let component: AllocationPageComponent;
  let allocationApiService: {
    getHealth: ReturnType<typeof vi.fn>;
    executeAllocation: ReturnType<typeof vi.fn>;
    compareAllocations: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    allocationApiService = {
      getHealth: vi.fn(() => of({ status: 'UP' })),
      executeAllocation: vi.fn(() => of(executionResponse())),
      compareAllocations: vi.fn(() => of(comparisonResponse())),
    };

    await TestBed.configureTestingModule({
      imports: [AllocationPageComponent],
      providers: [
        {
          provide: AllocationApiService,
          useValue: allocationApiService,
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AllocationPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should show algorithm select in explicit mode', () => {
    expect(query('[data-testid="algorithm-select"]')).not.toBeNull();
    expect(query('[data-testid="goal-select"]')).toBeNull();
  });

  it('should show goal select in auto mode', () => {
    component.setMode('AUTO');
    fixture.detectChanges();

    expect(query('[data-testid="goal-select"]')).not.toBeNull();
    expect(query('[data-testid="algorithm-select"]')).toBeNull();
  });

  it('should hide algorithm and goal controls in compare mode', () => {
    component.setMode('COMPARE');
    fixture.detectChanges();

    expect(query('[data-testid="algorithm-select"]')).toBeNull();
    expect(query('[data-testid="goal-select"]')).toBeNull();
  });

  it('should use BALANCED as the default auto goal', () => {
    expect(component.selectedGoal).toBe('BALANCED');
  });

  it('should call AllocationApiService with sample scenario when running allocation', () => {
    clickPrimaryAction();

    expect(allocationApiService.executeAllocation).toHaveBeenCalledOnce();
    expect(allocationApiService.executeAllocation).toHaveBeenCalledWith(
      expect.objectContaining({
        selectionMode: 'EXPLICIT',
        algorithm: 'GREEDY',
        resources: expect.arrayContaining([
          expect.objectContaining({ id: 'R_BIG' }),
          expect.objectContaining({ id: 'R_SMALL' }),
        ]),
        requests: expect.arrayContaining([
          expect.objectContaining({ id: 'REQ_SMALL' }),
          expect.objectContaining({ id: 'REQ_BIG' }),
        ]),
      }),
    );
  });

  it('should display executed algorithm after a successful execution response', () => {
    clickPrimaryAction();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('CP_SAT');
  });

  it('should display total priority score after a successful execution response', () => {
    clickPrimaryAction();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Score 19');
  });

  it('should display backend ApiErrorResponse message', () => {
    allocationApiService.executeAllocation.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({
        status: 400,
        error: {
          status: 400,
          error: 'Bad Request',
          message: 'Algorithm must not be null for explicit selection.',
          path: '/api/allocations',
        },
      })),
    );

    clickPrimaryAction();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Algorithm must not be null for explicit selection.');
  });

  it('should display all algorithms after a successful comparison response', () => {
    component.setMode('COMPARE');
    fixture.detectChanges();
    clickPrimaryAction();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('GREEDY');
    expect(fixture.nativeElement.textContent).toContain('BACKTRACKING');
    expect(fixture.nativeElement.textContent).toContain('CP_SAT');
  });

  it('should send changed backtracking numeric limit in comparison request', async () => {
    component.setMode('COMPARE');
    fixture.detectChanges();

    await changeNumberInput('backtrackingTimeLimitMs', '500');

    expect(() => clickPrimaryAction()).not.toThrow();
    expect(allocationApiService.compareAllocations).toHaveBeenCalledOnce();
    expect(allocationApiService.compareAllocations.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        backtrackingTimeLimitMs: 500,
      }),
    );
  });

  it('should send changed CP-SAT numeric limit in execution request', async () => {
    await changeNumberInput('cpSatTimeLimitSeconds', '0.5');

    clickPrimaryAction();

    expect(allocationApiService.executeAllocation).toHaveBeenCalledOnce();
    expect(allocationApiService.executeAllocation.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        cpSatTimeLimitSeconds: 0.5,
      }),
    );
  });

  it('should omit time limit properties when both numeric inputs are cleared', async () => {
    await changeNumberInput('backtrackingTimeLimitMs', '');
    await changeNumberInput('cpSatTimeLimitSeconds', '');

    expect(component.backtrackingTimeLimitMs).toBeNull();
    expect(component.cpSatTimeLimitSeconds).toBeNull();

    clickPrimaryAction();

    const request = allocationApiService.executeAllocation.mock.calls[0][0];
    expect(request).not.toHaveProperty('backtrackingTimeLimitMs');
    expect(request).not.toHaveProperty('cpSatTimeLimitSeconds');
  });

  it('should not leave loading active or block another request after numeric input changes', async () => {
    await changeNumberInput('backtrackingTimeLimitMs', '500');

    clickPrimaryAction();
    expect(component.isLoading).toBe(false);

    clickPrimaryAction();

    expect(component.isLoading).toBe(false);
    expect(allocationApiService.executeAllocation).toHaveBeenCalledTimes(2);
  });

  function query(selector: string): Element | null {
    return fixture.nativeElement.querySelector(selector);
  }

  function clickPrimaryAction(): void {
    const button = query('.primary-action') as HTMLButtonElement;
    button.click();
  }

  async function changeNumberInput(name: string, value: string): Promise<void> {
    const input = query(`input[name="${name}"]`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
  }
});

function executionResponse(): AllocationApiResponse {
  return {
    selectionMode: 'EXPLICIT',
    requestedAlgorithm: 'CP_SAT',
    executedAlgorithm: 'CP_SAT',
    goal: null,
    selectionReason: 'CP-SAT was executed because the caller explicitly selected it.',
    measuredExecutionTimeMs: 14.25,
    allocations: [
      {
        request: {
          id: 'REQ_SMALL',
          name: 'Small exam',
          startTime: '2026-07-01T10:00:00',
          durationMinutes: 120,
          priority: 10,
          resourceRequirements: [],
        },
        assignedResources: [
          {
            id: 'R_SMALL',
            name: 'Small room',
            type: 'ROOM',
            capacities: { people: 30 },
            availability: [],
          },
        ],
      },
    ],
    rejectedRequests: [],
    statistics: {
      totalRequests: 2,
      allocatedRequests: 2,
      rejectedRequests: 0,
      algorithmExecutionTimeMs: 14,
      totalPriorityScore: 19,
      exploredStates: 0,
      stoppedByLimit: false,
      algorithmStatus: 'OPTIMAL',
      objectiveValue: 59,
    },
  };
}

function comparisonResponse(): AllocationComparisonApiResponse {
  return {
    results: {
      GREEDY: comparisonEntry('GREEDY', 10, 1, 1, null),
      BACKTRACKING: comparisonEntry('BACKTRACKING', 19, 2, 0, null),
      CP_SAT: comparisonEntry('CP_SAT', 19, 2, 0, 'OPTIMAL'),
    },
    bestTotalPriorityScore: 19,
    bestScoreAlgorithms: ['BACKTRACKING', 'CP_SAT'],
    fastestAlgorithm: 'GREEDY',
  };
}

function comparisonEntry(
  algorithm: 'GREEDY' | 'BACKTRACKING' | 'CP_SAT',
  totalPriorityScore: number,
  allocatedRequests: number,
  rejectedRequests: number,
  algorithmStatus: string | null,
) {
  return {
    algorithm,
    measuredExecutionTimeMs: 1.25,
    allocationResult: {
      allocations: [],
      rejectedRequests: [],
      statistics: {
        totalRequests: 2,
        allocatedRequests,
        rejectedRequests,
        algorithmExecutionTimeMs: 1,
        totalPriorityScore,
        exploredStates: 0,
        stoppedByLimit: false,
        algorithmStatus,
        objectiveValue: 0,
      },
    },
  };
}
