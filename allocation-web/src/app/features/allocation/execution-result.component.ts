import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';

import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
import {
  AllocationApiRequest,
  AllocationApiResponse,
  ResourceDto,
} from '../../core/models/allocation-api.models';
import { executionResultToCsv, serializeExecutionResult } from './result-export';

@Component({
  selector: 'app-execution-result',
  imports: [CommonModule],
  templateUrl: './execution-result.component.html',
  styleUrl: './execution-result.component.scss',
})
export class ExecutionResultComponent {
  private readonly downloadTextFile = inject(DOWNLOAD_TEXT_FILE);

  @Input() request: AllocationApiRequest | null = null;
  @Input() result: AllocationApiResponse | null = null;

  exportJson(): void {
    if (!this.request || !this.result) {
      return;
    }

    this.downloadTextFile(
      serializeExecutionResult(this.request, this.result),
      'allocation-execution-result.json',
      'application/json',
    );
  }

  exportCsv(): void {
    if (!this.request || !this.result) {
      return;
    }

    this.downloadTextFile(
      executionResultToCsv(this.request, this.result),
      'allocation-execution-result.csv',
      'text/csv;charset=utf-8',
    );
  }

  assignedResourceLabels(resources: ResourceDto[]): string {
    return resources.map((resource) => `${resource.id} (${resource.name})`).join(', ');
  }

  valueOrNotApplicable(value: string | null | undefined): string {
    return value ?? 'N/A';
  }
}
