import { AllocationRequestDto, ResourceDto } from '../../../core/models/allocation-api.models';

export interface AllocationScenario {
  resources: ResourceDto[];
  requests: AllocationRequestDto[];
}

export interface ScenarioEditorState {
  valid: boolean;
  scenario: AllocationScenario | null;
}
