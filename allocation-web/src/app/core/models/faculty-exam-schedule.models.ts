export interface FacultyTimeWindowDto {
  start: string;
  end: string;
}

export interface FacultyExamSlotDto {
  id: string;
  start: string;
  end: string;
}

export interface FacultyExamDto {
  id: string;
  code: string;
  name: string;
  studentCount: number;
  durationMinutes: number;
  requiredInvigilators: number;
  studentGroups: string[];
}

export interface FacultyRoomDto {
  id: string;
  name: string;
  capacity: number;
  availability: FacultyTimeWindowDto[];
}

export interface FacultyInvigilatorDto {
  id: string;
  name: string;
  availability: FacultyTimeWindowDto[];
}

export interface FacultyExamScheduleRequest {
  slots: FacultyExamSlotDto[];
  exams: FacultyExamDto[];
  rooms: FacultyRoomDto[];
  invigilators: FacultyInvigilatorDto[];
}

export interface FacultyScheduleRoomDto {
  id: string;
  name: string;
  capacity: number;
}

export interface FacultyScheduleInvigilatorDto {
  id: string;
  name: string;
}

export interface FacultyExamAssignmentDto {
  examId: string;
  examCode: string;
  examName: string;
  studentCount: number;
  slotId: string;
  slotStart: string;
  slotEnd: string;
  actualEnd: string;
  room: FacultyScheduleRoomDto;
  invigilators: FacultyScheduleInvigilatorDto[];
}

export interface FacultyUnscheduledExamDto {
  examId: string;
  examCode: string;
  examName: string;
  studentCount: number;
  reason: string;
}

export interface FacultyExamScheduleStatistics {
  totalExams: number;
  scheduledExams: number;
  unscheduledExams: number;
  solverStatus: string;
  executionTimeMs: number;
  stoppedByLimit: boolean;
}

export interface FacultyExamScheduleResponse {
  assignments: FacultyExamAssignmentDto[];
  unscheduledExams: FacultyUnscheduledExamDto[];
  statistics: FacultyExamScheduleStatistics;
}
