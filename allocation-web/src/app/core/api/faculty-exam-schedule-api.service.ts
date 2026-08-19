import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  FacultyExamScheduleRequest,
  FacultyExamScheduleResponse,
} from '../models/faculty-exam-schedule.models';

@Injectable({
  providedIn: 'root',
})
export class FacultyExamScheduleApiService {
  private readonly http = inject(HttpClient);

  scheduleExams(request: FacultyExamScheduleRequest): Observable<FacultyExamScheduleResponse> {
    return this.http.post<FacultyExamScheduleResponse>('/api/faculty/exam-schedule', request);
  }
}
