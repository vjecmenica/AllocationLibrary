import { createGreedyTrapScenario } from '../greedy-trap-scenario';
import { parseScenarioJson, serializeScenario } from './scenario-json';

describe('scenario JSON utilities', () => {
  it('should serialize a schema version 1 envelope', () => {
    const serialized = serializeScenario(createGreedyTrapScenario());
    const parsed = JSON.parse(serialized) as unknown;

    expect(parsed).toMatchObject({
      schemaVersion: 1,
      scenario: {
        resources: expect.any(Array),
        requests: expect.any(Array),
      },
    });
  });

  it('should serialize the scenario currently supplied by the caller', () => {
    const scenario = createGreedyTrapScenario();
    scenario.resources[0].id = 'CURRENT_RESOURCE';

    expect(serializeScenario(scenario)).toContain('"id": "CURRENT_RESOURCE"');
  });

  it('should produce valid pretty-printed JSON', () => {
    const serialized = serializeScenario(createGreedyTrapScenario());

    expect(() => JSON.parse(serialized) as unknown).not.toThrow();
    expect(serialized).toContain('\n  "schemaVersion": 1,');
    expect(serialized).toContain('\n    "resources": [');
  });

  it('should parse a versioned scenario envelope', () => {
    const scenario = createGreedyTrapScenario();
    const result = parseScenarioJson(serializeScenario(scenario));

    expect(result).toEqual({ success: true, scenario });
  });

  it('should parse a raw scenario', () => {
    const scenario = createGreedyTrapScenario();
    const result = parseScenarioJson(JSON.stringify(scenario));

    expect(result).toEqual({ success: true, scenario });
  });

  it('should reject malformed JSON', () => {
    expect(parseScenarioJson('{not-json')).toEqual({
      success: false,
      message: 'The selected file is not valid JSON.',
    });
  });

  it('should reject null', () => {
    expectInvalidScenario('null');
  });

  it('should reject a top-level array', () => {
    expectInvalidScenario('[]');
  });

  it('should reject an unsupported schema version', () => {
    expect(
      parseScenarioJson('{"schemaVersion":2,"scenario":{"resources":[],"requests":[]}}'),
    ).toEqual({
      success: false,
      message: 'Unsupported scenario schema version.',
    });
  });

  it('should reject a scenario without a resources array', () => {
    expectInvalidScenario('{"requests":[]}');
  });

  it('should reject a scenario without a requests array', () => {
    expectInvalidScenario('{"resources":[]}');
  });

  it('should reject a resource without required fields', () => {
    expectInvalidScenario('{"resources":[{"id":"R1","name":"Room"}],"requests":[]}');
  });

  it('should reject an array used as a capacity record', () => {
    const scenario = createGreedyTrapScenario();
    const value = JSON.parse(JSON.stringify(scenario)) as {
      resources: Array<{ capacities: unknown }>;
    };
    value.resources[0].capacities = [];

    expectInvalidScenario(JSON.stringify(value));
  });

  it('should reject a non-numeric capacity', () => {
    const json = serializeScenario(createGreedyTrapScenario()).replace(
      '"people": 100',
      '"people": "many"',
    );

    expectInvalidScenario(json);
  });

  it('should reject a non-finite number', () => {
    const json = JSON.stringify(createGreedyTrapScenario()).replace(
      '"durationMinutes":120',
      '"durationMinutes":1e400',
    );

    expectInvalidScenario(json);
  });

  it('should reject availability without start or end strings', () => {
    const scenario = createGreedyTrapScenario();
    const value = JSON.parse(JSON.stringify(scenario)) as {
      resources: Array<{ availability: Array<{ start?: string; end?: string }> }>;
    };
    delete value.resources[0].availability[0].end;

    expectInvalidScenario(JSON.stringify(value));
  });

  it('should reject a request without resource requirements', () => {
    const scenario = createGreedyTrapScenario();
    const value = JSON.parse(JSON.stringify(scenario)) as {
      requests: Array<{ resourceRequirements?: unknown }>;
    };
    delete value.requests[0].resourceRequirements;

    expectInvalidScenario(JSON.stringify(value));
  });

  it('should reject a requirement without quantity', () => {
    const scenario = createGreedyTrapScenario();
    const value = JSON.parse(JSON.stringify(scenario)) as {
      requests: Array<{ resourceRequirements: Array<{ quantity?: number }> }>;
    };
    delete value.requests[0].resourceRequirements[0].quantity;

    expectInvalidScenario(JSON.stringify(value));
  });

  it('should preserve string case', () => {
    const scenario = createGreedyTrapScenario();
    scenario.resources[0].type = 'RoomMixed';
    scenario.requests[0].resourceRequirements[0].resourceType = 'RoomMixed';
    const result = parseScenarioJson(JSON.stringify(scenario));

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.scenario.resources[0].type).toBe('RoomMixed');
      expect(result.scenario.requests[0].resourceRequirements[0].resourceType).toBe('RoomMixed');
    }
  });

  it('should preserve local datetime strings without timezone conversion', () => {
    const scenario = createGreedyTrapScenario();
    scenario.requests[0].startTime = '2026-09-12T14:35';
    const result = parseScenarioJson(JSON.stringify(scenario));

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.scenario.requests[0].startTime).toBe('2026-09-12T14:35');
    }
  });

  function expectInvalidScenario(json: string): void {
    expect(parseScenarioJson(json)).toEqual({
      success: false,
      message: 'The JSON file does not contain a valid allocation scenario.',
    });
  }
});
