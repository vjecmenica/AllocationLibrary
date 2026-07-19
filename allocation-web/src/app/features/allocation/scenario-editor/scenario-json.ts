import {
  AllocationRequestDto,
  ResourceDto,
  ResourceRequirementDto,
  TimeWindowDto,
} from '../../../core/models/allocation-api.models';
import { AllocationScenario } from './scenario-editor.models';

export interface AllocationScenarioFile {
  schemaVersion: 1;
  scenario: AllocationScenario;
}

export type ScenarioParseResult =
  { success: true; scenario: AllocationScenario } | { success: false; message: string };

const INVALID_JSON_MESSAGE = 'The selected file is not valid JSON.';
const INVALID_SCENARIO_MESSAGE = 'The JSON file does not contain a valid allocation scenario.';
const UNSUPPORTED_VERSION_MESSAGE = 'Unsupported scenario schema version.';

export function serializeScenario(scenario: AllocationScenario): string {
  const file: AllocationScenarioFile = {
    schemaVersion: 1,
    scenario,
  };

  return JSON.stringify(file, null, 2);
}

export function parseScenarioJson(json: string): ScenarioParseResult {
  let parsed: unknown;

  try {
    parsed = JSON.parse(json) as unknown;
  } catch {
    return { success: false, message: INVALID_JSON_MESSAGE };
  }

  if (!isPlainObject(parsed)) {
    return { success: false, message: INVALID_SCENARIO_MESSAGE };
  }

  let scenarioValue: unknown = parsed;
  if (hasOwn(parsed, 'schemaVersion')) {
    if (parsed['schemaVersion'] !== 1) {
      return { success: false, message: UNSUPPORTED_VERSION_MESSAGE };
    }

    if (!hasOwn(parsed, 'scenario')) {
      return { success: false, message: INVALID_SCENARIO_MESSAGE };
    }
    scenarioValue = parsed['scenario'];
  }

  const scenario = parseScenario(scenarioValue);
  return scenario
    ? { success: true, scenario }
    : { success: false, message: INVALID_SCENARIO_MESSAGE };
}

function parseScenario(value: unknown): AllocationScenario | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const resourcesValue = ownValue(value, 'resources');
  const requestsValue = ownValue(value, 'requests');
  if (!Array.isArray(resourcesValue) || !Array.isArray(requestsValue)) {
    return null;
  }

  const resources = parseArray(resourcesValue, parseResource);
  const requests = parseArray(requestsValue, parseRequest);
  return resources && requests ? { resources, requests } : null;
}

function parseResource(value: unknown): ResourceDto | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const id = ownValue(value, 'id');
  const name = ownValue(value, 'name');
  const type = ownValue(value, 'type');
  const capacities = parseNumberRecord(ownValue(value, 'capacities'));
  const availabilityValue = ownValue(value, 'availability');

  if (
    typeof id !== 'string' ||
    typeof name !== 'string' ||
    typeof type !== 'string' ||
    capacities === null ||
    !Array.isArray(availabilityValue)
  ) {
    return null;
  }

  const availability = parseArray(availabilityValue, parseTimeWindow);
  return availability ? { id, name, type, capacities, availability } : null;
}

function parseTimeWindow(value: unknown): TimeWindowDto | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const start = ownValue(value, 'start');
  const end = ownValue(value, 'end');
  return typeof start === 'string' && typeof end === 'string' ? { start, end } : null;
}

function parseRequest(value: unknown): AllocationRequestDto | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const id = ownValue(value, 'id');
  const name = ownValue(value, 'name');
  const startTime = ownValue(value, 'startTime');
  const durationMinutes = ownValue(value, 'durationMinutes');
  const priority = ownValue(value, 'priority');
  const requirementsValue = ownValue(value, 'resourceRequirements');

  if (
    typeof id !== 'string' ||
    typeof name !== 'string' ||
    typeof startTime !== 'string' ||
    !isFiniteNumber(durationMinutes) ||
    !isFiniteNumber(priority) ||
    !Array.isArray(requirementsValue)
  ) {
    return null;
  }

  const resourceRequirements = parseArray(requirementsValue, parseRequirement);
  return resourceRequirements
    ? { id, name, startTime, durationMinutes, priority, resourceRequirements }
    : null;
}

function parseRequirement(value: unknown): ResourceRequirementDto | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const resourceType = ownValue(value, 'resourceType');
  const quantity = ownValue(value, 'quantity');
  const requiredCapacities = parseNumberRecord(ownValue(value, 'requiredCapacities'));

  return typeof resourceType === 'string' && isFiniteNumber(quantity) && requiredCapacities !== null
    ? { resourceType, quantity, requiredCapacities }
    : null;
}

function parseNumberRecord(value: unknown): Record<string, number> | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const entries = Object.entries(value);
  if (entries.some(([, entryValue]) => !isFiniteNumber(entryValue))) {
    return null;
  }

  return Object.fromEntries(entries) as Record<string, number>;
}

function parseArray<T>(values: unknown[], parser: (value: unknown) => T | null): T[] | null {
  const parsed: T[] = [];

  for (const value of values) {
    const item = parser(value);
    if (item === null) {
      return null;
    }
    parsed.push(item);
  }

  return parsed;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return false;
  }

  const prototype = Object.getPrototypeOf(value) as unknown;
  return prototype === Object.prototype || prototype === null;
}

function hasOwn(value: Record<string, unknown>, key: string): boolean {
  return Object.prototype.hasOwnProperty.call(value, key);
}

function ownValue(value: Record<string, unknown>, key: string): unknown {
  return hasOwn(value, key) ? value[key] : undefined;
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
