import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { AllocationApiService } from '../../core/api/allocation-api.service';
import {
  AllocationAlgorithmType,
  AllocationApiRequest,
  AllocationApiResponse,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
  AllocationGoal,
  ApiErrorResponse,
  AllocationRequestDto,
  ResourceDto,
} from '../../core/models/allocation-api.models';
import { ComparisonResultComponent } from './comparison-result.component';
import { ExecutionResultComponent } from './execution-result.component';

type PageMode = 'EXPLICIT' | 'AUTO' | 'COMPARE';
type HealthState = 'checking' | 'online' | 'unavailable';

@Component({
  selector: 'app-allocation-page',
  imports: [CommonModule, FormsModule, ExecutionResultComponent, ComparisonResultComponent],
  templateUrl: './allocation-page.component.html',
  styleUrl: './allocation-page.component.scss',
})
export class AllocationPageComponent implements OnInit {
  private readonly allocationApi = inject(AllocationApiService);

  readonly algorithms: AllocationAlgorithmType[] = ['GREEDY', 'BACKTRACKING', 'CP_SAT'];
  readonly goals: AllocationGoal[] = ['FASTEST', 'BALANCED', 'BEST_QUALITY'];
  readonly sampleResources: ResourceDto[] = [
    {
      id: 'R_BIG',
      name: 'Large room',
      type: 'ROOM',
      capacities: { people: 100 },
      availability: [
        {
          start: '2026-07-01T08:00:00',
          end: '2026-07-01T18:00:00',
        },
      ],
    },
    {
      id: 'R_SMALL',
      name: 'Small room',
      type: 'ROOM',
      capacities: { people: 30 },
      availability: [
        {
          start: '2026-07-01T08:00:00',
          end: '2026-07-01T18:00:00',
        },
      ],
    },
  ];
  readonly sampleRequests: AllocationRequestDto[] = [
    {
      id: 'REQ_SMALL',
      name: 'Small exam',
      startTime: '2026-07-01T10:00:00',
      durationMinutes: 120,
      priority: 10,
      resourceRequirements: [
        {
          resourceType: 'ROOM',
          quantity: 1,
          requiredCapacities: { people: 30 },
        },
      ],
    },
    {
      id: 'REQ_BIG',
      name: 'Large exam',
      startTime: '2026-07-01T10:00:00',
      durationMinutes: 120,
      priority: 9,
      resourceRequirements: [
        {
          resourceType: 'ROOM',
          quantity: 1,
          requiredCapacities: { people: 100 },
        },
      ],
    },
  ];

  mode: PageMode = 'EXPLICIT';
  selectedAlgorithm: AllocationAlgorithmType = 'GREEDY';
  selectedGoal: AllocationGoal = 'BALANCED';
  backtrackingTimeLimitMs: number | null = 1000;
  cpSatTimeLimitSeconds: number | null = 1.0;
  readonly healthState = signal<HealthState>('checking');
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly executionResult = signal<AllocationApiResponse | null>(null);
  readonly comparisonResult = signal<AllocationComparisonApiResponse | null>(null);

  ngOnInit(): void {
    this.allocationApi.getHealth().subscribe({
      next: (response) => {
        this.healthState.set(response.status === 'UP' ? 'online' : 'unavailable');
      },
      error: () => {
        this.healthState.set('unavailable');
      },
    });
  }

  runAllocation(): void {
    if (this.isLoading()) {
      return;
    }

    const request = this.createExecutionRequest();

    this.startRequest();
    this.allocationApi
      .executeAllocation(request)
      .pipe(finalize(() => {
        this.isLoading.set(false);
      }))
      .subscribe({
        next: (response) => {
          this.executionResult.set(response);
        },
        error: (error: unknown) => {
          this.errorMessage.set(this.toErrorMessage(error));
        },
      });
  }

  compareAlgorithms(): void {
    if (this.isLoading()) {
      return;
    }

    const request = this.createComparisonRequest();

    this.startRequest();
    this.allocationApi
      .compareAllocations(request)
      .pipe(finalize(() => {
        this.isLoading.set(false);
      }))
      .subscribe({
        next: (response) => {
          this.comparisonResult.set(response);
        },
        error: (error: unknown) => {
          this.errorMessage.set(this.toErrorMessage(error));
        },
      });
  }

  setMode(mode: PageMode): void {
    this.mode = mode;
    this.errorMessage.set(null);
    this.executionResult.set(null);
    this.comparisonResult.set(null);
  }

  healthLabel(): string {
    if (this.healthState() === 'online') {
      return 'API Online';
    }

    if (this.healthState() === 'unavailable') {
      return 'API Unavailable';
    }

    return 'Checking API';
  }

  healthClass(): string {
    return `health-${this.healthState()}`;
  }

  private startRequest(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.executionResult.set(null);
    this.comparisonResult.set(null);
  }

  private createExecutionRequest(): AllocationApiRequest {
    const baseRequest = {
      ...this.optionalLimits(),
      resources: this.sampleResources,
      requests: this.sampleRequests,
    };

    if (this.mode === 'AUTO') {
      return {
        ...baseRequest,
        selectionMode: 'AUTO',
        goal: this.selectedGoal,
      };
    }

    return {
      ...baseRequest,
      selectionMode: 'EXPLICIT',
      algorithm: this.selectedAlgorithm,
    };
  }

  private createComparisonRequest(): AllocationComparisonApiRequest {
    return {
      ...this.optionalLimits(),
      resources: this.sampleResources,
      requests: this.sampleRequests,
    };
  }

  private optionalLimits(): {
    backtrackingTimeLimitMs?: number;
    cpSatTimeLimitSeconds?: number;
  } {
    return {
      ...this.optionalNumber('backtrackingTimeLimitMs', this.backtrackingTimeLimitMs),
      ...this.optionalNumber('cpSatTimeLimitSeconds', this.cpSatTimeLimitSeconds),
    };
  }

  private optionalNumber<T extends 'backtrackingTimeLimitMs' | 'cpSatTimeLimitSeconds'>(
    key: T,
    value: number | null,
  ): Partial<Record<T, number>> {
    if (value === null) {
      return {};
    }

    return { [key]: value } as Partial<Record<T, number>>;
  }

  private toErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && this.isApiErrorResponse(error.error)) {
      return error.error.message;
    }

    return 'The API request could not be completed.';
  }

  private isApiErrorResponse(error: unknown): error is ApiErrorResponse {
    return (
      typeof error === 'object' &&
      error !== null &&
      'message' in error &&
      typeof (error as ApiErrorResponse).message === 'string'
    );
  }
}
