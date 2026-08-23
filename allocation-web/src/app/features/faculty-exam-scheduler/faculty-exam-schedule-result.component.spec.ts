import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import {
  FacultyExamAssignmentDto,
  FacultyExamScheduleResponse,
  FacultyExamSlotDto,
} from '../../core/models/faculty-exam-schedule.models';
import { FacultyExamScheduleResultComponent } from './faculty-exam-schedule-result.component';

describe('FacultyExamScheduleResultComponent', () => {
  let fixture: ComponentFixture<FacultyExamScheduleResultComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FacultyExamScheduleResultComponent],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();

    fixture = TestBed.createComponent(FacultyExamScheduleResultComponent);
    fixture.componentRef.setInput('slots', calendarSlots());
    fixture.componentRef.setInput('periodName', 'Junski ispitni rok 2026');
    fixture.detectChanges();
  });

  it('should show the calendar structure before the first generated result', () => {
    expect(query('[data-testid="faculty-calendar"]')).not.toBeNull();
    expect(text()).toContain('Raspored još nije generisan.');
    expect(queryAll('[data-testid="calendar-week"]')).toHaveLength(2);
    expect(query('.calendar-cell[data-date="2026-06-08"]')).not.toBeNull();
    expect(text()).toContain('09:00');
    expect(text()).toContain('13:00');
  });

  it('should render empty days and empty calendar cells', () => {
    const emptyCell = query('.calendar-cell[data-date="2026-06-02"][data-time="09:00"]');

    expect(emptyCell).not.toBeNull();
    expect(emptyCell.textContent).toContain('—');
  });

  it('should present an OPTIMAL response as a generated schedule with summary counts', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();

    expect(fixture.componentInstance.hasUsableSchedule()).toBe(true);
    expect(text()).toContain('Raspored generisan');
    expect(text()).toContain('3 / 4 ispita raspoređeno');
    expect(query('[data-testid="schedule-summary-counts"]')).not.toBeNull();
  });

  it('should present a FEASIBLE response as a generated schedule without technical status data', async () => {
    const response = scheduleResponse();
    response.statistics.solverStatus = 'FEASIBLE';
    response.statistics.stoppedByLimit = true;
    setResult(response);
    await fixture.whenStable();

    const renderedText = text();
    expect(fixture.componentInstance.hasUsableSchedule()).toBe(true);
    expect(renderedText).toContain('Raspored generisan');
    expect(queryAll('[data-testid="exam-card"]')).toHaveLength(3);
    expect(query('[data-testid="schedule-summary-counts"]')).not.toBeNull();
    expect(renderedText).not.toMatch(/FEASIBLE|stoppedByLimit|solver|time limit/i);
  });

  it('should present an UNKNOWN response as a completed attempt without a usable schedule', async () => {
    setResult(nonSolutionResponse('UNKNOWN'));
    await fixture.whenStable();

    let renderedText = text();
    expect(fixture.componentInstance.hasUsableSchedule()).toBe(false);
    expect(renderedText).toContain('Raspored nije formiran');
    expect(renderedText).toContain('Nije bilo moguće formirati raspored u ovom pokušaju.');
    expect(renderedText).toContain('Pokušajte ponovo ili prilagodite konfiguraciju.');
    expect(renderedText).not.toContain('Raspored generisan');
    expect(renderedText).not.toContain('Raspored još nije generisan.');
    expect(query('[data-testid="schedule-summary-counts"]')).toBeNull();
    expect(query('[data-testid="no-usable-schedule-status"]')).not.toBeNull();
    expect(query('[data-testid="faculty-calendar"]')).not.toBeNull();

    clickTab('NERASPOREĐENI 4');
    await fixture.whenStable();

    renderedText = text();
    expect(query('[data-testid="unscheduled-exams-table"]')).not.toBeNull();
    expect(renderedText).toContain('Nije bilo moguće formirati izvodljiv raspored ispita.');
    expect(renderedText).not.toMatch(/UNKNOWN|solver|CP-SAT|stoppedByLimit/i);
  });

  it('should whitelist usable statuses instead of special-casing UNKNOWN', async () => {
    setResult(nonSolutionResponse('MODEL_INVALID'));
    await fixture.whenStable();

    expect(fixture.componentInstance.hasUsableSchedule()).toBe(false);
    expect(text()).toContain('Raspored nije formiran');
    expect(text()).not.toContain('Raspored generisan');
    expect(text()).not.toContain('MODEL_INVALID');
  });

  it('should show parallel exams in the same cell using actualEnd', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();

    const cell = query('.calendar-cell[data-date="2026-06-01"][data-time="09:00"]');
    expect(cell.querySelectorAll('[data-testid="exam-card"]')).toHaveLength(2);
    expect(cell.textContent).toContain('ALG');
    expect(cell.textContent).toContain('OOP2');
    expect(cell.textContent).toContain('09:00–10:30');
    expect(cell.textContent).not.toContain('09:00–12:00');
    expect(cell.textContent).toContain('Amfiteatar A');
  });

  it('should open keyboard-accessible exam details without technical solver data', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();

    const card = query('[data-testid="exam-card"]') as HTMLButtonElement;
    expect(card.tagName).toBe('BUTTON');
    card.click();
    await fixture.whenStable();

    const details = query('[data-testid="exam-details"]');
    expect(details.textContent).toContain('Kapacitet sale');
    expect(details.textContent).toContain('Broj studenata');
    expect(details.textContent).toContain('Ana Petrović');
    expect(details.textContent).not.toMatch(/OPTIMAL|CP-SAT|executionTimeMs|stoppedByLimit/);

    card.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(query('[data-testid="exam-details"]')).toBeNull();
    expect(document.activeElement).toBe(card);
  });

  it('should expose card expansion state and labelled non-modal exam details', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();

    const card = query('[data-testid="exam-card"]') as HTMLButtonElement;
    expect(card.getAttribute('aria-controls')).toBe('faculty-exam-details');
    expect(card.getAttribute('aria-expanded')).toBe('false');

    card.focus();
    card.click();
    await fixture.whenStable();

    const details = query('#faculty-exam-details');
    const heading = query('#faculty-exam-details-heading');
    const closeButton = query('[aria-label="Zatvori detalje ispita"]') as HTMLButtonElement;
    expect(card.getAttribute('aria-expanded')).toBe('true');
    expect(details.tagName).toBe('ASIDE');
    expect(details.getAttribute('role')).toBeNull();
    expect(details.getAttribute('aria-labelledby')).toBe(heading.id);
    expect(document.activeElement).toBe(closeButton);

    closeButton.click();
    await fixture.whenStable();

    expect(query('[data-testid="exam-details"]')).toBeNull();
    expect(card.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(card);
  });

  it('should render exam details beside the scrollable workspace body', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();

    const card = query('[data-testid="exam-card"]') as HTMLButtonElement;
    card.click();
    await fixture.whenStable();

    const content = query('.workspace-content');
    const body = query('.workspace-body');
    const details = query('[data-testid="exam-details"]');
    expect(content.contains(body)).toBe(true);
    expect(details.parentElement).toBe(content);
    expect(body.contains(details)).toBe(false);
  });

  it('should close exam details when switching result views', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();

    (query('[data-testid="exam-card"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(query('[data-testid="exam-details"]')).not.toBeNull();

    clickTab('DETALJAN PREGLED');
    await fixture.whenStable();

    expect(fixture.componentInstance.selectedAssignment()).toBeNull();
    expect(query('[data-testid="exam-details"]')).toBeNull();
  });

  it('should return to Calendar when a new result arrives', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();
    clickTab('NERASPOREĐENI 1');
    await fixture.whenStable();
    expect(fixture.componentInstance.activeView()).toBe('UNSCHEDULED');

    setResult({ ...scheduleResponse(), assignments: [...scheduleResponse().assignments] });
    await fixture.whenStable();

    expect(fixture.componentInstance.activeView()).toBe('CALENDAR');
    expect(query('#schedule-panel-calendar')).not.toBeNull();
  });

  it('should preserve the selected view when the result reference has not changed', async () => {
    const response = scheduleResponse();
    setResult(response);
    await fixture.whenStable();
    clickTab('NERASPOREĐENI 1');
    await fixture.whenStable();

    setResult(response);
    await fixture.whenStable();

    expect(fixture.componentInstance.activeView()).toBe('UNSCHEDULED');
  });

  it('should return to the empty Calendar when a result is cleared', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();
    clickTab('DETALJAN PREGLED');
    await fixture.whenStable();

    setResult(null);
    await fixture.whenStable();

    expect(fixture.componentInstance.activeView()).toBe('CALENDAR');
    expect(text()).toContain('Raspored još nije generisan.');
  });

  it('should close exam details when a new result arrives', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();
    (query('[data-testid="exam-card"]') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(fixture.componentInstance.selectedAssignment()).not.toBeNull();

    setResult({ ...scheduleResponse(), assignments: [...scheduleResponse().assignments] });
    await fixture.whenStable();

    expect(fixture.componentInstance.selectedAssignment()).toBeNull();
    expect(query('[data-testid="exam-details"]')).toBeNull();
  });

  it('should connect result tabs to their labelled tabpanels', async () => {
    const expected = [
      ['schedule-tab-calendar', 'schedule-panel-calendar'],
      ['schedule-tab-details', 'schedule-panel-details'],
      ['schedule-tab-unscheduled', 'schedule-panel-unscheduled'],
    ];

    for (const [tabId, panelId] of expected) {
      const tab = query(`#${tabId}`) as HTMLButtonElement;
      tab.click();
      await fixture.whenStable();

      const panel = query(`#${panelId}`);
      expect(tab.getAttribute('aria-controls')).toBe(panelId);
      expect(tab.getAttribute('aria-selected')).toBe('true');
      expect(tab.getAttribute('tabindex')).toBe('0');
      expect(panel.getAttribute('aria-labelledby')).toBe(tabId);
    }
  });

  it('should navigate result tabs with arrow, Home, and End keys', async () => {
    const calendarTab = query('#schedule-tab-calendar') as HTMLButtonElement;
    calendarTab.focus();

    calendarTab.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.activeView()).toBe('DETAILS');
    expect((document.activeElement as HTMLElement).id).toBe('schedule-tab-details');

    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'End', bubbles: true }),
    );
    await fixture.whenStable();
    expect(fixture.componentInstance.activeView()).toBe('UNSCHEDULED');

    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Home', bubbles: true }),
    );
    await fixture.whenStable();
    expect(fixture.componentInstance.activeView()).toBe('CALENDAR');

    (document.activeElement as HTMLElement).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }),
    );
    await fixture.whenStable();
    expect(fixture.componentInstance.activeView()).toBe('UNSCHEDULED');
    expect((document.activeElement as HTMLElement).id).toBe('schedule-tab-unscheduled');
  });

  it('should keep the detailed table chronologically sorted', async () => {
    setResult(scheduleResponse());
    clickTab('DETALJAN PREGLED');
    await fixture.whenStable();

    const table = query('[data-testid="scheduled-exams-table"]');
    const rows = [...table.querySelectorAll('tbody tr')].map((row) => row.textContent ?? '');
    expect(rows[0]).toContain('ALG');
    expect(rows[1]).toContain('OOP2');
    expect(rows[2]).toContain('MAT101');
    expect(rows[0]).toContain('09:00–10:30');
  });

  it('should show unscheduled exams in their dedicated view', async () => {
    setResult(scheduleResponse());
    await fixture.whenStable();
    clickTab('NERASPOREĐENI 1');
    await fixture.whenStable();

    const table = query('[data-testid="unscheduled-exams-table"]');
    expect(table.textContent).toContain('BP1');
    expect(table.textContent).toContain(
      'Nije pronađena dozvoljena kombinacija termina, sale i dežurnih u okviru zadatih ograničenja.',
    );
    expect(table.textContent).not.toContain('No permitted combination');
  });

  it.each([
    [
      'No permitted combination of slot, room, and invigilators was selected under the current constraints.',
      'Nije pronađena dozvoljena kombinacija termina, sale i dežurnih u okviru zadatih ograničenja.',
    ],
    [
      'The solver did not produce a feasible exam schedule.',
      'Nije bilo moguće formirati izvodljiv raspored ispita.',
    ],
  ])('should localize a known unscheduled reason', (reason, expected) => {
    expect(fixture.componentInstance.formatUnscheduledReason(reason)).toBe(expected);
    expect(expected.toLowerCase()).not.toContain('solver');
  });

  it('should preserve an unknown unscheduled reason', () => {
    const reason = 'A future domain-specific reason.';
    expect(fixture.componentInstance.formatUnscheduledReason(reason)).toBe(reason);
  });

  it('should render the no-feasible-schedule reason without solver terminology', async () => {
    const response = scheduleResponse();
    response.unscheduledExams[0].reason = 'The solver did not produce a feasible exam schedule.';
    setResult(response);
    await fixture.whenStable();
    clickTab('NERASPOREĐENI 1');
    await fixture.whenStable();

    const tableText = query('[data-testid="unscheduled-exams-table"]').textContent;
    expect(tableText).toContain('Nije bilo moguće formirati izvodljiv raspored ispita.');
    expect(tableText?.toLowerCase()).not.toContain('solver');
    expect(response.unscheduledExams[0].reason).toBe(
      'The solver did not produce a feasible exam schedule.',
    );
  });

  it('should show a stale-result notice without removing the calendar result', async () => {
    setResult(scheduleResponse());
    fixture.componentRef.setInput('stale', true);
    await fixture.whenStable();

    expect(text()).toContain('Konfiguracija je izmenjena.');
    expect(queryAll('[data-testid="exam-card"]')).toHaveLength(3);
  });

  function setResult(result: FacultyExamScheduleResponse | null): void {
    fixture.componentRef.setInput('result', result);
  }

  function clickTab(label: string): void {
    const button = [...fixture.nativeElement.querySelectorAll('.view-tabs button')]
      .find((element: HTMLButtonElement) => element.textContent?.trim() === label) as HTMLButtonElement;
    button.click();
  }

  function query(selector: string): HTMLElement {
    return fixture.nativeElement.querySelector(selector);
  }

  function queryAll(selector: string): HTMLElement[] {
    return [...fixture.nativeElement.querySelectorAll(selector)];
  }

  function text(): string {
    return fixture.nativeElement.textContent as string;
  }
});

