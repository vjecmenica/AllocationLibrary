import { FacultyTimeWindowDto } from '../../core/models/faculty-exam-schedule.models';
import {
  isValidDailySlot,
  isValidDateRange,
  isValidIsoDate,
} from './faculty-schedule.utils';

export interface FacultyScheduleDailySlot {
  startTime: string;
  endTime: string;
}

export interface FacultyScheduleExam {
  id: string;
  code: string;
  name: string;
  studentCount: number;
  durationMinutes: number;
  requiredInvigilators: number;
  studentGroups: string[];
}

export interface FacultyScheduleRoom {
  id: string;
  name: string;
  capacity: number;
  availableEntirePeriod: boolean;
  availability: FacultyTimeWindowDto[];
}

export interface FacultyScheduleInvigilator {
  id: string;
  name: string;
  availableEntirePeriod: boolean;
  availability: FacultyTimeWindowDto[];
}

export interface FacultyScheduleConfiguration {
  periodName: string;
  startDate: string;
  endDate: string;
  dailySlots: FacultyScheduleDailySlot[];
  exams: FacultyScheduleExam[];
  rooms: FacultyScheduleRoom[];
  invigilators: FacultyScheduleInvigilator[];
}

export interface FacultyScheduleFile {
  schemaVersion: 1;
  schedule: FacultyScheduleConfiguration;
}

export type FacultyScheduleParseResult =
  | { success: true; schedule: FacultyScheduleConfiguration }
  | { success: false; message: string };

const INVALID_JSON_MESSAGE = 'Izabrani fajl nije validan JSON.';
const INVALID_SCHEDULE_MESSAGE = 'Izabrani fajl nije validan Faculty scenario.';
const UNSUPPORTED_VERSION_MESSAGE = 'Verzija formata nije podržana.';

export function serializeFacultySchedule(schedule: FacultyScheduleConfiguration): string {
  const file: FacultyScheduleFile = {
    schemaVersion: 1,
    schedule,
  };
  return JSON.stringify(file, null, 2);
}

export function parseFacultyScheduleJson(json: string): FacultyScheduleParseResult {
  let parsed: unknown;
  try {
    parsed = JSON.parse(json) as unknown;
  } catch {
    return { success: false, message: INVALID_JSON_MESSAGE };
  }

  if (!isPlainObject(parsed)) {
    return invalidSchedule();
  }

  if (ownValue(parsed, 'schemaVersion') !== 1) {
    return Object.hasOwn(parsed, 'schemaVersion')
      ? { success: false, message: UNSUPPORTED_VERSION_MESSAGE }
      : invalidSchedule();
  }

  const schedule = parseSchedule(ownValue(parsed, 'schedule'));
  return schedule === null
    ? invalidSchedule()
    : { success: true, schedule };
}

function parseSchedule(value: unknown): FacultyScheduleConfiguration | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const periodName = requiredString(value, 'periodName');
  const startDate = requiredString(value, 'startDate');
  const endDate = requiredString(value, 'endDate');
  const dailySlots = parseArray(ownValue(value, 'dailySlots'), parseDailySlot);
  const exams = parseArray(ownValue(value, 'exams'), parseExam);
  const rooms = parseArray(ownValue(value, 'rooms'), parseRoom);
  const invigilators = parseArray(ownValue(value, 'invigilators'), parseInvigilator);

  if (
    periodName === null ||
    startDate === null ||
    endDate === null ||
    !isValidDateRange(startDate, endDate) ||
    dailySlots === null ||
    dailySlots.length === 0 ||
    hasDuplicate(dailySlots.map((slot) => slot.startTime)) ||
    exams === null ||
    exams.length === 0 ||
    hasDuplicate(exams.map((exam) => exam.id)) ||
    rooms === null ||
    rooms.length === 0 ||
    hasDuplicate(rooms.map((room) => room.id)) ||
    invigilators === null ||
    hasDuplicate(invigilators.map((invigilator) => invigilator.id))
  ) {
    return null;
  }

  return {
    periodName,
    startDate,
    endDate,
    dailySlots,
    exams,
    rooms,
    invigilators,
  };
}

function parseDailySlot(value: unknown): FacultyScheduleDailySlot | null {
  if (!isPlainObject(value)) {
    return null;
  }
  const startTime = requiredString(value, 'startTime');
  const endTime = requiredString(value, 'endTime');
  return startTime !== null && endTime !== null && isValidDailySlot(startTime, endTime)
    ? { startTime, endTime }
    : null;
}

