import { AllocationScenario } from './scenario-editor/scenario-editor.models';

export function createGreedyTrapScenario(): AllocationScenario {
  return {
    resources: [
      {
        id: 'R_BIG',
        name: 'Large room',
        type: 'ROOM',
        capacities: { people: 100 },
        availability: [
          {
            start: '2026-07-01T08:00:00',
            end: '2026-07-01T18:00:00',
          },
        ],
      },
      {
        id: 'R_SMALL',
        name: 'Small room',
        type: 'ROOM',
        capacities: { people: 30 },
        availability: [
          {
            start: '2026-07-01T08:00:00',
            end: '2026-07-01T18:00:00',
          },
        ],
      },
    ],
    requests: [
      {
        id: 'REQ_SMALL',
        name: 'Small exam',
        startTime: '2026-07-01T10:00:00',
        durationMinutes: 120,
        priority: 10,
        resourceRequirements: [
          {
            resourceType: 'ROOM',
            quantity: 1,
            requiredCapacities: { people: 30 },
          },
        ],
      },
      {
        id: 'REQ_BIG',
        name: 'Large exam',
        startTime: '2026-07-01T10:00:00',
        durationMinutes: 120,
        priority: 9,
        resourceRequirements: [
          {
            resourceType: 'ROOM',
            quantity: 1,
            requiredCapacities: { people: 100 },
          },
        ],
      },
    ],
  };
}