function calendarSlots(): FacultyExamSlotDto[] {
  return [
    slot('S1', '2026-06-01', '09:00', '12:00'),
    slot('S2', '2026-06-01', '13:00', '16:00'),
    slot('S3', '2026-06-02', '09:00', '12:00'),
    slot('S4', '2026-06-02', '13:00', '16:00'),
    slot('S5', '2026-06-08', '09:00', '12:00'),
    slot('S6', '2026-06-08', '13:00', '16:00'),
  ];
}

function slot(id: string, date: string, start: string, end: string): FacultyExamSlotDto {
  return { id, start: `${date}T${start}:00`, end: `${date}T${end}:00` };
}

function scheduleResponse(): FacultyExamScheduleResponse {
  return {
    assignments: [
      assignment('MAT', 'MAT101', 'Matematika 1', '2026-06-08T09:00:00', 'Sala 203'),
      assignment('OOP', 'OOP2', 'Objektno programiranje 2', '2026-06-01T09:00:00', 'Sala 203'),
      assignment('ALG', 'ALG', 'Algoritmi', '2026-06-01T09:00:00', 'Amfiteatar A'),
    ],
    unscheduledExams: [{
      examId: 'BP',
      examCode: 'BP1',
      examName: 'Baze podataka',
      studentCount: 90,
      reason: 'No permitted combination of slot, room, and invigilators was selected under the current constraints.',
    }],
    statistics: {
      totalExams: 4,
      scheduledExams: 3,
      unscheduledExams: 1,
      solverStatus: 'OPTIMAL',
      executionTimeMs: 24,
      stoppedByLimit: false,
    },
  };
}

