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
} from '../../core/models/allocation-api.models';
import { ComparisonResultComponent } from './comparison-result.component';
import { ExecutionResultComponent } from './execution-result.component';
import { createGreedyTrapScenario } from './greedy-trap-scenario';
import { ScenarioEditorComponent } from './scenario-editor/scenario-editor.component';
import { AllocationScenario, ScenarioEditorState } from './scenario-editor/scenario-editor.models';

type PageMode = 'EXPLICIT' | 'AUTO' | 'COMPARE';
type HealthState = 'checking' | 'online' | 'unavailable';

@Component({
  selector: 'app-allocation-page',
  imports: [
    CommonModule,
    FormsModule,
    ExecutionResultComponent,
    ComparisonResultComponent,
    ScenarioEditorComponent,
  ],
  templateUrl: './allocation-page.component.html',
  styleUrl: './allocation-page.component.scss',
})
export class AllocationPageComponent implements OnInit {
  private readonly allocationApi = inject(AllocationApiService);

  readonly algorithms: AllocationAlgorithmType[] = ['GREEDY', 'BACKTRACKING', 'CP_SAT'];
  readonly goals: AllocationGoal[] = ['FASTEST', 'BALANCED', 'BEST_QUALITY'];

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
  readonly lastExecutionRequest = signal<AllocationApiRequest | null>(null);
  readonly lastComparisonRequest = signal<AllocationComparisonApiRequest | null>(null);
  readonly currentScenario = signal<AllocationScenario>(createGreedyTrapScenario());
  readonly scenarioValid = signal(true);

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
    if (this.isLoading() || !this.scenarioValid()) {
      return;
    }

    const request = this.createExecutionRequest();

    this.startRequest();
    this.allocationApi
      .executeAllocation(request)
      .pipe(
        finalize(() => {
          this.isLoading.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          this.lastExecutionRequest.set(request);
          this.executionResult.set(response);
        },
        error: (error: unknown) => {
          this.errorMessage.set(this.toErrorMessage(error));
        },
      });
  }

  compareAlgorithms(): void {
    if (this.isLoading() || !this.scenarioValid()) {
      return;
    }

    const request = this.createComparisonRequest();

    this.startRequest();
    this.allocationApi
      .compareAllocations(request)
      .pipe(
        finalize(() => {
          this.isLoading.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          this.lastComparisonRequest.set(request);
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
    this.lastExecutionRequest.set(null);
    this.lastComparisonRequest.set(null);
  }

  onScenarioChange(state: ScenarioEditorState): void {
    this.errorMessage.set(null);
    this.executionResult.set(null);
    this.comparisonResult.set(null);
    this.lastExecutionRequest.set(null);
    this.lastComparisonRequest.set(null);

    if (state.valid && state.scenario) {
      this.currentScenario.set(state.scenario);
      this.scenarioValid.set(true);
      return;
    }

    this.scenarioValid.set(false);
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
    this.lastExecutionRequest.set(null);
    this.lastComparisonRequest.set(null);
  }

  private createExecutionRequest(): AllocationApiRequest {
    const scenario = this.currentScenario();
    const baseRequest = {
      ...this.optionalLimits(),
      resources: scenario.resources,
      requests: scenario.requests,
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
    const scenario = this.currentScenario();
    return {
      ...this.optionalLimits(),
      resources: scenario.resources,
      requests: scenario.requests,
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
