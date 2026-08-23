import demoFile from '../../../../public/demo/faculty-exam-schedule-demo.json';

import {
  FacultyScheduleConfiguration,
  parseFacultyScheduleJson,
  serializeFacultySchedule,
} from './faculty-schedule-json';

describe('faculty schedule JSON utilities', () => {
  it('should parse a valid schema version 1 file', () => {
    expect(parseFacultyScheduleJson(serializeFacultySchedule(validSchedule()))).toEqual({
      success: true,
      schedule: validSchedule(),
    });
  });

  it('should reject invalid JSON', () => {
    expect(parseFacultyScheduleJson('{not-json')).toEqual({
      success: false,
      message: 'Izabrani fajl nije validan JSON.',
    });
  });

  it('should reject an unsupported schema version', () => {
    expect(parseFacultyScheduleJson('{"schemaVersion":2,"schedule":{}}')).toEqual({
      success: false,
      message: 'Verzija formata nije podržana.',
    });
  });

  it('should reject a missing schedule', () => {
    expectInvalid('{"schemaVersion":1}');
  });

  it('should reject missing required arrays', () => {
    const schedule = validSchedule();
    const { rooms: _rooms, ...withoutRooms } = schedule;
    expectInvalid(JSON.stringify({ schemaVersion: 1, schedule: withoutRooms }));
  });

  it('should reject invalid numeric fields', () => {
    const schedule = validSchedule();
    schedule.exams[0].studentCount = Number.POSITIVE_INFINITY;
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject a decimal student count', () => {
    const schedule = validSchedule();
    schedule.exams[0].studentCount = 12.5;
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject Faculty integers above the Java Integer maximum', () => {
    const examFields: Array<'studentCount' | 'durationMinutes' | 'requiredInvigilators'> = [
      'studentCount',
      'durationMinutes',
      'requiredInvigilators',
    ];

    for (const field of examFields) {
      const schedule = validSchedule();
      schedule.exams[0][field] = 2147483648;
      expectInvalid(serializeFacultySchedule(schedule));
    }

    const schedule = validSchedule();
    schedule.rooms[0].capacity = 2147483648;
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should accept the Java Integer maximum for every Faculty integer field', () => {
    const schedule = validSchedule();
    schedule.exams[0].studentCount = 2147483647;
    schedule.exams[0].durationMinutes = 2147483647;
    schedule.exams[0].requiredInvigilators = 2147483647;
    schedule.rooms[0].capacity = 2147483647;

    expect(parseFacultyScheduleJson(serializeFacultySchedule(schedule))).toEqual({
      success: true,
      schedule,
    });
  });

  it('should reject invalid availability structures', () => {
    const value = fileRecord();
    const schedule = value['schedule'] as Record<string, unknown>;
    const rooms = schedule['rooms'] as Array<Record<string, unknown>>;
    rooms[0]['availability'] = [{ start: '2026-06-15T08:00' }];
    expectInvalid(JSON.stringify(value));
  });

  it('should accept a normal local date-time interval without seconds', () => {
    const schedule = validSchedule();
    schedule.rooms[0].availability = [
      { start: '2026-06-15T09:00', end: '2026-06-15T10:00' },
    ];

    expect(parseFacultyScheduleJson(serializeFacultySchedule(schedule))).toEqual({
      success: true,
      schedule,
    });
  });

  it.each([
    ['2026-06-15T09:00', '2026-06-15T09:00:00'],
    ['2026-06-15T09:00:00', '2026-06-15T09:00'],
  ])('should reject equal local date-times with different precision', (start, end) => {
    const schedule = validSchedule();
    schedule.rooms[0].availability = [{ start, end }];

    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should accept a positive interval ending within the same minute', () => {
    const schedule = validSchedule();
    schedule.rooms[0].availability = [
      { start: '2026-06-15T09:00', end: '2026-06-15T09:00:30' },
    ];

    expect(parseFacultyScheduleJson(serializeFacultySchedule(schedule))).toEqual({
      success: true,
      schedule,
    });
  });

  it('should reject a reversed local date-time interval', () => {
    const schedule = validSchedule();
    schedule.rooms[0].availability = [
      { start: '2026-06-15T10:00', end: '2026-06-15T09:00' },
    ];

    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject duplicate exam IDs', () => {
    const schedule = validSchedule();
    schedule.exams.push({ ...schedule.exams[0] });
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject duplicate room IDs', () => {
    const schedule = validSchedule();
    schedule.rooms.push({ ...schedule.rooms[0], availability: [] });
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject duplicate invigilator IDs', () => {
    const schedule = validSchedule();
    schedule.invigilators.push({ ...schedule.invigilators[0], availability: [] });
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject duplicate daily start times', () => {
    const schedule = validSchedule();
    schedule.dailySlots.push({ startTime: '09:00', endTime: '10:00' });
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject an invalid period', () => {
    const schedule = validSchedule();
    schedule.endDate = '2026-06-14';
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject an invalid daily slot', () => {
    const schedule = validSchedule();
    schedule.dailySlots[0].endTime = '08:00';
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should reject blank student groups', () => {
    const schedule = validSchedule();
    schedule.exams[0].studentGroups.push('   ');
    expectInvalid(serializeFacultySchedule(schedule));
  });

  it('should accept an empty invigilator list', () => {
    const schedule = validSchedule();
    schedule.invigilators = [];
    expect(parseFacultyScheduleJson(serializeFacultySchedule(schedule))).toEqual({
      success: true,
      schedule,
    });
  });

  it('should preserve configuration semantics through serialize and parse', () => {
    const schedule = validSchedule();
    const result = parseFacultyScheduleJson(serializeFacultySchedule(schedule));

    expect(result).toEqual({ success: true, schedule });
    expect(serializeFacultySchedule(schedule)).toContain('\n  "schemaVersion": 1,');
  });

  it('should parse the committed demonstration file', () => {
    const result = parseFacultyScheduleJson(JSON.stringify(demoFile));

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.schedule.exams).toHaveLength(24);
      expect(result.schedule.rooms).toHaveLength(6);
      expect(result.schedule.invigilators).toHaveLength(12);
      expect(result.schedule.dailySlots).toHaveLength(3);
      expect(result.schedule.startDate).toBe('2026-06-15');
      expect(result.schedule.endDate).toBe('2026-06-21');
      expect(result.schedule.rooms.some((room) => !room.availableEntirePeriod)).toBe(true);
    }
  });

  it('should include student-group overlap in the demonstration file', () => {
    const result = parseFacultyScheduleJson(JSON.stringify(demoFile));
    expect(result.success).toBe(true);
    if (!result.success) {
      return;
    }

    const groupCounts = new Map<string, number>();
    result.schedule.exams.flatMap((exam) => exam.studentGroups).forEach((group) => {
      groupCounts.set(group, (groupCounts.get(group) ?? 0) + 1);
    });
    expect([...groupCounts.values()].some((count) => count > 1)).toBe(true);
  });

  function expectInvalid(json: string): void {
    expect(parseFacultyScheduleJson(json)).toEqual({
      success: false,
      message: 'Izabrani fajl nije validan Faculty scenario.',
    });
  }
});

function validSchedule(): FacultyScheduleConfiguration {
  return {
    periodName: 'Junski ispitni rok 2026',
    startDate: '2026-06-15',
    endDate: '2026-06-21',
    dailySlots: [
      { startTime: '09:00', endTime: '12:00' },
      { startTime: '13:00', endTime: '16:00' },
    ],
    exams: [{
      id: 'EXAM_1',
      code: 'MAT1',
      name: 'Matematika 1',
      studentCount: 120,
      durationMinutes: 120,
      requiredInvigilators: 2,
      studentGroups: ['SI1', 'RTI1'],
    }],
    rooms: [{
      id: 'ROOM_1',
      name: 'Amfiteatar',
      capacity: 180,
      availableEntirePeriod: false,
      availability: [{ start: '2026-06-15T08:00', end: '2026-06-17T20:00' }],
    }],
    invigilators: [{
      id: 'INV_1',
      name: 'Demo dežurni',
      availableEntirePeriod: true,
      availability: [],
    }],
  };
}

function fileRecord(): Record<string, unknown> {
  return JSON.parse(serializeFacultySchedule(validSchedule())) as Record<string, unknown>;
}
