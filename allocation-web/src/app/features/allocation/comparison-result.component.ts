import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

import {
  AllocationAlgorithmType,
  AllocationComparisonApiResponse,
} from '../../core/models/allocation-api.models';

@Component({
  selector: 'app-comparison-result',
  imports: [CommonModule],
  templateUrl: './comparison-result.component.html',
  styleUrl: './comparison-result.component.scss',
})
export class ComparisonResultComponent {
  @Input() result: AllocationComparisonApiResponse | null = null;

  readonly algorithms: AllocationAlgorithmType[] = ['GREEDY', 'BACKTRACKING', 'CP_SAT'];

  isBestScore(algorithm: AllocationAlgorithmType): boolean {
    return this.result?.bestScoreAlgorithms.includes(algorithm) ?? false;
  }

  statusFor(algorithm: AllocationAlgorithmType): string {
    return this.result?.results[algorithm].allocationResult.statistics.algorithmStatus ?? 'N/A';
  }
}
