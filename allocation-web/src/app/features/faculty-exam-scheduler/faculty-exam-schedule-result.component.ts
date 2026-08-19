import { Component, Input } from '@angular/core';

import {
  FacultyExamScheduleResponse,
  FacultyScheduleInvigilatorDto,
} from '../../core/models/faculty-exam-schedule.models';

@Component({
  selector: 'app-faculty-exam-schedule-result',
  templateUrl: './faculty-exam-schedule-result.component.html',
  styleUrl: './faculty-exam-schedule-result.component.scss',
})
export class FacultyExamScheduleResultComponent {
  @Input({ required: true }) result!: FacultyExamScheduleResponse;
  @Input({ required: true }) periodName = '';

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
}
