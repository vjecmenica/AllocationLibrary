import { Component, HostListener, Input, signal } from '@angular/core';

import {
  FacultyExamAssignmentDto,
  FacultyExamScheduleResponse,
  FacultyExamSlotDto,
  FacultyScheduleInvigilatorDto,
} from '../../core/models/faculty-exam-schedule.models';
import {
  buildFacultyCalendar,
  FacultyCalendarViewModel,
  sortFacultyAssignments,
} from './faculty-schedule-calendar.utils';

type ScheduleView = 'CALENDAR' | 'DETAILS' | 'UNSCHEDULED';

@Component({
  selector: 'app-faculty-exam-schedule-result',
  templateUrl: './faculty-exam-schedule-result.component.html',
  styleUrl: './faculty-exam-schedule-result.component.scss',
})
export class FacultyExamScheduleResultComponent {
  private slotsValue: FacultyExamSlotDto[] = [];
  private resultValue: FacultyExamScheduleResponse | null = null;

  @Input() periodName = '';
  @Input() stale = false;
  @Input() loading = false;
  @Input() errorMessage: string | null = null;

  @Input({ required: true })
  set slots(value: FacultyExamSlotDto[]) {
    this.slotsValue = value ?? [];
    this.refreshViewModel();
  }

  @Input()
  set result(value: FacultyExamScheduleResponse | null) {
    this.resultValue = value;
    this.sortedAssignments.set(sortFacultyAssignments(value?.assignments ?? []));
    this.selectedAssignment.set(null);
    this.refreshViewModel();
  }

  get result(): FacultyExamScheduleResponse | null {
    return this.resultValue;
  }

  readonly activeView = signal<ScheduleView>('CALENDAR');
  readonly selectedAssignment = signal<FacultyExamAssignmentDto | null>(null);
  readonly calendar = signal<FacultyCalendarViewModel>({ weeks: [] });
  readonly sortedAssignments = signal<FacultyExamAssignmentDto[]>([]);

  selectView(view: ScheduleView): void {
    this.activeView.set(view);
    this.selectedAssignment.set(null);
  }

  openDetails(assignment: FacultyExamAssignmentDto): void {
    this.selectedAssignment.set(assignment);
  }

  closeDetails(): void {
    this.selectedAssignment.set(null);
  }

  @HostListener('keydown.escape')
  closeDetailsWithEscape(): void {
    this.closeDetails();
  }

  formatDate(value: string): string {
    const [date] = value.split('T');
    const [year, month, day] = date.split('-');
    return `${day}.${month}.${year}.`;
  }

  formatTime(value: string): string {
    return value.split('T')[1]?.slice(0, 5) ?? value;
  }

  invigilatorNames(invigilators: FacultyScheduleInvigilatorDto[]): string {
    return invigilators.length > 0
      ? invigilators.map((invigilator) => invigilator.name).join(', ')
      : 'Nisu potrebni';
  }

  private refreshViewModel(): void {
    this.calendar.set(buildFacultyCalendar(this.slotsValue, this.resultValue?.assignments ?? []));
  }
}
