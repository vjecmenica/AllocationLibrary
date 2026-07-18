import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
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
  backtrackingTimeLimitMs = '1000';
  cpSatTimeLimitSeconds = '1.0';
  healthState: HealthState = 'checking';
  isLoading = false;
  errorMessage: string | null = null;
  executionResult: AllocationApiResponse | null = null;
  comparisonResult: AllocationComparisonApiResponse | null = null;

  ngOnInit(): void {
    this.allocationApi.getHealth().subscribe({
      next: (response) => {
        this.healthState = response.status === 'UP' ? 'online' : 'unavailable';
      },
      error: () => {
        this.healthState = 'unavailable';
      },
    });
  }

  runAllocation(): void {
    if (this.isLoading) {
      return;
    }

    this.startRequest();
    this.allocationApi
      .executeAllocation(this.createExecutionRequest())
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          this.executionResult = response;
        },
        error: (error: unknown) => {
          this.errorMessage = this.toErrorMessage(error);
        },
      });
  }

  compareAlgorithms(): void {
    if (this.isLoading) {
      return;
    }

    this.startRequest();
    this.allocationApi
      .compareAllocations(this.createComparisonRequest())
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: (response) => {
          this.comparisonResult = response;
        },
        error: (error: unknown) => {
          this.errorMessage = this.toErrorMessage(error);
        },
      });
  }

  setMode(mode: PageMode): void {
    this.mode = mode;
    this.errorMessage = null;
    this.executionResult = null;
    this.comparisonResult = null;
  }

  healthLabel(): string {
    if (this.healthState === 'online') {
      return 'API Online';
    }

    if (this.healthState === 'unavailable') {
      return 'API Unavailable';
    }

    return 'Checking API';
  }

  healthClass(): string {
    return `health-${this.healthState}`;
  }

  private startRequest(): void {
    this.isLoading = true;
    this.errorMessage = null;
    this.executionResult = null;
    this.comparisonResult = null;
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
    value: string,
  ): Partial<Record<T, number>> {
    if (value.trim() === '') {
      return {};
    }

    return { [key]: Number(value) } as Partial<Record<T, number>>;
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
