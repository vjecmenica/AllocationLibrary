import {
  FacultyExamSlotDto,
  FacultyTimeWindowDto,
} from '../../core/models/faculty-exam-schedule.models';

export interface DailySlotInput {
  id: string;
  startTime: string;
  endTime: string;
}

const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const LOCAL_TIME_PATTERN = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const LOCAL_DATE_TIME_PATTERN =
  /^(\d{4}-\d{2}-\d{2})T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/;

export function isValidIsoDate(value: string): boolean {
  const match = ISO_DATE_PATTERN.exec(value);
  if (!match) {
    return false;
  }

  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const candidate = new Date(Date.UTC(year, month - 1, day));

  return (
    candidate.getUTCFullYear() === year &&
    candidate.getUTCMonth() === month - 1 &&
    candidate.getUTCDate() === day
  );
}

export function isValidDateRange(startDate: string, endDate: string): boolean {
  return isValidIsoDate(startDate) && isValidIsoDate(endDate) && startDate <= endDate;
}

export function isValidDailySlot(startTime: string, endTime: string): boolean {
  return (
    LOCAL_TIME_PATTERN.test(startTime) &&
    LOCAL_TIME_PATTERN.test(endTime) &&
    startTime < endTime
  );
}

export function generateExamSlots(
  startDate: string,
  endDate: string,
  dailySlots: readonly DailySlotInput[],
): FacultyExamSlotDto[] {
  if (!isValidDateRange(startDate, endDate)) {
    throw new Error('Invalid exam period date range.');
  }
  if (dailySlots.length === 0 || dailySlots.some((slot) => !isValidDailySlot(slot.startTime, slot.endTime))) {
    throw new Error('At least one valid daily exam slot is required.');
  }

  const result: FacultyExamSlotDto[] = [];
  for (let date = startDate; date <= endDate; date = nextIsoDate(date)) {
    for (const dailySlot of dailySlots) {
      result.push({
        id: `SLOT_${date.replaceAll('-', '')}_${dailySlot.id}`,
        start: `${date}T${dailySlot.startTime}:00`,
        end: `${date}T${dailySlot.endTime}:00`,
      });
    }
  }
  return result;
}

export function fullPeriodAvailability(startDate: string, endDate: string): FacultyTimeWindowDto {
  if (!isValidDateRange(startDate, endDate)) {
    throw new Error('Invalid exam period date range.');
  }

  return {
    start: `${startDate}T00:00:00`,
    end: `${nextIsoDate(endDate)}T00:00:00`,
  };
}

export function normalizeLocalDateTime(value: string): string {
  if (value.length === 16) {
    return `${value}:00`;
  }
  return value;
}

export function isValidLocalDateTime(value: string): boolean {
  const match = LOCAL_DATE_TIME_PATTERN.exec(value);
  return match !== null && isValidIsoDate(match[1]);
}

export function isValidLocalDateTimeRange(start: string, end: string): boolean {
  return (
    isValidLocalDateTime(start) &&
    isValidLocalDateTime(end) &&
    normalizeLocalDateTime(start) < normalizeLocalDateTime(end)
  );
}

export function parseStudentGroups(value: string): string[] {
  return value
    .split(',')
    .map((group) => group.trim())
    .filter((group) => group.length > 0);
}

function nextIsoDate(value: string): string {
  const match = ISO_DATE_PATTERN.exec(value);
  if (!match) {
    throw new Error('Invalid ISO date.');
  }

  const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
  date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}
