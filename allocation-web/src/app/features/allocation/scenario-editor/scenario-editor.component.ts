import { Component, EventEmitter, Input, OnInit, Output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';

import {
  AllocationRequestDto,
  ResourceDto,
  ResourceRequirementDto,
} from '../../../core/models/allocation-api.models';
import { createGreedyTrapScenario } from '../greedy-trap-scenario';
import { parseScenarioJson, serializeScenario } from './scenario-json';
import { AllocationScenario, ScenarioEditorState } from './scenario-editor.models';

type TextControl = FormControl<string>;
type NumberControl = FormControl<number | null>;

type CapacityForm = FormGroup<{
  name: TextControl;
  value: NumberControl;
}>;

type TimeWindowForm = FormGroup<{
  start: TextControl;
  end: TextControl;
}>;

type ResourceForm = FormGroup<{
  id: TextControl;
  name: TextControl;
  type: TextControl;
  capacities: FormArray<CapacityForm>;
  availability: FormArray<TimeWindowForm>;
}>;

type RequirementForm = FormGroup<{
  resourceType: TextControl;
  quantity: NumberControl;
  requiredCapacities: FormArray<CapacityForm>;
}>;

type RequestForm = FormGroup<{
  id: TextControl;
  name: TextControl;
  startTime: TextControl;
  durationMinutes: NumberControl;
  priority: NumberControl;
  resourceRequirements: FormArray<RequirementForm>;
}>;

type ScenarioForm = FormGroup<{
  resources: FormArray<ResourceForm>;
  requests: FormArray<RequestForm>;
}>;

type ImportStatus = {
  type: 'success' | 'error';
  message: string;
};

const MAX_SCENARIO_FILE_SIZE_BYTES = 1024 * 1024;
const IMPORTED_SCENARIO_INVALID_MESSAGE = 'The imported scenario contains invalid values.';
const SCENARIO_FILE_READ_ERROR_MESSAGE = 'The selected scenario file could not be read.';

const notBlankValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  return typeof value === 'string' && value.trim().length > 0 ? null : { blank: true };
};

const integerValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  return value === null || value === '' || Number.isInteger(value) ? null : { integer: true };
};

const nonEmptyArrayValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  return control instanceof FormArray && control.length === 0 ? { empty: true } : null;
};

function uniqueTrimmedFieldValidator(fieldName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!(control instanceof FormArray)) {
      return null;
    }

    const values = control.controls
      .map((item) => item.get(fieldName)?.value)
      .filter((value): value is string => typeof value === 'string')
      .map((value) => value.trim())
      .filter((value) => value.length > 0);

    return new Set(values).size === values.length ? null : { duplicate: true };
  };
}

const timeWindowOrderValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const start = control.get('start')?.value;
  const end = control.get('end')?.value;

  if (typeof start !== 'string' || typeof end !== 'string' || !start || !end) {
    return null;
  }

  return normalizeLocalDateTime(start) < normalizeLocalDateTime(end) ? null : { timeOrder: true };
};

@Component({
  selector: 'app-scenario-editor',
  imports: [ReactiveFormsModule],
  templateUrl: './scenario-editor.component.html',
  styleUrl: './scenario-editor.component.scss',
})
export class ScenarioEditorComponent implements OnInit {
  @Output() readonly scenarioChange = new EventEmitter<ScenarioEditorState>();

  readonly form: ScenarioForm;
  readonly importStatus = signal<ImportStatus | null>(null);
  validationAttempted = false;
  newResourceIndex: number | null = null;
  newRequestIndex: number | null = null;
  private editorDisabled = false;

