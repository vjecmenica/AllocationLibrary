import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';

import { AllocationApiResponse, ResourceDto } from '../../core/models/allocation-api.models';

@Component({
  selector: 'app-execution-result',
  imports: [CommonModule],
  templateUrl: './execution-result.component.html',
  styleUrl: './execution-result.component.scss',
})
export class ExecutionResultComponent {
  @Input() result: AllocationApiResponse | null = null;

  assignedResourceLabels(resources: ResourceDto[]): string {
    return resources.map((resource) => `${resource.id} (${resource.name})`).join(', ');
  }

  valueOrNotApplicable(value: string | null | undefined): string {
    return value ?? 'N/A';
  }
}
