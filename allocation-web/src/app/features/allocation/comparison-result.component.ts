import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';

import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
import {
  AllocationAlgorithmType,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
} from '../../core/models/allocation-api.models';
import { comparisonResultToCsv, serializeComparisonResult } from './result-export';

@Component({
  selector: 'app-comparison-result',
  imports: [CommonModule],
  templateUrl: './comparison-result.component.html',
  styleUrl: './comparison-result.component.scss',
})
export class ComparisonResultComponent {
  private readonly downloadTextFile = inject(DOWNLOAD_TEXT_FILE);

  @Input() request: AllocationComparisonApiRequest | null = null;
  @Input() result: AllocationComparisonApiResponse | null = null;

  readonly algorithms: AllocationAlgorithmType[] = ['GREEDY', 'BACKTRACKING', 'CP_SAT'];

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
