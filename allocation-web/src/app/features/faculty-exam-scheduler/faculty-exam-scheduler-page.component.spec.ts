import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of, Subject, throwError } from 'rxjs';

import { FacultyExamScheduleApiService } from '../../core/api/faculty-exam-schedule-api.service';
import {
  FacultyExamScheduleRequest,
  FacultyExamScheduleResponse,
} from '../../core/models/faculty-exam-schedule.models';
import { FacultyExamSchedulerPageComponent } from './faculty-exam-scheduler-page.component';

describe('FacultyExamSchedulerPageComponent', () => {
  let fixture: ComponentFixture<FacultyExamSchedulerPageComponent>;
  let component: FacultyExamSchedulerPageComponent;
  let api: { scheduleExams: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { scheduleExams: vi.fn(() => of(scheduleResponse())) };

    await TestBed.configureTestingModule({
      imports: [FacultyExamSchedulerPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        { provide: FacultyExamScheduleApiService, useValue: api },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FacultyExamSchedulerPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should submit a valid faculty request built from the current form', () => {
    component.generateSchedule();

    expect(api.scheduleExams).toHaveBeenCalledOnce();
    const request = api.scheduleExams.mock.calls[0][0] as FacultyExamScheduleRequest;
    expect(request.slots).toHaveLength(6);
    expect(request.slots[0]).toEqual({
      id: 'SLOT_20260601_D1',
      start: '2026-06-01T09:00:00',
      end: '2026-06-01T12:00:00',
    });
    expect(request.exams[0].studentGroups).toEqual(['SI1', 'RTI1']);
    expect(request.rooms[0].availability).toEqual([
      { start: '2026-06-01T00:00:00', end: '2026-06-03T00:00:00' },
    ]);
  });

  it('should not include algorithm or solver configuration in the faculty request', () => {
    component.generateSchedule();

    const request = api.scheduleExams.mock.calls[0][0] as FacultyExamScheduleRequest;
    expect(Object.keys(request).sort()).toEqual(['exams', 'invigilators', 'rooms', 'slots']);
    expect(JSON.stringify(request)).not.toMatch(
      /selectionMode|algorithm|goal|cpSatTimeLimitSeconds|backtrackingTimeLimitMs|solverWorkers/,
    );
  });

  it('should prevent duplicate submissions while a request is running', async () => {
    const response = new Subject<FacultyExamScheduleResponse>();
    api.scheduleExams.mockReturnValue(response.asObservable());

    component.generateSchedule();
    component.generateSchedule();
    await fixture.whenStable();

    expect(api.scheduleExams).toHaveBeenCalledOnce();
    expect(component.isLoading()).toBe(true);
    expect(generateButton().disabled).toBe(true);

    response.next(scheduleResponse());
    response.complete();
    await fixture.whenStable();
    expect(component.isLoading()).toBe(false);
  });

  it('should render the scheduled count and actual exam interval from the response', async () => {
    component.generateSchedule();
    await fixture.whenStable();

    const text = fixture.nativeElement.textContent as string;
    const card = fixture.nativeElement.querySelector('[data-testid="exam-card"]');
    expect(text).toContain('1 / 2 ispita raspoređeno');
    expect(card.textContent).toContain('09:00–10:30');
    expect(card.textContent).not.toContain('09:00–12:00');
  });

  it('should show unscheduled exams in a separate section', async () => {
    component.generateSchedule();
    await fixture.whenStable();
    clickScheduleTab('NERASPOREĐENI 1');
    await fixture.whenStable();

    const table = fixture.nativeElement.querySelector('[data-testid="unscheduled-exams-table"]');
    expect(table).not.toBeNull();
    expect(table.textContent).toContain('OOP2');
    expect(table.textContent).toContain('Nije pronađena dozvoljena kombinacija.');
  });

  it('should show a user-friendly backend error', async () => {
    api.scheduleExams.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400 })));

    component.generateSchedule();
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Podaci nisu ispravni.',
    );
    expect(component.isLoading()).toBe(false);
  });

  it('should reject invalid period and daily-slot values before submission', () => {
    component.form.controls.startDate.setValue('2026-06-03');
    component.form.controls.endDate.setValue('2026-06-01');
    component.dailySlots.at(0).controls.endTime.setValue('08:00');

    component.generateSchedule();

    expect(component.form.invalid).toBe(true);
    expect(api.scheduleExams).not.toHaveBeenCalled();
  });

  it('should accept daily slots with different start times', () => {
    component.dailySlots.at(0).controls.startTime.setValue('09:00');
    component.dailySlots.at(1).controls.startTime.setValue('10:00');

    expect(component.dailySlots.hasError('duplicateStartTime')).toBe(false);
    expect(component.dailySlots.valid).toBe(true);
  });

  it('should reject daily slots with the same start time', async () => {
    component.dailySlots.at(1).controls.startTime.setValue('09:00');
    await fixture.whenStable();

    expect(component.dailySlots.hasError('duplicateStartTime')).toBe(true);
    expect(component.form.invalid).toBe(true);
    expect(generateButton().disabled).toBe(true);
  });

  it('should reject identical daily slots', () => {
    component.dailySlots.at(1).controls.startTime.setValue('09:00');
    component.dailySlots.at(1).controls.endTime.setValue('12:00');

    expect(component.dailySlots.hasError('duplicateStartTime')).toBe(true);
  });

  it('should not submit a schedule with duplicate daily-slot start times', () => {
    component.dailySlots.at(1).controls.startTime.setValue('09:00');

    component.generateSchedule();

    expect(api.scheduleExams).not.toHaveBeenCalled();
  });

  it('should explain duplicate daily-slot start times to the user', async () => {
    component.dailySlots.at(1).controls.startTime.setValue('09:00');

    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain(
      'Svaki dnevni termin mora imati jedinstveno početno vreme.',
    );
  });

  it('should add a daily slot with the first unused default start time', () => {
    const existingStartTimes = component.dailySlots.controls
      .map((slot) => slot.controls.startTime.value);

    component.addDailySlot();

    const addedSlot = component.dailySlots.at(component.dailySlots.length - 1);
    expect(addedSlot.controls.startTime.value).toBe('08:00');
    expect(addedSlot.controls.endTime.value).toBe('11:00');
    expect(existingStartTimes).not.toContain(addedSlot.controls.startTime.value);
    expect(component.dailySlots.hasError('duplicateStartTime')).toBe(false);
  });

  it('should reject required text fields that contain only whitespace', () => {
    component.form.controls.periodName.setValue('   ');

    component.generateSchedule();

    expect(component.form.controls.periodName.invalid).toBe(true);
    expect(api.scheduleExams).not.toHaveBeenCalled();
  });

  it('should not expose allocation algorithm controls in the faculty template', () => {
    const text = (fixture.nativeElement.textContent as string).toUpperCase();

    expect(text).not.toMatch(/CP-SAT|OR-TOOLS|GREEDY|BACKTRACKING|EXPLICIT|COMPARE|\bAUTO\b/);
    expect(fixture.debugElement.query(By.css('[data-testid="algorithm-select"]'))).toBeNull();
  });

  it('should render configuration and schedule workspaces together', () => {
    expect(fixture.nativeElement.querySelector('[data-testid="configuration-workspace"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="schedule-workspace"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="faculty-calendar"]')).not.toBeNull();
  });

  it('should expose all configuration tabs with current record counts', () => {
    const tabLabels = [...fixture.nativeElement.querySelectorAll('.configuration-tabs button')]
      .map((button: HTMLButtonElement) => button.getAttribute('aria-label'));

    expect(tabLabels).toEqual(['Rok', 'Ispiti: 3', 'Sale: 2', 'Dežurni: 3']);
  });

  it('should connect configuration tabs to their labelled tabpanels', async () => {
    const expected = [
      ['faculty-tab-period', 'faculty-panel-period'],
      ['faculty-tab-exams', 'faculty-panel-exams'],
      ['faculty-tab-rooms', 'faculty-panel-rooms'],
      ['faculty-tab-invigilators', 'faculty-panel-invigilators'],
    ];

    for (const [tabId, panelId] of expected) {
      const tab = fixture.nativeElement.querySelector(`#${tabId}`) as HTMLButtonElement;
      tab.click();
      await fixture.whenStable();

      const panel = fixture.nativeElement.querySelector(`#${panelId}`) as HTMLElement;
      expect(tab.getAttribute('aria-controls')).toBe(panelId);
      expect(tab.getAttribute('aria-selected')).toBe('true');
      expect(tab.getAttribute('tabindex')).toBe('0');
      expect(panel.getAttribute('aria-labelledby')).toBe(tabId);
    }
  });

  it('should navigate configuration tabs with arrow, Home, and End keys', async () => {
    const periodTab = fixture.nativeElement.querySelector('#faculty-tab-period') as HTMLButtonElement;
    periodTab.focus();

    periodTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await fixture.whenStable();
    expect(component.activeConfigurationSection()).toBe('EXAMS');
    expect((document.activeElement as HTMLElement).id).toBe('faculty-tab-exams');

    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'End', bubbles: true }),
    );
    await fixture.whenStable();
    expect(component.activeConfigurationSection()).toBe('INVIGILATORS');

    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Home', bubbles: true }),
    );
    await fixture.whenStable();
    expect(component.activeConfigurationSection()).toBe('PERIOD');

    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }),
    );
    await fixture.whenStable();
    expect(component.activeConfigurationSection()).toBe('INVIGILATORS');
    expect((document.activeElement as HTMLElement).id).toBe('faculty-tab-invigilators');
  });

  it('should preserve form state when switching configuration tabs', async () => {
    component.form.controls.periodName.setValue('Septembarski rok');
    component.selectConfigurationSection('EXAMS');
    await fixture.whenStable();
    component.selectConfigurationSection('PERIOD');
    await fixture.whenStable();

    expect(component.form.controls.periodName.value).toBe('Septembarski rok');
    expect((fixture.nativeElement.querySelector('[data-testid="period-name"]') as HTMLInputElement).value)
      .toBe('Septembarski rok');
  });

  it('should update tab counts when records are added', async () => {
    component.addExam();
    component.addRoom();
    component.addInvigilator();
    await fixture.whenStable();

    const text = fixture.nativeElement.querySelector('.configuration-tabs').textContent;
    expect(text).toContain('Ispiti4');
    expect(text).toContain('Sale3');
    expect(text).toContain('Dežurni4');
  });

  it('should keep the generate action inside the configuration panel', () => {
    const configuration = fixture.nativeElement.querySelector('[data-testid="configuration-workspace"]');
    expect(configuration.querySelector('[data-testid="generate-schedule"]')).not.toBeNull();
  });

  it('should retain a generated schedule and mark it stale after configuration changes', async () => {
    component.generateSchedule();
    await fixture.whenStable();

    component.form.controls.periodName.setValue('Izmenjeni rok');
    await fixture.whenStable();

    expect(component.result()).not.toBeNull();
    expect(component.resultStale()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Konfiguracija je izmenjena.');
    expect(fixture.nativeElement.querySelector('[data-testid="exam-card"]')).not.toBeNull();
  });

  function generateButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="generate-schedule"]');
  }

  function clickScheduleTab(label: string): void {
    const button = [...fixture.nativeElement.querySelectorAll('.view-tabs button')]
      .find((element: HTMLButtonElement) => element.textContent?.trim() === label) as HTMLButtonElement;
    button.click();
  }
});

function scheduleResponse(): FacultyExamScheduleResponse {
  return {
    assignments: [
      {
        examId: 'EXAM_1',
        examCode: 'MAT101',
        examName: 'Matematika 1',
        studentCount: 80,
        slotId: 'SLOT_20260601_D1',
        slotStart: '2026-06-01T09:00:00',
        slotEnd: '2026-06-01T12:00:00',
        actualEnd: '2026-06-01T10:30:00',
        room: { id: 'ROOM_1', name: 'Amfiteatar A', capacity: 120 },
        invigilators: [{ id: 'INV_1', name: 'Ana Petrović' }],
      },
    ],
    unscheduledExams: [
      {
        examId: 'EXAM_2',
        examCode: 'OOP2',
        examName: 'Objektno programiranje 2',
        studentCount: 45,
        reason: 'Nije pronađena dozvoljena kombinacija.',
      },
    ],
    statistics: {
      totalExams: 2,
      scheduledExams: 1,
      unscheduledExams: 1,
      solverStatus: 'OPTIMAL',
      executionTimeMs: 24,
      stoppedByLimit: false,
    },
  };
}