  constructor() {
    this.form = this.createScenarioForm(createGreedyTrapScenario());
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.importStatus.set(null);
      this.emitState();
    });
  }

  @Input()
  set disabled(value: boolean) {
    this.editorDisabled = value;

    if (value && this.form.enabled) {
      this.form.disable({ emitEvent: false });
    } else if (!value && this.form.disabled) {
      this.form.enable({ emitEvent: false });
    }
  }

  get disabled(): boolean {
    return this.editorDisabled;
  }

  get resources(): FormArray<ResourceForm> {
    return this.form.controls.resources;
  }

  get requests(): FormArray<RequestForm> {
    return this.form.controls.requests;
  }

  ngOnInit(): void {
    this.emitState();
  }

  loadSample(): void {
    if (this.disabled) {
      return;
    }

    this.importStatus.set(null);
    this.validationAttempted = false;
    this.replaceScenario(createGreedyTrapScenario());
    this.emitState();
  }

  clear(): void {
    if (this.disabled) {
      return;
    }

    this.importStatus.set(null);
    this.resources.clear({ emitEvent: false });
    this.requests.clear({ emitEvent: false });
    this.form.updateValueAndValidity({ emitEvent: false });
    this.validationAttempted = true;
    this.newResourceIndex = null;
    this.newRequestIndex = null;
    this.emitState();
  }

  async importScenarioFile(event: Event): Promise<void> {
    if (this.disabled) {
      return;
    }

    this.importStatus.set(null);
    const input = event.currentTarget;
    if (!(input instanceof HTMLInputElement)) {
      this.importStatus.set({ type: 'error', message: SCENARIO_FILE_READ_ERROR_MESSAGE });
      return;
    }

    const file = input.files?.item(0);
    if (!file) {
      input.value = '';
      return;
    }

    try {
      if (file.size > MAX_SCENARIO_FILE_SIZE_BYTES) {
        this.importStatus.set({ type: 'error', message: 'Scenario file is too large.' });
        return;
      }

      const result = parseScenarioJson(await file.text());
      if (this.disabled) {
        return;
      }

      if (!result.success) {
        this.importStatus.set({ type: 'error', message: result.message });
        return;
      }

      const scenario = this.validatedScenario(result.scenario);
      if (!scenario) {
        this.importStatus.set({ type: 'error', message: IMPORTED_SCENARIO_INVALID_MESSAGE });
        return;
      }

      this.validationAttempted = false;
      this.replaceScenario(scenario);
      this.emitState();
      this.importStatus.set({ type: 'success', message: 'Scenario imported successfully.' });
    } catch {
      this.importStatus.set({ type: 'error', message: SCENARIO_FILE_READ_ERROR_MESSAGE });
    } finally {
      input.value = '';
    }
  }

  exportScenario(): void {
    if (this.disabled || !this.isScenarioValid()) {
      return;
    }

    const blob = new Blob([serializeScenario(this.toScenario())], {
      type: 'application/json',
    });
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = 'allocation-scenario.json';
    anchor.hidden = true;
    document.body.append(anchor);

    try {
      anchor.click();
    } finally {
      anchor.remove();
      URL.revokeObjectURL(objectUrl);
    }
  }

  isScenarioValid(): boolean {
    return this.form.valid && this.requests.length > 0;
  }

  addResource(): void {
    if (this.disabled) {
      return;
    }

    this.resources.push(this.createResourceForm());
    this.resources.markAsDirty();
    this.newResourceIndex = this.resources.length - 1;
  }

  removeResource(index: number): void {
    if (this.disabled) {
      return;
    }

    this.resources.removeAt(index);
    this.resources.markAsDirty();
    this.newResourceIndex = null;
  }

  addCapacity(resourceIndex: number): void {
    if (this.disabled) {
      return;
    }

    const capacities = this.resources.at(resourceIndex).controls.capacities;
    capacities.push(this.createCapacityForm());
    capacities.markAsDirty();
  }

  removeCapacity(resourceIndex: number, capacityIndex: number): void {
    if (this.disabled) {
      return;
    }

    const capacities = this.resources.at(resourceIndex).controls.capacities;
    capacities.removeAt(capacityIndex);
    capacities.markAsDirty();
  }

  addAvailability(resourceIndex: number): void {
    if (this.disabled) {
      return;
    }

    const availability = this.resources.at(resourceIndex).controls.availability;
    availability.push(this.createTimeWindowForm());
    availability.markAsDirty();
  }

  removeAvailability(resourceIndex: number, availabilityIndex: number): void {
    if (this.disabled) {
      return;
    }

    const availability = this.resources.at(resourceIndex).controls.availability;
    availability.removeAt(availabilityIndex);
    availability.markAsDirty();
  }

  addRequest(): void {
    if (this.disabled) {
      return;
    }

    this.requests.push(this.createRequestForm());
    this.requests.markAsDirty();
    this.newRequestIndex = this.requests.length - 1;
  }

  removeRequest(index: number): void {
    if (this.disabled) {
      return;
    }

    this.requests.removeAt(index);
    this.requests.markAsDirty();
    this.newRequestIndex = null;
  }

  addRequirement(requestIndex: number): void {
    if (this.disabled) {
      return;
    }

    const requirements = this.requests.at(requestIndex).controls.resourceRequirements;
    requirements.push(this.createRequirementForm());
    requirements.markAsDirty();
  }

  removeRequirement(requestIndex: number, requirementIndex: number): void {
    if (this.disabled) {
      return;
    }

    const requirements = this.requests.at(requestIndex).controls.resourceRequirements;
    requirements.removeAt(requirementIndex);
    requirements.markAsDirty();
  }

  addRequiredCapacity(requestIndex: number, requirementIndex: number): void {
    if (this.disabled) {
      return;
    }

    const capacities = this.requests
      .at(requestIndex)
      .controls.resourceRequirements.at(requirementIndex).controls.requiredCapacities;
    capacities.push(this.createCapacityForm());
    capacities.markAsDirty();
  }

  removeRequiredCapacity(
    requestIndex: number,
    requirementIndex: number,
    capacityIndex: number,
  ): void {
    if (this.disabled) {
      return;
    }

    const capacities = this.requests
      .at(requestIndex)
      .controls.resourceRequirements.at(requirementIndex).controls.requiredCapacities;
    capacities.removeAt(capacityIndex);
    capacities.markAsDirty();
  }

  resourceSummary(resource: ResourceForm): string {
    return this.summary(resource.controls.id.value, resource.controls.name.value, 'New resource');
  }

  requestSummary(request: RequestForm): string {
    return this.summary(request.controls.id.value, request.controls.name.value, 'New request');
  }

  fieldError(control: AbstractControl, label: string): string | null {
    if ((!control.touched && !this.validationAttempted) || control.valid) {
      return null;
    }

    if (control.hasError('required') || control.hasError('blank')) {
      return `${label} is required.`;
    }

    if (control.hasError('integer')) {
      return `${label} must be an integer.`;
    }

    if (control.hasError('min')) {
      const minimum = (control.getError('min') as { min: number }).min;
      return `${label} must be at least ${minimum}.`;
    }

    return `${label} is invalid.`;
  }

  duplicateError<TControl extends AbstractControl>(
    array: FormArray<TControl>,
    fieldName: string,
    message: string,
  ): string | null {
    const relevantFieldTouched = array.controls.some((control) => control.get(fieldName)?.touched);
    return array.hasError('duplicate') && (this.validationAttempted || relevantFieldTouched)
      ? message
      : null;
  }

  timeWindowError(window: TimeWindowForm): string | null {
    const touched = window.controls.start.touched || window.controls.end.touched;
    return window.hasError('timeOrder') && (this.validationAttempted || touched)
      ? 'Start must be before end.'
      : null;
  }

  collectionError<TControl extends AbstractControl>(
    array: FormArray<TControl>,
    message: string,
  ): string | null {
    return array.hasError('empty') && (this.validationAttempted || array.dirty || array.touched)
      ? message
      : null;
  }

  private replaceScenario(scenario: AllocationScenario): void {
    this.resources.clear({ emitEvent: false });
    this.requests.clear({ emitEvent: false });

    for (const resource of scenario.resources) {
      this.resources.push(this.createResourceForm(resource), { emitEvent: false });
    }

    for (const request of scenario.requests) {
      this.requests.push(this.createRequestForm(request), { emitEvent: false });
    }

    this.form.updateValueAndValidity({ emitEvent: false });
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.newResourceIndex = null;
    this.newRequestIndex = null;
  }

  private createScenarioForm(scenario: AllocationScenario): ScenarioForm {
    return new FormGroup({
      resources: new FormArray<ResourceForm>(
        scenario.resources.map((resource) => this.createResourceForm(resource)),
        { validators: [uniqueTrimmedFieldValidator('id')] },
      ),
      requests: new FormArray<RequestForm>(
        scenario.requests.map((request) => this.createRequestForm(request)),
        { validators: [nonEmptyArrayValidator, uniqueTrimmedFieldValidator('id')] },
      ),
    });
  }

  private validatedScenario(scenario: AllocationScenario): AllocationScenario | null {
    const candidateForm = this.createScenarioForm(scenario);
    candidateForm.updateValueAndValidity({ emitEvent: false });
    return candidateForm.valid && candidateForm.controls.requests.length > 0
      ? this.toScenario(candidateForm)
      : null;
  }

  private createResourceForm(resource?: ResourceDto): ResourceForm {
    return new FormGroup({
      id: new FormControl(resource?.id ?? '', {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      name: new FormControl(resource?.name ?? '', {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      type: new FormControl(resource?.type ?? '', {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      capacities: new FormArray<CapacityForm>(
        Object.entries(resource?.capacities ?? {}).map(([name, value]) =>
          this.createCapacityForm(name, value),
        ),
        { validators: [uniqueTrimmedFieldValidator('name')] },
      ),
      availability: new FormArray<TimeWindowForm>(
        (resource?.availability ?? []).map((window) =>
          this.createTimeWindowForm(window.start, window.end),
        ),
      ),
    });
  }

  private createCapacityForm(name = '', value: number | null = null): CapacityForm {
    return new FormGroup({
      name: new FormControl(name, {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      value: new FormControl<number | null>(value, {
        validators: [Validators.required, integerValidator, Validators.min(0)],
      }),
    });
  }

  private createTimeWindowForm(start = '', end = ''): TimeWindowForm {
    return new FormGroup(
      {
        start: new FormControl(start, {
          nonNullable: true,
          validators: [Validators.required],
        }),
        end: new FormControl(end, {
          nonNullable: true,
          validators: [Validators.required],
        }),
      },
      { validators: [timeWindowOrderValidator] },
    );
  }

  private createRequestForm(request?: AllocationRequestDto): RequestForm {
    return new FormGroup({
      id: new FormControl(request?.id ?? '', {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      name: new FormControl(request?.name ?? '', {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      startTime: new FormControl(request?.startTime ?? '', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      durationMinutes: new FormControl<number | null>(request?.durationMinutes ?? null, {
        validators: [Validators.required, integerValidator, Validators.min(1)],
      }),
      priority: new FormControl<number | null>(request?.priority ?? null, {
        validators: [Validators.required, integerValidator],
      }),
      resourceRequirements: new FormArray<RequirementForm>(
        (request?.resourceRequirements ?? [undefined]).map((requirement) =>
          this.createRequirementForm(requirement),
        ),
        { validators: [nonEmptyArrayValidator] },
      ),
    });
  }

  private createRequirementForm(requirement?: ResourceRequirementDto): RequirementForm {
    return new FormGroup({
      resourceType: new FormControl(requirement?.resourceType ?? '', {
        nonNullable: true,
        validators: [notBlankValidator],
      }),
      quantity: new FormControl<number | null>(requirement?.quantity ?? 1, {
        validators: [Validators.required, integerValidator, Validators.min(1)],
      }),
      requiredCapacities: new FormArray<CapacityForm>(
        Object.entries(requirement?.requiredCapacities ?? {}).map(([name, value]) =>
          this.createCapacityForm(name, value),
        ),
        { validators: [uniqueTrimmedFieldValidator('name')] },
      ),
    });
  }

  private emitState(): void {
    this.form.updateValueAndValidity({ emitEvent: false });
    const valid = this.isScenarioValid();
    this.scenarioChange.emit({
      valid,
      scenario: valid ? this.toScenario() : null,
    });
  }

  private toScenario(form: ScenarioForm = this.form): AllocationScenario {
    const resources = form.controls.resources;
    const requests = form.controls.requests;

    return {
      resources: resources.controls.map((resource) => ({
        id: resource.controls.id.value.trim(),
        name: resource.controls.name.value.trim(),
        type: resource.controls.type.value.trim(),
        capacities: this.toCapacityRecord(resource.controls.capacities),
        availability: resource.controls.availability.controls.map((window) => ({
          start: normalizeLocalDateTime(window.controls.start.value),
          end: normalizeLocalDateTime(window.controls.end.value),
        })),
      })),
      requests: requests.controls.map((request) => ({
        id: request.controls.id.value.trim(),
        name: request.controls.name.value.trim(),
        startTime: normalizeLocalDateTime(request.controls.startTime.value),
        durationMinutes: request.controls.durationMinutes.value ?? 0,
        priority: request.controls.priority.value ?? 0,
        resourceRequirements: request.controls.resourceRequirements.controls.map((requirement) => ({
          resourceType: requirement.controls.resourceType.value.trim(),
          quantity: requirement.controls.quantity.value ?? 0,
          requiredCapacities: this.toCapacityRecord(requirement.controls.requiredCapacities),
        })),
      })),
    };
  }

  private toCapacityRecord(capacities: FormArray<CapacityForm>): Record<string, number> {
    const result: Record<string, number> = {};

    for (const capacity of capacities.controls) {
      result[capacity.controls.name.value.trim()] = capacity.controls.value.value ?? 0;
    }

    return result;
  }

  private summary(id: string, name: string, fallback: string): string {
    const trimmedId = id.trim();
    const trimmedName = name.trim();

    if (trimmedId && trimmedName) {
      return `${trimmedId} - ${trimmedName}`;
    }

    return trimmedId || trimmedName || fallback;
  }
}

function normalizeLocalDateTime(value: string): string {
  const trimmed = value.trim();
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(trimmed) ? `${trimmed}:00` : trimmed;
}
