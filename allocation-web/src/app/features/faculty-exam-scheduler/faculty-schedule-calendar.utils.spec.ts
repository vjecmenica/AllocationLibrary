import {
  FacultyExamAssignmentDto,
  FacultyExamSlotDto,
} from '../../core/models/faculty-exam-schedule.models';
import { buildFacultyCalendar, sortFacultyAssignments } from './faculty-schedule-calendar.utils';

describe('faculty schedule calendar utilities', () => {
  it('should preserve every active date and group dates by calendar week', () => {
    const calendar = buildFacultyCalendar(slotsForDates([
      '2026-06-01',
      '2026-06-02',
      '2026-06-08',
    ]));

    expect(calendar.weeks).toHaveLength(2);
    expect(calendar.weeks[0].days.map((day) => day.date)).toEqual([
      '2026-06-01',
      '2026-06-02',
    ]);
    expect(calendar.weeks[1].days.map((day) => day.date)).toEqual(['2026-06-08']);
  });

  it('should keep an inclusive period longer than seven days in multiple week groups', () => {
    const dates = Array.from({ length: 9 }, (_, index) => `2026-06-${String(index + 1).padStart(2, '0')}`);

    const calendar = buildFacultyCalendar(slotsForDates(dates));

    expect(calendar.weeks).toHaveLength(2);
    expect(calendar.weeks.flatMap((week) => week.days)).toHaveLength(9);
  });

  it('should create a row for every allowed daily slot start time', () => {
    const calendar = buildFacultyCalendar([
      slot('D1', '2026-06-01', '09:00', '12:00'),
      slot('D2', '2026-06-01', '13:00', '16:00'),
      slot('D3', '2026-06-01', '17:00', '20:00'),
    ]);

    expect(calendar.weeks[0].rows.map((row) => row.startTime)).toEqual(['09:00', '13:00', '17:00']);
  });

  it('should retain empty days and empty cells', () => {
    const calendar = buildFacultyCalendar(slotsForDates(['2026-06-01', '2026-06-02']), [
      assignment('A', '2026-06-01T09:00:00', 'ALG', 'Sala 1'),
    ]);

    expect(calendar.weeks[0].days).toHaveLength(2);
    expect(calendar.weeks[0].rows[0].cells[1].assignments).toEqual([]);
  });

  it('should put an assignment in its matching date and start-time cell', () => {
    const expected = assignment('A', '2026-06-02T09:00:00', 'ALG', 'Sala 1');
    const calendar = buildFacultyCalendar(slotsForDates(['2026-06-01', '2026-06-02']), [expected]);

    expect(calendar.weeks[0].rows[0].cells[1].assignments).toEqual([expected]);
  });

  it('should support multiple parallel exams in one cell with stable ordering', () => {
    const calendar = buildFacultyCalendar(slotsForDates(['2026-06-01']), [
      assignment('B', '2026-06-01T09:00:00', 'OOP2', 'Sala 203'),
      assignment('A', '2026-06-01T09:00:00', 'ALG', 'Amfiteatar A'),
    ]);

    expect(calendar.weeks[0].rows[0].cells[0].assignments.map((item) => item.examCode)).toEqual([
      'ALG',
      'OOP2',
    ]);
  });

  it('should sort assignments by slot start, exam code, and room name without mutation', () => {
    const input = [
      assignment('C', '2026-06-02T09:00:00', 'MAT', 'Sala 2'),
      assignment('B', '2026-06-01T09:00:00', 'OOP', 'Sala 2'),
      assignment('A', '2026-06-01T09:00:00', 'OOP', 'Sala 1'),
    ];
    const snapshot = structuredClone(input);

    const sorted = sortFacultyAssignments(input);

    expect(sorted.map((item) => item.examId)).toEqual(['A', 'B', 'C']);
    expect(input).toEqual(snapshot);
  });
});

function slotsForDates(dates: string[]): FacultyExamSlotDto[] {
  return dates.map((date, index) => slot(`D${index}`, date, '09:00', '12:00'));
}

function slot(id: string, date: string, start: string, end: string): FacultyExamSlotDto {
  return { id, start: `${date}T${start}:00`, end: `${date}T${end}:00` };
}

function assignment(
  examId: string,
  slotStart: string,
  examCode: string,
  roomName: string,
): FacultyExamAssignmentDto {
  return {
    examId,
    examCode,
    examName: `Predmet ${examCode}`,
    studentCount: 40,
    slotId: `SLOT_${examId}`,
    slotStart,
    slotEnd: `${slotStart.slice(0, 11)}12:00:00`,
    actualEnd: `${slotStart.slice(0, 11)}10:30:00`,
    room: { id: `ROOM_${examId}`, name: roomName, capacity: 60 },
    invigilators: [{ id: 'I1', name: 'Ana Petrović' }],
  };
}