function parseExam(value: unknown): FacultyScheduleExam | null {
  if (!isPlainObject(value)) {
    return null;
  }

  const id = requiredString(value, 'id');
  const code = requiredString(value, 'code');
  const name = requiredString(value, 'name');
  const studentCount = integer(value, 'studentCount');
  const durationMinutes = integer(value, 'durationMinutes');
  const requiredInvigilators = integer(value, 'requiredInvigilators');
  const studentGroups = stringArray(ownValue(value, 'studentGroups'));

  if (
    id === null || code === null || name === null ||
    studentCount === null || studentCount <= 0 ||
    durationMinutes === null || durationMinutes <= 0 ||
    requiredInvigilators === null || requiredInvigilators < 0 ||
    studentGroups === null
  ) {
    return null;
  }

  return {
    id,
    code,
    name,
    studentCount,
    durationMinutes,
    requiredInvigilators,
    studentGroups,
  };
}

function parseRoom(value: unknown): FacultyScheduleRoom | null {
  if (!isPlainObject(value)) {
    return null;
  }
  const id = requiredString(value, 'id');
  const name = requiredString(value, 'name');
  const capacity = integer(value, 'capacity');
  const availableEntirePeriod = booleanValue(value, 'availableEntirePeriod');
  const availability = parseArray(ownValue(value, 'availability'), parseTimeWindow);
  return id !== null && name !== null && capacity !== null && capacity > 0 &&
    availableEntirePeriod !== null && availability !== null
    ? { id, name, capacity, availableEntirePeriod, availability }
    : null;
}

function parseInvigilator(value: unknown): FacultyScheduleInvigilator | null {
  if (!isPlainObject(value)) {
    return null;
  }
  const id = requiredString(value, 'id');
  const name = requiredString(value, 'name');
  const availableEntirePeriod = booleanValue(value, 'availableEntirePeriod');
  const availability = parseArray(ownValue(value, 'availability'), parseTimeWindow);
  return id !== null && name !== null && availableEntirePeriod !== null && availability !== null
    ? { id, name, availableEntirePeriod, availability }
    : null;
}

function parseTimeWindow(value: unknown): FacultyTimeWindowDto | null {
  if (!isPlainObject(value)) {
    return null;
  }
  const start = requiredString(value, 'start');
  const end = requiredString(value, 'end');
  return start !== null && end !== null && isValidLocalDateTime(start) &&
    isValidLocalDateTime(end) && start < end
    ? { start, end }
    : null;
}

function parseArray<T>(value: unknown, parser: (item: unknown) => T | null): T[] | null {
  if (!Array.isArray(value)) {
    return null;
  }
  const parsed: T[] = [];
  for (const item of value) {
    const candidate = parser(item);
    if (candidate === null) {
      return null;
    }
    parsed.push(candidate);
  }
  return parsed;
}

function requiredString(value: Record<string, unknown>, key: string): string | null {
  const candidate = ownValue(value, key);
  if (typeof candidate !== 'string' || candidate.trim().length === 0) {
    return null;
  }
  return candidate.trim();
}

function stringArray(value: unknown): string[] | null {
  if (!Array.isArray(value)) {
    return null;
  }
  const strings: string[] = [];
  for (const item of value) {
    if (typeof item !== 'string' || item.trim().length === 0) {
      return null;
    }
    strings.push(item.trim());
  }
  return strings;
}

function integer(value: Record<string, unknown>, key: string): number | null {
  const candidate = ownValue(value, key);
  return typeof candidate === 'number' && Number.isFinite(candidate) && Number.isInteger(candidate)
    ? candidate
    : null;
}

function booleanValue(value: Record<string, unknown>, key: string): boolean | null {
  const candidate = ownValue(value, key);
  return typeof candidate === 'boolean' ? candidate : null;
}

function hasDuplicate(values: readonly string[]): boolean {
  return new Set(values).size !== values.length;
}

function isValidLocalDateTime(value: string): boolean {
  const match = /^(\d{4}-\d{2}-\d{2})T([01]\d|2[0-3]):([0-5]\d)(?::([0-5]\d))?$/.exec(value);
  if (!match) {
    return false;
  }
  return isValidIsoDate(match[1]);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return false;
  }
  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

function ownValue(value: Record<string, unknown>, key: string): unknown {
  return Object.hasOwn(value, key) ? value[key] : undefined;
}

function invalidSchedule(): FacultyScheduleParseResult {
  return { success: false, message: INVALID_SCHEDULE_MESSAGE };
}
