import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FacultyExamScheduleRequest } from '../models/faculty-exam-schedule.models';
import { FacultyExamScheduleApiService } from './faculty-exam-schedule-api.service';

describe('FacultyExamScheduleApiService', () => {
  let service: FacultyExamScheduleApiService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(FacultyExamScheduleApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should post the faculty request to /api/faculty/exam-schedule', () => {
    const body: FacultyExamScheduleRequest = {
      slots: [],
      exams: [],
      rooms: [],
      invigilators: [],
    };

    service.scheduleExams(body).subscribe();

    const request = httpTestingController.expectOne('/api/faculty/exam-schedule');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ assignments: [], unscheduledExams: [], statistics: {} });
  });
});