function nonSolutionResponse(solverStatus: string): FacultyExamScheduleResponse {
  const reason = 'The solver did not produce a feasible exam schedule.';
  return {
    assignments: [],
    unscheduledExams: [
      { examId: 'MAT', examCode: 'MAT101', examName: 'Matematika 1', studentCount: 60, reason },
      { examId: 'OOP', examCode: 'OOP2', examName: 'Objektno programiranje 2', studentCount: 60, reason },
      { examId: 'ALG', examCode: 'ALG', examName: 'Algoritmi', studentCount: 60, reason },
      { examId: 'BP', examCode: 'BP1', examName: 'Baze podataka', studentCount: 90, reason },
    ],
    statistics: {
      totalExams: 4,
      scheduledExams: 0,
      unscheduledExams: 4,
      solverStatus,
      executionTimeMs: 24,
      stoppedByLimit: true,
    },
  };
}

function assignment(
  examId: string,
  examCode: string,
  examName: string,
  slotStart: string,
  roomName: string,
): FacultyExamAssignmentDto {
  return {
    examId,
    examCode,
    examName,
    studentCount: 60,
    slotId: `SLOT_${examId}`,
    slotStart,
    slotEnd: `${slotStart.slice(0, 11)}12:00:00`,
    actualEnd: `${slotStart.slice(0, 11)}10:30:00`,
    room: { id: `ROOM_${examId}`, name: roomName, capacity: 120 },
    invigilators: [{ id: 'I1', name: 'Ana Petrović' }],
  };
}
