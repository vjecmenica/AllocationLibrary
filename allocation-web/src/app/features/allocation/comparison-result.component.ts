import { CommonModule } from '@angular/common';
import { Component, Input, computed, inject, signal } from '@angular/core';

import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
import {
  AllocationAlgorithmType,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
} from '../../core/models/allocation-api.models';
import {
  COMPARISON_ALGORITHMS,
  RequestOutcomeStatus,
  buildRequestComparisonRows,
} from './comparison-analysis';
import { comparisonResultToCsv, serializeComparisonResult } from './result-export';

@Component({
  selector: 'app-comparison-result',
  imports: [CommonModule],
  templateUrl: './comparison-result.component.html',
  styleUrl: './comparison-result.component.scss',
})
export class ComparisonResultComponent {
  private readonly downloadTextFile = inject(DOWNLOAD_TEXT_FILE);
  private readonly requestValue = signal<AllocationComparisonApiRequest | null>(null);
  private readonly resultValue = signal<AllocationComparisonApiResponse | null>(null);

  @Input()
  set request(value: AllocationComparisonApiRequest | null) {
    this.requestValue.set(value);
  }

  get request(): AllocationComparisonApiRequest | null {
    return this.requestValue();
  }

  @Input()
  set result(value: AllocationComparisonApiResponse | null) {
    this.resultValue.set(value);
  }

  get result(): AllocationComparisonApiResponse | null {
    return this.resultValue();
  }

  readonly algorithms = COMPARISON_ALGORITHMS;
  readonly selectedAlgorithm = signal<AllocationAlgorithmType>('GREEDY');
  readonly requestRows = computed(() => {
    const request = this.requestValue();
    const result = this.resultValue();
    return request && result ? buildRequestComparisonRows(request, result) : [];
  });
  readonly selectedAcceptedRows = computed(() => {
    const algorithm = this.selectedAlgorithm();
    return this.requestRows().filter((row) => row.outcomes[algorithm].status === 'ACCEPTED');
  });
  readonly selectedRejectedRows = computed(() => {
    const algorithm = this.selectedAlgorithm();
    return this.requestRows().filter((row) => row.outcomes[algorithm].status === 'REJECTED');
  });
  readonly selectedUnknownRows = computed(() => {
    const algorithm = this.selectedAlgorithm();
    return this.requestRows().filter((row) => row.outcomes[algorithm].status === 'UNKNOWN');
  });
  readonly selectedUnknownRequestIds = computed(() =>
    this.selectedUnknownRows()
      .map((row) => row.request.id)
      .join(', '),
  );

  selectAlgorithm(algorithm: AllocationAlgorithmType): void {
    this.selectedAlgorithm.set(algorithm);
  }

  outcomeClass(status: RequestOutcomeStatus): string {
    return `outcome-${status.toLowerCase()}`;
  }

  exportJson(): void {
    if (!this.request || !this.result) {
      return;
    }

    this.downloadTextFile(
      serializeComparisonResult(this.request, this.result),
      'allocation-comparison-result.json',
      'application/json',
    );
  }

  exportCsv(): void {
    if (!this.request || !this.result) {
      return;
    }

    this.downloadTextFile(
      comparisonResultToCsv(this.request, this.result),
      'allocation-comparison-result.csv',
      'text/csv;charset=utf-8',
    );
  }

  isBestScore(algorithm: AllocationAlgorithmType): boolean {
    return this.result?.bestScoreAlgorithms.includes(algorithm) ?? false;
  }

  statusFor(algorithm: AllocationAlgorithmType): string {
    return this.result?.results[algorithm].allocationResult.statistics.algorithmStatus ?? 'N/A';
  }
}
