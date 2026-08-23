import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { By } from '@angular/platform-browser';
import { of, Subject, throwError } from 'rxjs';

import demoFile from '../../../../public/demo/faculty-exam-schedule-demo.json';
import { FacultyExamScheduleApiService } from '../../core/api/faculty-exam-schedule-api.service';
import { DOWNLOAD_TEXT_FILE, TextFileDownloader } from '../../core/files/download-text-file';
import {
  FacultyExamScheduleRequest,
  FacultyExamScheduleResponse,
} from '../../core/models/faculty-exam-schedule.models';
import {
  FacultyScheduleConfiguration,
  parseFacultyScheduleJson,
  serializeFacultySchedule,
} from './faculty-schedule-json';
import { FacultyExamSchedulerPageComponent } from './faculty-exam-scheduler-page.component';

describe('FacultyExamSchedulerPageComponent', () => {
  let fixture: ComponentFixture<FacultyExamSchedulerPageComponent>;
  let component: FacultyExamSchedulerPageComponent;
  let api: { scheduleExams: ReturnType<typeof vi.fn> };
  let httpTesting: HttpTestingController;
  let downloadTextFile: ReturnType<typeof vi.fn<TextFileDownloader>>;

  beforeEach(async () => {
    api = { scheduleExams: vi.fn(() => of(scheduleResponse())) };
    downloadTextFile = vi.fn<TextFileDownloader>();

    await TestBed.configureTestingModule({
      imports: [FacultyExamSchedulerPageComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: FacultyExamScheduleApiService, useValue: api },
        { provide: DOWNLOAD_TEXT_FILE, useValue: downloadTextFile },
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(FacultyExamSchedulerPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
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

  it('should enforce Java Integer values for exam numeric controls', () => {
    const exam = component.exams.at(0);

    exam.controls.studentCount.setValue(80);
    expect(exam.controls.studentCount.valid).toBe(true);
    exam.controls.studentCount.setValue(80.5);
    expect(exam.controls.studentCount.invalid).toBe(true);
    exam.controls.studentCount.setValue(2147483648);
    expect(exam.controls.studentCount.invalid).toBe(true);

    exam.controls.durationMinutes.setValue(120.5);
    expect(exam.controls.durationMinutes.invalid).toBe(true);

    exam.controls.requiredInvigilators.setValue(1.5);
    expect(exam.controls.requiredInvigilators.invalid).toBe(true);
    exam.controls.requiredInvigilators.setValue(0);
    expect(exam.controls.requiredInvigilators.valid).toBe(true);
  });

  it('should enforce Java Integer values for room capacity', () => {
    const capacity = component.rooms.at(0).controls.capacity;

    capacity.setValue(100.5);
    expect(capacity.invalid).toBe(true);
    capacity.setValue(2147483648);
    expect(capacity.invalid).toBe(true);
    capacity.setValue(100);
    expect(capacity.valid).toBe(true);
  });

  it.each([80.5, 2147483648])(
    'should disable Generate and skip the API for invalid studentCount %s',
    async (studentCount) => {
      component.exams.at(0).controls.studentCount.setValue(studentCount);
      await fixture.whenStable();

      expect(component.form.invalid).toBe(true);
      expect(generateButton().disabled).toBe(true);

      component.generateSchedule();
      expect(api.scheduleExams).not.toHaveBeenCalled();
    },
  );

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

  it('should expose compact import, export, and demo actions', () => {
    expect(button('import-faculty-schedule').textContent?.trim()).toBe('UVEZI');
    expect(button('export-faculty-schedule').textContent?.trim()).toBe('IZVEZI');
    expect(button('load-faculty-demo').textContent?.trim()).toBe('UČITAJ DEMO');
  });

  it('should import a valid file and replace the complete form configuration', async () => {
    const schedule = importedSchedule();

    await importJson(serializeFacultySchedule(schedule));

    expect(component.form.controls.periodName.value).toBe(schedule.periodName);
    expect(component.form.controls.startDate.value).toBe(schedule.startDate);
    expect(component.form.controls.endDate.value).toBe(schedule.endDate);
    expect(component.dailySlots.getRawValue()).toEqual([
      { id: 'D1', startTime: '10:00', endTime: '12:00' },
      { id: 'D2', startTime: '14:00', endTime: '17:00' },
    ]);
    expect(component.exams).toHaveLength(1);
    expect(component.exams.at(0).controls.id.value).toBe('EXAM_4');
    expect(component.exams.at(0).controls.studentGroups.value).toBe('SI4, RTI4');
    expect(component.rooms).toHaveLength(1);
    expect(component.invigilators).toHaveLength(1);
    expect(component.form.pristine).toBe(true);
    expect(component.form.untouched).toBe(true);
  });

  it('should restore entire-period and custom availability values on import', async () => {
    await importJson(serializeFacultySchedule(importedSchedule()));

    const room = component.rooms.at(0);
    const invigilator = component.invigilators.at(0);
    expect(room.controls.availableEntirePeriod.value).toBe(false);
    expect(room.controls.availability.getRawValue()).toEqual([
      { start: '2026-07-01T08:00', end: '2026-07-02T18:00' },
    ]);
    expect(invigilator.controls.availableEntirePeriod.value).toBe(true);
    expect(invigilator.controls.availability.disabled).toBe(true);
  });

  it('should preserve the current form and show an error for an invalid file', async () => {
    const previousIds = component.exams.controls.map((exam) => exam.controls.id.value);

    await importJson('{not-json');

    expect(component.exams.controls.map((exam) => exam.controls.id.value)).toEqual(previousIds);
    expect(component.configurationStatus()).toEqual({
      type: 'error',
      message: 'Izabrani fajl nije validan JSON.',
    });
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Izabrani fajl nije validan JSON.',
    );
  });

  it('should reject an oversized file without changing the form', async () => {
    const file = scheduleFile(serializeFacultySchedule(importedSchedule()));
    Object.defineProperty(file, 'size', { configurable: true, value: 1024 * 1024 + 1 });

    await importFile(file);

    expect(component.exams).toHaveLength(3);
    expect(component.configurationStatus()?.message).toContain('Maksimalna veličina je 1 MB');
  });

  it('should clear an old result and refresh the empty calendar after bulk import', async () => {
    component.generateSchedule();
    await fixture.whenStable();
    component.form.controls.periodName.setValue('Stale value');
    expect(component.resultStale()).toBe(true);

    await importJson(serializeFacultySchedule(importedSchedule()));

    expect(component.result()).toBeNull();
    expect(component.resultStale()).toBe(false);
    expect(component.errorMessage()).toBeNull();
    expect(component.activeConfigurationSection()).toBe('PERIOD');
    expect(component.currentCalendarSlots()).toHaveLength(6);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="exam-card"]')).toHaveLength(0);
  });

  it('should avoid generated ID collisions after import', async () => {
    await importJson(serializeFacultySchedule(importedSchedule()));

    component.addDailySlot();
    component.addExam();
    component.addRoom();
    component.addInvigilator();

    expect(uniqueIds(component.dailySlots.controls.map((slot) => slot.controls.id.value))).toBe(true);
    expect(uniqueIds(component.exams.controls.map((exam) => exam.controls.id.value))).toBe(true);
    expect(uniqueIds(component.rooms.controls.map((room) => room.controls.id.value))).toBe(true);
    expect(uniqueIds(component.invigilators.controls.map((item) => item.controls.id.value))).toBe(true);
  });

  it('should export the current valid configuration through the shared download helper', () => {
    component.form.controls.periodName.setValue('Aktuelni rok');
    component.exams.at(0).controls.studentGroups.setValue('SI1, RTI1');
    component.exportFacultySchedule();

    expect(downloadTextFile).toHaveBeenCalledOnce();
    const [json, fileName, mimeType] = downloadTextFile.mock.calls[0];
    expect(fileName).toBe('faculty-exam-schedule.json');
    expect(mimeType).toBe('application/json;charset=utf-8');
    const parsed = parseFacultyScheduleJson(json);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.schedule.periodName).toBe('Aktuelni rok');
      expect(parsed.schedule.exams[0].studentGroups).toEqual(['SI1', 'RTI1']);
      expect(parsed.schedule.rooms[0].availableEntirePeriod).toBe(true);
    }
    expect(JSON.parse(json)).toMatchObject({ schemaVersion: 1 });
  });

  it('should preserve custom availability in an exported file', () => {
    const room = component.rooms.at(0);
    component.addAvailability(room);
    room.controls.availability.at(0).setValue({
      start: '2026-06-01T08:00',
      end: '2026-06-01T18:00',
    });

    component.exportFacultySchedule();

    const parsed = parseFacultyScheduleJson(downloadTextFile.mock.calls[0][0]);
    expect(parsed.success).toBe(true);
    if (parsed.success) {
      expect(parsed.schedule.rooms[0].availableEntirePeriod).toBe(false);
      expect(parsed.schedule.rooms[0].availability).toEqual([
        { start: '2026-06-01T08:00', end: '2026-06-01T18:00' },
      ]);
    }
  });

  it('should canonicalize disabled room availability and re-import its own export', () => {
    const room = component.rooms.at(0);
    component.addAvailability(room);
    expect(room.controls.availability.invalid).toBe(true);

    room.controls.availableEntirePeriod.setValue(true);
    component.setEntirePeriodAvailability(room, true);

    expect(component.form.valid).toBe(true);
    expect(room.controls.availability.getRawValue()).toEqual([{ start: '', end: '' }]);

    component.exportFacultySchedule();

    const json = downloadTextFile.mock.calls[0][0];
    const file = JSON.parse(json) as {
      schedule: { rooms: Array<{ availableEntirePeriod: boolean; availability: unknown[] }> };
    };
    expect(file.schedule.rooms[0]).toMatchObject({
      availableEntirePeriod: true,
      availability: [],
    });
    expect(parseFacultyScheduleJson(json).success).toBe(true);
  });

  it('should canonicalize disabled invigilator availability and re-import its own export', () => {
    const invigilator = component.invigilators.at(0);
    component.addAvailability(invigilator);
    expect(invigilator.controls.availability.invalid).toBe(true);

    invigilator.controls.availableEntirePeriod.setValue(true);
    component.setEntirePeriodAvailability(invigilator, true);

    expect(component.form.valid).toBe(true);
    expect(invigilator.controls.availability.getRawValue()).toEqual([{ start: '', end: '' }]);

    component.exportFacultySchedule();

    const json = downloadTextFile.mock.calls[0][0];
    const file = JSON.parse(json) as {
      schedule: {
        invigilators: Array<{ availableEntirePeriod: boolean; availability: unknown[] }>;
      };
    };
    expect(file.schedule.invigilators[0]).toMatchObject({
      availableEntirePeriod: true,
      availability: [],
    });
    expect(parseFacultyScheduleJson(json).success).toBe(true);
  });

  it('should validate equivalent local date-time precision in the reactive form', () => {
    const room = component.rooms.at(0);
    component.addAvailability(room);
    const window = room.controls.availability.at(0);

    window.setValue({
      start: '2026-06-15T09:00',
      end: '2026-06-15T09:00:00',
    });
    expect(window.invalid).toBe(true);

    window.setValue({
      start: '2026-06-15T09:00',
      end: '2026-06-15T09:00:30',
    });
    expect(window.valid).toBe(true);
  });

  it('should not enable persistent submit validation after a successful export', () => {
    expect(component.submitted()).toBe(false);

    component.exportFacultySchedule();
    component.addExam();

    expect(component.submitted()).toBe(false);
    expect(component.showError(component.exams.at(component.exams.length - 1).controls.code))
      .toBe(false);
  });

  it('should not export an invalid form', () => {
    component.form.controls.periodName.setValue('   ');

    component.exportFacultySchedule();

    expect(downloadTextFile).not.toHaveBeenCalled();
    expect(component.configurationStatus()?.message).toBe(
      'Najpre ispravite podatke u konfiguraciji.',
    );
    expect(component.submitted()).toBe(true);
  });

  it('should not export a decimal student count', () => {
    component.exams.at(0).controls.studentCount.setValue(12.5);

    component.exportFacultySchedule();

    expect(component.form.invalid).toBe(true);
    expect(downloadTextFile).not.toHaveBeenCalled();
  });

  it('should load the canonical bundled demo through the import path', async () => {
    const loading = component.loadDemoSchedule();
    const request = httpTesting.expectOne('/demo/faculty-exam-schedule-demo.json');
    expect(request.request.responseType).toBe('text');
    request.flush(JSON.stringify(demoFile));
    await loading;
    await fixture.whenStable();

    expect(component.exams).toHaveLength(24);
    expect(component.rooms).toHaveLength(6);
    expect(component.invigilators).toHaveLength(12);
    expect(component.dailySlots).toHaveLength(3);
    expect(component.currentCalendarSlots()).toHaveLength(21);
    expect(component.configurationStatus()).toEqual({
      type: 'success',
      message: 'Demo scenario je učitan.',
    });
  });

  it('should keep the current configuration when the bundled demo cannot be loaded', async () => {
    const loading = component.loadDemoSchedule();
    httpTesting.expectOne('/demo/faculty-exam-schedule-demo.json').flush('missing', {
      status: 404,
      statusText: 'Not Found',
    });
    await loading;
    await fixture.whenStable();

    expect(component.exams).toHaveLength(3);
    expect(component.configurationStatus()).toEqual({
      type: 'error',
      message: 'Scenario nije moguće učitati.',
    });
  });

  function generateButton(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('[data-testid="generate-schedule"]');
  }

  function clickScheduleTab(label: string): void {
    const button = [...fixture.nativeElement.querySelectorAll('.view-tabs button')]
      .find((element: HTMLButtonElement) => element.textContent?.trim() === label) as HTMLButtonElement;
    button.click();
  }

  function button(testId: string): HTMLButtonElement {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function fileInput(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input[type="file"]');
  }

  function scheduleFile(json: string): File {
    const file = new File([json], 'faculty.json', { type: 'application/json' });
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: vi.fn(() => Promise.resolve(json)),
    });
    return file;
  }

  async function importJson(json: string): Promise<void> {
    await importFile(scheduleFile(json));
  }

  async function importFile(file: File): Promise<void> {
    const input = fileInput();
    const files = {
      0: file,
      length: 1,
      item: (index: number) => (index === 0 ? file : null),
    } as unknown as FileList;
    Object.defineProperty(input, 'files', { configurable: true, value: files });
    Object.defineProperty(input, 'value', {
      configurable: true,
      writable: true,
      value: 'faculty.json',
    });
    await component.importFacultyScheduleFile({ currentTarget: input } as unknown as Event);
    await fixture.whenStable();
    expect(input.value).toBe('');
  }
});

function uniqueIds(ids: readonly string[]): boolean {
  return new Set(ids).size === ids.length;
}

function importedSchedule(): FacultyScheduleConfiguration {
  return {
    periodName: 'Uvezeni julski rok',
    startDate: '2026-07-01',
    endDate: '2026-07-03',
    dailySlots: [
      { startTime: '10:00', endTime: '12:00' },
      { startTime: '14:00', endTime: '17:00' },
    ],
    exams: [{
      id: 'EXAM_4',
      code: 'IMP',
      name: 'Uvezeni ispit',
      studentCount: 40,
      durationMinutes: 90,
      requiredInvigilators: 1,
      studentGroups: ['SI4', 'RTI4'],
    }],
    rooms: [{
      id: 'ROOM_3',
      name: 'Uvezena sala',
      capacity: 80,
      availableEntirePeriod: false,
      availability: [{ start: '2026-07-01T08:00', end: '2026-07-02T18:00' }],
    }],
    invigilators: [{
      id: 'INV_4',
      name: 'Uvezeni dežurni',
      availableEntirePeriod: true,
      availability: [],
    }],
  };
}

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
