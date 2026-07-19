import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { createGreedyTrapScenario } from '../greedy-trap-scenario';
import { ScenarioEditorComponent } from './scenario-editor.component';
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

  function latestState(): ScenarioEditorState {
    const state = emittedStates.at(-1);
    if (!state) {
      throw new Error('Expected the scenario editor to emit state.');
    }

    return state;
  }
});
