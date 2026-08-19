import {
  FacultyExamAssignmentDto,
  FacultyExamSlotDto,
} from '../../core/models/faculty-exam-schedule.models';

export interface FacultyCalendarDay {
  date: string;
  dayName: string;
  dateLabel: string;
}

export interface FacultyCalendarCell {
  date: string;
  startTime: string;
  assignments: FacultyExamAssignmentDto[];
}

export interface FacultyCalendarRow {
  startTime: string;
  endTime: string;
  cells: FacultyCalendarCell[];
}

export interface FacultyCalendarWeek {
  id: string;
  label: string;
  days: FacultyCalendarDay[];
  rows: FacultyCalendarRow[];
}

export interface FacultyCalendarViewModel {
  weeks: FacultyCalendarWeek[];
}

const DAY_NAMES = ['NED', 'PON', 'UTO', 'SRE', 'ČET', 'PET', 'SUB'];
const MONTH_NAMES = [
  'januar',
  'februar',
  'mart',
  'april',
  'maj',
  'jun',
  'jul',
  'avgust',
  'septembar',
  'oktobar',
  'novembar',
  'decembar',
];

export function buildFacultyCalendar(
  slots: readonly FacultyExamSlotDto[],
  assignments: readonly FacultyExamAssignmentDto[] = [],
): FacultyCalendarViewModel {
  const sortedSlots = [...slots].sort((left, right) =>
    left.start.localeCompare(right.start) || left.id.localeCompare(right.id),
  );
  const sortedAssignments = sortFacultyAssignments(assignments);
  const dates = unique(sortedSlots.map((slot) => datePart(slot.start)));
  const timeRows = uniqueBy(
    sortedSlots.map((slot) => ({
      startTime: timePart(slot.start),
      endTime: timePart(slot.end),
    })),
    (slot) => slot.startTime,
  ).sort((left, right) => left.startTime.localeCompare(right.startTime));

  const assignmentsByCell = new Map<string, FacultyExamAssignmentDto[]>();
  for (const assignment of sortedAssignments) {
    const key = cellKey(datePart(assignment.slotStart), timePart(assignment.slotStart));
    const cellAssignments = assignmentsByCell.get(key) ?? [];
    cellAssignments.push(assignment);
    assignmentsByCell.set(key, cellAssignments);
  }

  const weekDates = new Map<string, string[]>();
  for (const date of dates) {
    const weekId = mondayOfWeek(date);
    const currentDates = weekDates.get(weekId) ?? [];
    currentDates.push(date);
    weekDates.set(weekId, currentDates);
  }

  return {
    weeks: [...weekDates.entries()].map(([id, activeDates]) => {
      const days = activeDates.map(toCalendarDay);
      return {
        id,
        label: weekLabel(activeDates[0], activeDates[activeDates.length - 1]),
        days,
        rows: timeRows.map((row) => ({
          ...row,
          cells: days.map((day) => ({
            date: day.date,
            startTime: row.startTime,
            assignments: [...(assignmentsByCell.get(cellKey(day.date, row.startTime)) ?? [])],
          })),
        })),
      };
    }),
  };
}

export function sortFacultyAssignments(
  assignments: readonly FacultyExamAssignmentDto[],
): FacultyExamAssignmentDto[] {
  return [...assignments].sort((left, right) =>
    left.slotStart.localeCompare(right.slotStart)
      || left.examCode.localeCompare(right.examCode)
      || left.room.name.localeCompare(right.room.name),
  );
}

function toCalendarDay(date: string): FacultyCalendarDay {
  const parsed = parseIsoDate(date);
  return {
    date,
    dayName: DAY_NAMES[parsed.getUTCDay()],
    dateLabel: `${twoDigits(parsed.getUTCDate())}.${twoDigits(parsed.getUTCMonth() + 1)}.`,
  };
}

function mondayOfWeek(date: string): string {
  const parsed = parseIsoDate(date);
  const daysSinceMonday = (parsed.getUTCDay() + 6) % 7;
  parsed.setUTCDate(parsed.getUTCDate() - daysSinceMonday);
  return toIsoDate(parsed);
}

function weekLabel(firstDate: string, lastDate: string): string {
  const first = parseIsoDate(firstDate);
  const last = parseIsoDate(lastDate);
  if (firstDate === lastDate) {
    return `${first.getUTCDate()}. ${MONTH_NAMES[first.getUTCMonth()]} ${first.getUTCFullYear()}.`;
  }
  if (first.getUTCFullYear() === last.getUTCFullYear() && first.getUTCMonth() === last.getUTCMonth()) {
    return `${first.getUTCDate()}–${last.getUTCDate()}. ${MONTH_NAMES[first.getUTCMonth()]} ${first.getUTCFullYear()}.`;
  }
  return `${first.getUTCDate()}. ${MONTH_NAMES[first.getUTCMonth()]} – ${last.getUTCDate()}. ${MONTH_NAMES[last.getUTCMonth()]} ${last.getUTCFullYear()}.`;
}

function parseIsoDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day));
}

function toIsoDate(value: Date): string {
  return value.toISOString().slice(0, 10);
}

function datePart(value: string): string {
  return value.slice(0, 10);
}

function timePart(value: string): string {
  return value.slice(11, 16);
}

function cellKey(date: string, startTime: string): string {
  return `${date}|${startTime}`;
}

function twoDigits(value: number): string {
  return String(value).padStart(2, '0');
}

function unique<T>(values: readonly T[]): T[] {
  return [...new Set(values)];
}

function uniqueBy<T>(values: readonly T[], key: (value: T) => string): T[] {
  const seen = new Set<string>();
  return values.filter((value) => {
    const valueKey = key(value);
    if (seen.has(valueKey)) {
      return false;
    }
    seen.add(valueKey);
    return true;
  });
}
