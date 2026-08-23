import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { AllocationApiService } from './core/api/allocation-api.service';
import { FacultyExamScheduleApiService } from './core/api/faculty-exam-schedule-api.service';
import { AllocationPageComponent } from './features/allocation/allocation-page.component';
import { FacultyExamSchedulerPageComponent } from './features/faculty-exam-scheduler/faculty-exam-scheduler-page.component';
import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter(routes),
        {
          provide: FacultyExamScheduleApiService,
          useValue: { scheduleExams: vi.fn() },
        },
        {
          provide: AllocationApiService,
          useValue: {
            getHealth: vi.fn(() => of({ status: 'UP' })),
            executeAllocation: vi.fn(),
            compareAllocations: vi.fn(),
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should display the Faculty Exam Scheduler on the root route', async () => {
    const harness = await RouterTestingHarness.create();

    const component = await harness.navigateByUrl('/', FacultyExamSchedulerPageComponent);

    expect(component).toBeInstanceOf(FacultyExamSchedulerPageComponent);
    expect(harness.routeNativeElement?.textContent).toContain('Raspored ispita');
  });

  it('should preserve the experimental Allocation UI on /analysis', async () => {
    const harness = await RouterTestingHarness.create();

    const component = await harness.navigateByUrl('/analysis', AllocationPageComponent);

    expect(component).toBeInstanceOf(AllocationPageComponent);
    expect(harness.routeNativeElement?.textContent).toContain('AllocationLibrary');
    expect(harness.routeNativeElement?.textContent).toContain(
      'Experimental and analytical interface',
    );
  });
});
