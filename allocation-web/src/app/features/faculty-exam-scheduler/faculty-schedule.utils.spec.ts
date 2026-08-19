import {
  generateExamSlots,
  isValidDailySlot,
  isValidDateRange,
  parseStudentGroups,
} from './faculty-schedule.utils';

describe('faculty schedule utilities', () => {
  it('should generate one concrete slot for one date', () => {
    const slots = generateExamSlots('2026-06-01', '2026-06-01', [
      { id: 'D1', startTime: '09:00', endTime: '12:00' },
    ]);

    expect(slots).toEqual([
      {
        id: 'SLOT_20260601_D1',
        start: '2026-06-01T09:00:00',
        end: '2026-06-01T12:00:00',
      },
    ]);
  });

  it('should generate slots for multiple dates and include both period boundaries', () => {
    const slots = generateExamSlots('2026-06-01', '2026-06-03', [
      { id: 'D1', startTime: '09:00', endTime: '12:00' },
    ]);

    expect(slots).toHaveLength(3);
    expect(slots[0].start).toBe('2026-06-01T09:00:00');
    expect(slots[2].start).toBe('2026-06-03T09:00:00');
  });

  it('should generate every daily slot for every date', () => {
    const slots = generateExamSlots('2026-06-01', '2026-06-02', [
      { id: 'D1', startTime: '09:00', endTime: '12:00' },
      { id: 'D2', startTime: '13:00', endTime: '16:00' },
    ]);

    expect(slots).toHaveLength(4);
    expect(slots.map((slot) => slot.start)).toEqual([
      '2026-06-01T09:00:00',
      '2026-06-01T13:00:00',
      '2026-06-02T09:00:00',
      '2026-06-02T13:00:00',
    ]);
  });

  it('should reject a date range whose start is after its end', () => {
    expect(isValidDateRange('2026-06-03', '2026-06-01')).toBe(false);
    expect(() => generateExamSlots('2026-06-03', '2026-06-01', [
      { id: 'D1', startTime: '09:00', endTime: '12:00' },
    ])).toThrow();
  });

  it('should reject a daily slot whose end is not after its start', () => {
    expect(isValidDailySlot('12:00', '12:00')).toBe(false);
    expect(isValidDailySlot('13:00', '12:00')).toBe(false);
  });

  it('should trim student groups and remove empty values', () => {
    expect(parseStudentGroups(' SI2, RTI2, ,  ')).toEqual(['SI2', 'RTI2']);
  });
});
