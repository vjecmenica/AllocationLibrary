import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { createGreedyTrapScenario } from '../greedy-trap-scenario';
import { ScenarioEditorComponent } from './scenario-editor.component';
import { serializeScenario } from './scenario-json';
import { ScenarioEditorState } from './scenario-editor.models';

describe('ScenarioEditorComponent', () => {
  let fixture: ComponentFixture<ScenarioEditorComponent>;
  let component: ScenarioEditorComponent;
  let emittedStates: ScenarioEditorState[];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScenarioEditorComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(ScenarioEditorComponent);
    component = fixture.componentInstance;
    emittedStates = [];
    component.scenarioChange.subscribe((state) => emittedStates.push(state));
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should initialize with the valid Greedy Trap scenario', () => {
    const state = latestState();

    expect(state.valid).toBe(true);
    expect(component.resources.length).toBe(2);
    expect(component.requests.length).toBe(2);
    expect(state.scenario?.resources.map((resource) => resource.id)).toEqual(['R_BIG', 'R_SMALL']);
    expect(state.scenario?.requests.map((request) => request.id)).toEqual(['REQ_SMALL', 'REQ_BIG']);
  });

  it('should create independent Greedy Trap scenario objects', () => {
    const first = createGreedyTrapScenario();
    const second = createGreedyTrapScenario();

    expect(first.resources).not.toBe(second.resources);
    expect(first.resources[0]).not.toBe(second.resources[0]);
    expect(first.resources[0].availability[0]).not.toBe(second.resources[0].availability[0]);
    expect(first.requests[0].resourceRequirements[0]).not.toBe(
      second.requests[0].resourceRequirements[0],
    );

    first.resources[0].id = 'MUTATED';
    expect(second.resources[0].id).toBe('R_BIG');
  });

  it('should load a fresh Greedy Trap scenario', () => {
    component.resources.at(0).controls.id.setValue('CHANGED');

    component.loadSample();

    expect(latestState().valid).toBe(true);
    expect(latestState().scenario?.resources[0].id).toBe('R_BIG');
    expect(latestState().scenario?.requests[0].id).toBe('REQ_SMALL');
  });

  it('should clear resources and requests and become invalid', () => {
    component.clear();

    expect(component.resources.length).toBe(0);
    expect(component.requests.length).toBe(0);
    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should add and remove a resource', () => {
    component.addResource();
    expect(component.resources.length).toBe(3);

    component.removeResource(2);
    expect(component.resources.length).toBe(2);
  });

  it('should add and remove an allocation request', () => {
    component.addRequest();
    expect(component.requests.length).toBe(3);

    component.removeRequest(2);
    expect(component.requests.length).toBe(2);
    expect(latestState().valid).toBe(true);
  });

  it('should map capacity rows to a capacity record', () => {
    component.addCapacity(0);
    component.resources.at(0).controls.capacities.at(1).setValue({
      name: 'seats',
      value: 50,
    });

    expect(latestState().scenario?.resources[0].capacities).toEqual({
      people: 100,
      seats: 50,
    });
  });

  it('should normalize datetime-local values without a timezone conversion', () => {
    component.addAvailability(0);
    component.resources.at(0).controls.availability.at(1).setValue({
      start: '2026-08-02T09:15',
      end: '2026-08-02T10:45',
    });

    expect(latestState().scenario?.resources[0].availability[1]).toEqual({
      start: '2026-08-02T09:15:00',
      end: '2026-08-02T10:45:00',
    });
  });

  it('should reject duplicate resource IDs after trimming', () => {
    component.resources.at(1).controls.id.setValue(' R_BIG ');

    expect(latestState()).toEqual({ valid: false, scenario: null });
    expect(component.resources.hasError('duplicate')).toBe(true);
  });

  it('should reject duplicate request IDs after trimming', () => {
    component.requests.at(1).controls.id.setValue(' REQ_SMALL ');

    expect(latestState()).toEqual({ valid: false, scenario: null });
    expect(component.requests.hasError('duplicate')).toBe(true);
  });

  it('should reject an availability window whose start is not before its end', () => {
    component.resources.at(0).controls.availability.at(0).setValue({
      start: '2026-07-01T12:00',
      end: '2026-07-01T12:00',
    });

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject a request with zero duration', () => {
    component.requests.at(0).controls.durationMinutes.setValue(0);

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject a request without a resource requirement', () => {
    component.requests.at(0).controls.resourceRequirements.clear();

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject a resource requirement with zero quantity', () => {
    component.requests.at(0).controls.resourceRequirements.at(0).controls.quantity.setValue(0);

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject a negative required capacity', () => {
    component.requests
      .at(0)
      .controls.resourceRequirements.at(0)
      .controls.requiredCapacities.at(0)
      .controls.value.setValue(-1);

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject a negative resource capacity', () => {
    component.resources.at(0).controls.capacities.at(0).controls.value.setValue(-1);

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject duplicate resource capacity names after trimming', () => {
    component.addCapacity(0);
    component.resources.at(0).controls.capacities.at(1).setValue({
      name: ' people ',
      value: 50,
    });

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should reject duplicate required capacity names after trimming', () => {
    component.addRequiredCapacity(0, 0);
    component.requests
      .at(0)
      .controls.resourceRequirements.at(0)
      .controls.requiredCapacities.at(1)
      .setValue({ name: ' people ', value: 50 });

    expect(latestState()).toEqual({ valid: false, scenario: null });
  });

  it('should trim resource types and capacity names without changing case', () => {
    component.resources.at(0).controls.type.setValue(' RoomMixed ');
    component.resources.at(0).controls.capacities.at(0).controls.name.setValue(' PeopleMixed ');
    component.requests
      .at(0)
      .controls.resourceRequirements.at(0)
      .controls.resourceType.setValue(' RoomMixed ');
    component.requests
      .at(0)
      .controls.resourceRequirements.at(0)
      .controls.requiredCapacities.at(0)
      .controls.name.setValue(' PeopleMixed ');

    const scenario = latestState().scenario;
    expect(scenario?.resources[0].type).toBe('RoomMixed');
    expect(scenario?.resources[0].capacities).toHaveProperty('PeopleMixed', 100);
    expect(scenario?.requests[0].resourceRequirements[0].resourceType).toBe('RoomMixed');
    expect(scenario?.requests[0].resourceRequirements[0].requiredCapacities).toHaveProperty(
      'PeopleMixed',
      30,
    );
  });

  it('should allow resources without availability windows', () => {
    component.resources.at(0).controls.availability.clear();

    expect(latestState().valid).toBe(true);
    expect(latestState().scenario?.resources[0].availability).toEqual([]);
  });

  it('should allow a valid request scenario without resources', () => {
    component.resources.clear();

    expect(latestState().valid).toBe(true);
    expect(latestState().scenario?.resources).toEqual([]);
  });

  it('should render JSON import and export buttons', () => {
    expect(button('import-json').textContent?.trim()).toBe('IMPORT JSON');
    expect(button('export-json').textContent?.trim()).toBe('EXPORT JSON');
  });

  it('should open the native file picker from the import button', () => {
    const inputClick = vi.spyOn(fileInput(), 'click').mockImplementation(() => undefined);

    button('import-json').click();

    expect(inputClick).toHaveBeenCalledOnce();
  });

  it('should enable export for the initial valid scenario', () => {
    expect(button('export-json').disabled).toBe(false);
  });

  it('should disable export for an invalid scenario', async () => {
    component.clear();
    await fixture.whenStable();

    expect(button('export-json').disabled).toBe(true);
  });

  it('should disable import and export while the editor is disabled', async () => {
    component.disabled = true;
    await fixture.whenStable();

    expect(button('import-json').disabled).toBe(true);
    expect(button('export-json').disabled).toBe(true);
    expect(fileInput().disabled).toBe(true);
  });

  it('should export the current form state and release the object URL', async () => {
    component.resources.at(0).controls.id.setValue('CURRENT_RESOURCE');
    let exportedBlob: Blob | null = null;
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockImplementation((value) => {
      if (!(value instanceof Blob)) {
        throw new Error('Expected export to create a Blob.');
      }
      exportedBlob = value;
      return 'blob:scenario-test';
    });
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    let downloadName: string | null = null;
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      downloadName = this.download;
    });

    component.exportScenario();

    expect(createObjectUrl).toHaveBeenCalledOnce();
    expect(anchorClick).toHaveBeenCalledOnce();
    expect(downloadName).toBe('allocation-scenario.json');
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:scenario-test');
    expect(exportedBlob).not.toBeNull();
    expect(await exportedBlob!.text()).toContain('"id": "CURRENT_RESOURCE"');
  });

  it('should import a valid versioned scenario atomically', async () => {
    const scenario = createGreedyTrapScenario();
    scenario.resources = [scenario.resources[0]];
    scenario.requests = [scenario.requests[0]];

    await importJson(serializeScenario(scenario));

    expect(component.resources.length).toBe(1);
    expect(component.requests.length).toBe(1);
    expect(component.form.valid).toBe(true);
    expect(component.form.pristine).toBe(true);
    expect(component.form.untouched).toBe(true);
    expect(component.newResourceIndex).toBeNull();
    expect(component.newRequestIndex).toBeNull();
    expect(latestState()).toEqual({ valid: true, scenario });
    expect(component.importStatus()).toEqual({
      type: 'success',
      message: 'Scenario imported successfully.',
    });
    expect(host().textContent).toContain('Scenario imported successfully.');
    expect(host().querySelectorAll('details[open]')).toHaveLength(0);
  });

  it('should import a valid raw scenario', async () => {
    const scenario = createGreedyTrapScenario();
    scenario.resources[0].id = 'RAW_RESOURCE';

    await importJson(JSON.stringify(scenario));

    expect(latestState().valid).toBe(true);
    expect(latestState().scenario?.resources[0].id).toBe('RAW_RESOURCE');
  });

  it('should emit a new valid state after a successful import', async () => {
    const emissionCount = emittedStates.length;

    await importJson(serializeScenario(createGreedyTrapScenario()));

    expect(emittedStates).toHaveLength(emissionCount + 1);
    expect(latestState().valid).toBe(true);
  });

  it('should show an error and preserve the scenario for malformed JSON', async () => {
    await expectFailedImport('{not-json', 'The selected file is not valid JSON.');
  });

  it('should preserve the scenario for structurally invalid JSON', async () => {
    await expectFailedImport(
      '{"resources":{},"requests":[]}',
      'The JSON file does not contain a valid allocation scenario.',
    );
  });

  it('should preserve the scenario when imported values fail form validation', async () => {
    const scenario = createGreedyTrapScenario();
    scenario.resources[1].id = scenario.resources[0].id;

    await expectFailedImport(
      serializeScenario(scenario),
      'The imported scenario contains invalid values.',
    );
  });

  it('should import a valid request scenario without resources', async () => {
    const scenario = createGreedyTrapScenario();
    scenario.resources = [];

    await importJson(serializeScenario(scenario));

    expect(latestState().valid).toBe(true);
    expect(latestState().scenario?.resources).toEqual([]);
  });

  it('should preserve the scenario when an import has no requests', async () => {
    const scenario = createGreedyTrapScenario();
    scenario.requests = [];

    await expectFailedImport(
      serializeScenario(scenario),
      'The imported scenario contains invalid values.',
    );
  });

  it('should preserve the scenario for an unsupported schema version', async () => {
    await expectFailedImport(
      '{"schemaVersion":2,"scenario":{"resources":[],"requests":[]}}',
      'Unsupported scenario schema version.',
    );
  });

  it('should reject an oversized scenario file', async () => {
    const file = scenarioFile(serializeScenario(createGreedyTrapScenario()));
    Object.defineProperty(file, 'size', { configurable: true, value: 1024 * 1024 + 1 });

    await importFile(file);

    expect(component.importStatus()).toEqual({
      type: 'error',
      message: 'Scenario file is too large.',
    });
    expect(component.resources.length).toBe(2);
    expect(component.requests.length).toBe(2);
  });

  it('should allow the same file to be selected again after import', async () => {
    const file = scenarioFile(serializeScenario(createGreedyTrapScenario()));
    const input = fileInput();

    await importFile(file, input);
    const firstEmissionCount = emittedStates.length;
    await importFile(file, input);

    expect(input.value).toBe('');
    expect(emittedStates).toHaveLength(firstEmissionCount + 1);
    expect(component.importStatus()?.type).toBe('success');
  });

  it('should clear import status after a manual form change', async () => {
    await importJson(serializeScenario(createGreedyTrapScenario()));

    component.resources.at(0).controls.name.setValue('Changed after import');

    expect(component.importStatus()).toBeNull();
  });

  it('should clear import status after loading the sample', async () => {
    await importJson(serializeScenario(createGreedyTrapScenario()));

    component.loadSample();

    expect(component.importStatus()).toBeNull();
  });

  it('should clear import status after clearing the editor', async () => {
    await importJson(serializeScenario(createGreedyTrapScenario()));

    component.clear();

    expect(component.importStatus()).toBeNull();
  });

  function latestState(): ScenarioEditorState {
    const state = emittedStates.at(-1);
    if (!state) {
      throw new Error('Expected the scenario editor to emit state.');
    }

    return state;
  }

  function host(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function button(testId: string): HTMLButtonElement {
    const element = host().querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
    if (!element) {
      throw new Error(`Expected button with test ID ${testId}.`);
    }
    return element;
  }

  function fileInput(): HTMLInputElement {
    const input = host().querySelector<HTMLInputElement>('input[type="file"]');
    if (!input) {
      throw new Error('Expected a scenario file input.');
    }
    return input;
  }

  function scenarioFile(json: string): File {
    const file = new File([json], 'scenario.json', { type: 'application/json' });
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: vi.fn(() => Promise.resolve(json)),
    });
    return file;
  }

  async function importJson(json: string): Promise<void> {
    await importFile(scenarioFile(json));
  }

  async function importFile(file: File, input = fileInput()): Promise<void> {
    const files = {
      0: file,
      length: 1,
      item: (index: number) => (index === 0 ? file : null),
    } as unknown as FileList;
    Object.defineProperty(input, 'files', { configurable: true, value: files });
    Object.defineProperty(input, 'value', {
      configurable: true,
      writable: true,
      value: 'scenario.json',
    });

    await component.importScenarioFile({ currentTarget: input } as unknown as Event);
    await fixture.whenStable();
  }

  async function expectFailedImport(json: string, message: string): Promise<void> {
    const resourceIds = component.resources.controls.map((resource) => resource.controls.id.value);
    const requestIds = component.requests.controls.map((request) => request.controls.id.value);
    const emissionCount = emittedStates.length;

    await importJson(json);

    expect(component.resources.controls.map((resource) => resource.controls.id.value)).toEqual(
      resourceIds,
    );
    expect(component.requests.controls.map((request) => request.controls.id.value)).toEqual(
      requestIds,
    );
    expect(emittedStates).toHaveLength(emissionCount);
    expect(component.importStatus()).toEqual({ type: 'error', message });
    expect(host().querySelector('[role="alert"]')?.textContent).toContain(message);
  }
});
