import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { finalize, firstValueFrom } from 'rxjs';

import { FacultyExamScheduleApiService } from '../../core/api/faculty-exam-schedule-api.service';
import { DOWNLOAD_TEXT_FILE } from '../../core/files/download-text-file';
import {
  FacultyExamDto,
  FacultyExamSlotDto,
  FacultyExamScheduleRequest,
  FacultyExamScheduleResponse,
  FacultyInvigilatorDto,
  FacultyRoomDto,
  FacultyTimeWindowDto,
} from '../../core/models/faculty-exam-schedule.models';
import { FacultyExamScheduleResultComponent } from './faculty-exam-schedule-result.component';
import {
  FacultyScheduleConfiguration,
  parseFacultyScheduleJson,
  serializeFacultySchedule,
} from './faculty-schedule-json';
import {
  fullPeriodAvailability,
  generateExamSlots,
  isJavaInteger,
  isValidDailySlot,
  isValidDateRange,
  isValidLocalDateTimeRange,
  normalizeLocalDateTime,
  parseStudentGroups,
} from './faculty-schedule.utils';

type ConfigurationSection = 'PERIOD' | 'EXAMS' | 'ROOMS' | 'INVIGILATORS';
type ConfigurationStatus = { type: 'success' | 'error'; message: string };

const MAX_FACULTY_FILE_SIZE_BYTES = 1024 * 1024;
const FACULTY_DEMO_URL = '/demo/faculty-exam-schedule-demo.json';

const CONFIGURATION_SECTIONS: readonly ConfigurationSection[] = [
  'PERIOD',
  'EXAMS',
  'ROOMS',
  'INVIGILATORS',
];

type DailySlotForm = FormGroup<{
  id: FormControl<string>;
  startTime: FormControl<string>;
  endTime: FormControl<string>;
}>;

type AvailabilityForm = FormGroup<{
  start: FormControl<string>;
  end: FormControl<string>;
}>;

type ExamForm = FormGroup<{
  id: FormControl<string>;
  code: FormControl<string>;
  name: FormControl<string>;
  studentCount: FormControl<number>;
  durationMinutes: FormControl<number>;
  requiredInvigilators: FormControl<number>;
  studentGroups: FormControl<string>;
}>;

type RoomForm = FormGroup<{
  id: FormControl<string>;
  name: FormControl<string>;
  capacity: FormControl<number>;
  availableEntirePeriod: FormControl<boolean>;
  availability: FormArray<AvailabilityForm>;
}>;

type InvigilatorForm = FormGroup<{
  id: FormControl<string>;
  name: FormControl<string>;
  availableEntirePeriod: FormControl<boolean>;
  availability: FormArray<AvailabilityForm>;
}>;

const dateRangeValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const start = control.get('startDate')?.value;
  const end = control.get('endDate')?.value;
  return typeof start === 'string' && typeof end === 'string' && isValidDateRange(start, end)
    ? null
    : { dateRange: true };
};

const dailySlotValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const start = control.get('startTime')?.value;
  const end = control.get('endTime')?.value;
  return typeof start === 'string' && typeof end === 'string' && isValidDailySlot(start, end)
    ? null
    : { timeRange: true };
};

const uniqueDailySlotStartValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const startTimes = (control as FormArray<DailySlotForm>).controls
    .map((slot) => slot.controls.startTime.value.trim())
    .filter((startTime) => startTime.length > 0);

  return new Set(startTimes).size === startTimes.length
    ? null
    : { duplicateStartTime: true };
};

const availabilityValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const start = control.get('start')?.value;
  const end = control.get('end')?.value;
  return typeof start === 'string' && typeof end === 'string' &&
    isValidLocalDateTimeRange(start, end)
    ? null
    : { timeRange: true };
};

const nonBlankValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { blank: true };

function javaIntegerValidator(minimum: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    isJavaInteger(control.value) && control.value >= minimum
      ? null
      : { integerRange: true };
}

@Component({
  selector: 'app-faculty-exam-scheduler-page',
  imports: [ReactiveFormsModule, RouterLink, FacultyExamScheduleResultComponent],
  templateUrl: './faculty-exam-scheduler-page.component.html',
  styleUrl: './faculty-exam-scheduler-page.component.scss',
})
export class FacultyExamSchedulerPageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(FacultyExamScheduleApiService);
  private readonly http = inject(HttpClient);
  private readonly downloadTextFile = inject(DOWNLOAD_TEXT_FILE);

  private formRevision = 0;

  readonly isLoading = signal(false);
  readonly isConfigurationLoading = signal(false);
  readonly submitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly configurationStatus = signal<ConfigurationStatus | null>(null);
  readonly result = signal<FacultyExamScheduleResponse | null>(null);
  readonly resultPeriodName = signal('');
  readonly activeConfigurationSection = signal<ConfigurationSection>('PERIOD');
  readonly currentCalendarSlots = signal<FacultyExamSlotDto[]>([]);
  readonly resultCalendarSlots = signal<FacultyExamSlotDto[]>([]);
  readonly resultStale = signal(false);
  readonly displayedCalendarSlots = computed(() =>
    this.result() ? this.resultCalendarSlots() : this.currentCalendarSlots(),
  );
  readonly fileActionsDisabled = computed(() =>
    this.isLoading() || this.isConfigurationLoading(),
  );

  readonly form = this.formBuilder.group(
    {
      periodName: this.formBuilder.control('Junski ispitni rok 2026', nonBlankValidator),
      startDate: this.formBuilder.control('2026-06-01', Validators.required),
      endDate: this.formBuilder.control('2026-06-02', Validators.required),
      dailySlots: this.formBuilder.array<DailySlotForm>([
        this.createDailySlotForm('D1', '09:00', '12:00'),
        this.createDailySlotForm('D2', '13:00', '16:00'),
        this.createDailySlotForm('D3', '17:00', '20:00'),
      ], [Validators.minLength(1), uniqueDailySlotStartValidator]),
      exams: this.formBuilder.array<ExamForm>([
        this.createExamForm('EXAM_1', 'MAT101', 'Matematika 1', 80, 120, 2, 'SI1, RTI1'),
        this.createExamForm('EXAM_2', 'OOP2', 'Objektno programiranje 2', 45, 120, 1, 'SI2'),
        this.createExamForm('EXAM_3', 'ALG', 'Algoritmi i strukture podataka', 60, 90, 1, 'SI2'),
      ], Validators.minLength(1)),
      rooms: this.formBuilder.array<RoomForm>([
        this.createRoomForm('ROOM_1', 'Amfiteatar A', 120, true),
        this.createRoomForm('ROOM_2', 'Sala 203', 60, true),
      ], Validators.minLength(1)),
      invigilators: this.formBuilder.array<InvigilatorForm>([
        this.createInvigilatorForm('INV_1', 'Ana Petrović', true),
        this.createInvigilatorForm('INV_2', 'Marko Jovanović', true),
        this.createInvigilatorForm('INV_3', 'Jelena Ilić', true),
      ]),
    },
    { validators: dateRangeValidator },
  );

  constructor() {
    this.refreshCalendarSlots();
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.formRevision++;
      this.errorMessage.set(null);
      this.configurationStatus.set(null);
      this.refreshCalendarSlots();
      if (this.result()) {
        this.resultStale.set(true);
      }
    });
  }

  get dailySlots(): FormArray<DailySlotForm> {
    return this.form.controls.dailySlots;
  }

  get exams(): FormArray<ExamForm> {
    return this.form.controls.exams;
  }

  get rooms(): FormArray<RoomForm> {
    return this.form.controls.rooms;
  }

  get invigilators(): FormArray<InvigilatorForm> {
    return this.form.controls.invigilators;
  }

  selectConfigurationSection(section: ConfigurationSection): void {
    this.activeConfigurationSection.set(section);
  }

  onConfigurationTabKeydown(event: KeyboardEvent, section: ConfigurationSection): void {
    const currentIndex = CONFIGURATION_SECTIONS.indexOf(section);
    let targetIndex: number;

    switch (event.key) {
      case 'ArrowLeft':
        targetIndex = (currentIndex - 1 + CONFIGURATION_SECTIONS.length)
          % CONFIGURATION_SECTIONS.length;
        break;
      case 'ArrowRight':
        targetIndex = (currentIndex + 1) % CONFIGURATION_SECTIONS.length;
        break;
      case 'Home':
        targetIndex = 0;
        break;
      case 'End':
        targetIndex = CONFIGURATION_SECTIONS.length - 1;
        break;
      default:
        return;
    }

    event.preventDefault();
    this.selectConfigurationSection(CONFIGURATION_SECTIONS[targetIndex]);
    const tabs = (event.currentTarget as HTMLElement).parentElement
      ?.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs?.[targetIndex]?.focus();
  }

  addDailySlot(): void {
    const { startTime, endTime } = this.nextDailySlotDefaults();
    this.dailySlots.push(
      this.createDailySlotForm(this.nextAvailableId('D', '', this.dailySlots), startTime, endTime),
    );
  }

  removeDailySlot(index: number): void {
    this.dailySlots.removeAt(index);
    this.dailySlots.markAsTouched();
  }

  addExam(): void {
    this.exams.push(
      this.createExamForm(this.nextAvailableId('EXAM', '_', this.exams), '', '', 1, 60, 0, ''),
    );
  }

  removeExam(index: number): void {
    this.exams.removeAt(index);
    this.exams.markAsTouched();
  }

  addRoom(): void {
    this.rooms.push(
      this.createRoomForm(this.nextAvailableId('ROOM', '_', this.rooms), '', 1, true),
    );
  }

  removeRoom(index: number): void {
    this.rooms.removeAt(index);
    this.rooms.markAsTouched();
  }

  addInvigilator(): void {
    this.invigilators.push(
      this.createInvigilatorForm(
        this.nextAvailableId('INV', '_', this.invigilators),
        '',
        true,
      ),
    );
  }

  removeInvigilator(index: number): void {
    this.invigilators.removeAt(index);
  }

  addAvailability(resource: RoomForm | InvigilatorForm): void {
    resource.controls.availableEntirePeriod.setValue(false);
    resource.controls.availability.enable({ emitEvent: false });
    resource.controls.availability.push(this.createAvailabilityForm('', ''));
  }

  removeAvailability(resource: RoomForm | InvigilatorForm, index: number): void {
    resource.controls.availability.removeAt(index);
  }

  setEntirePeriodAvailability(resource: RoomForm | InvigilatorForm, enabled: boolean): void {
    if (enabled) {
      resource.controls.availability.disable({ emitEvent: false });
    } else {
      resource.controls.availability.enable({ emitEvent: false });
    }
    resource.updateValueAndValidity();
  }

  async importFacultyScheduleFile(event: Event): Promise<void> {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.item(0);
    this.configurationStatus.set(null);

    if (!file) {
      return;
    }

    this.isConfigurationLoading.set(true);
    try {
      if (file.size > MAX_FACULTY_FILE_SIZE_BYTES) {
        this.configurationStatus.set({
          type: 'error',
          message: 'Izabrani fajl je prevelik. Maksimalna veličina je 1 MB.',
        });
        return;
      }
      this.importScheduleText(await file.text(), 'Podaci su uspešno uvezeni.');
    } catch {
      this.configurationStatus.set({
        type: 'error',
        message: 'Scenario nije moguće učitati.',
      });
    } finally {
      this.isConfigurationLoading.set(false);
      input.value = '';
    }
  }

  exportFacultySchedule(): void {
    this.configurationStatus.set(null);
    if (this.form.invalid) {
      this.submitted.set(true);
      this.form.markAllAsTouched();
      this.configurationStatus.set({
        type: 'error',
        message: 'Najpre ispravite podatke u konfiguraciji.',
      });
      return;
    }

    this.downloadTextFile(
      serializeFacultySchedule(this.currentScheduleConfiguration()),
      'faculty-exam-schedule.json',
      'application/json;charset=utf-8',
    );
    this.configurationStatus.set({ type: 'success', message: 'Podaci su izvezeni.' });
  }

  async loadDemoSchedule(): Promise<void> {
    if (this.fileActionsDisabled()) {
      return;
    }

    this.configurationStatus.set(null);
    this.isConfigurationLoading.set(true);
    try {
      const json = await firstValueFrom(
        this.http.get(FACULTY_DEMO_URL, { responseType: 'text' }),
      );
      if (!this.importScheduleText(json, 'Demo scenario je učitan.')) {
        this.configurationStatus.set({
          type: 'error',
          message: 'Scenario nije moguće učitati.',
        });
      }
    } catch {
      this.configurationStatus.set({
        type: 'error',
        message: 'Scenario nije moguće učitati.',
      });
    } finally {
      this.isConfigurationLoading.set(false);
    }
  }

  showError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.submitted());
  }

  generateSchedule(): void {
    if (this.isLoading() || this.isConfigurationLoading()) {
      return;
    }

    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.createRequest();
    const periodName = this.form.controls.periodName.value.trim();
    const requestRevision = this.formRevision;
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.api.scheduleExams(request)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (response) => {
          this.resultPeriodName.set(periodName);
          this.resultCalendarSlots.set(request.slots.map((slot) => ({ ...slot })));
          this.result.set(response);
          this.resultStale.set(this.formRevision !== requestRevision);
        },
        error: (error: unknown) => {
          this.errorMessage.set(this.userFriendlyError(error));
        },
      });
  }

  private importScheduleText(json: string, successMessage: string): boolean {
    const parsed = parseFacultyScheduleJson(json);
    if (!parsed.success) {
      this.configurationStatus.set({ type: 'error', message: parsed.message });
      return false;
    }
    if (!this.isValidScheduleCandidate(parsed.schedule)) {
      this.configurationStatus.set({
        type: 'error',
        message: 'Uvezeni scenario sadrži neispravne podatke.',
      });
      return false;
    }

    this.replaceScheduleConfiguration(parsed.schedule);
    this.configurationStatus.set({ type: 'success', message: successMessage });
    return true;
  }

  private isValidScheduleCandidate(schedule: FacultyScheduleConfiguration): boolean {
    const candidate = this.formBuilder.group(
      {
        periodName: this.formBuilder.control(schedule.periodName, nonBlankValidator),
        startDate: this.formBuilder.control(schedule.startDate, Validators.required),
        endDate: this.formBuilder.control(schedule.endDate, Validators.required),
        dailySlots: this.formBuilder.array<DailySlotForm>(
          schedule.dailySlots.map((slot, index) =>
            this.createDailySlotForm(`D${index + 1}`, slot.startTime, slot.endTime),
          ),
          [Validators.minLength(1), uniqueDailySlotStartValidator],
        ),
        exams: this.formBuilder.array<ExamForm>(
          schedule.exams.map((exam) => this.createExamForm(
            exam.id,
            exam.code,
            exam.name,
            exam.studentCount,
            exam.durationMinutes,
            exam.requiredInvigilators,
            exam.studentGroups.join(', '),
          )),
          Validators.minLength(1),
        ),
        rooms: this.formBuilder.array<RoomForm>(
          schedule.rooms.map((room) => this.createRoomForm(
            room.id,
            room.name,
            room.capacity,
            room.availableEntirePeriod,
            room.availability,
          )),
          Validators.minLength(1),
        ),
        invigilators: this.formBuilder.array<InvigilatorForm>(
          schedule.invigilators.map((invigilator) => this.createInvigilatorForm(
            invigilator.id,
            invigilator.name,
            invigilator.availableEntirePeriod,
            invigilator.availability,
          )),
        ),
      },
      { validators: dateRangeValidator },
    );
    return candidate.valid;
  }

  private replaceScheduleConfiguration(schedule: FacultyScheduleConfiguration): void {
    this.form.controls.periodName.setValue(schedule.periodName, { emitEvent: false });
    this.form.controls.startDate.setValue(schedule.startDate, { emitEvent: false });
    this.form.controls.endDate.setValue(schedule.endDate, { emitEvent: false });

    this.dailySlots.clear({ emitEvent: false });
    schedule.dailySlots.forEach((slot, index) => this.dailySlots.push(
      this.createDailySlotForm(`D${index + 1}`, slot.startTime, slot.endTime),
      { emitEvent: false },
    ));

    this.exams.clear({ emitEvent: false });
    schedule.exams.forEach((exam) => this.exams.push(this.createExamForm(
      exam.id,
      exam.code,
      exam.name,
      exam.studentCount,
      exam.durationMinutes,
      exam.requiredInvigilators,
      exam.studentGroups.join(', '),
    ), { emitEvent: false }));

    this.rooms.clear({ emitEvent: false });
    schedule.rooms.forEach((room) => this.rooms.push(this.createRoomForm(
      room.id,
      room.name,
      room.capacity,
      room.availableEntirePeriod,
      room.availability,
    ), { emitEvent: false }));

    this.invigilators.clear({ emitEvent: false });
    schedule.invigilators.forEach((invigilator) => this.invigilators.push(
      this.createInvigilatorForm(
        invigilator.id,
        invigilator.name,
        invigilator.availableEntirePeriod,
        invigilator.availability,
      ),
      { emitEvent: false },
    ));

    this.form.updateValueAndValidity({ emitEvent: false });
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.formRevision++;
    this.submitted.set(false);
    this.errorMessage.set(null);
    this.result.set(null);
    this.resultPeriodName.set('');
    this.resultCalendarSlots.set([]);
    this.resultStale.set(false);
    this.activeConfigurationSection.set('PERIOD');
    this.refreshCalendarSlots();
  }

  private currentScheduleConfiguration(): FacultyScheduleConfiguration {
    const value = this.form.getRawValue();
    return {
      periodName: value.periodName.trim(),
      startDate: value.startDate,
      endDate: value.endDate,
      dailySlots: value.dailySlots.map((slot) => ({
        startTime: slot.startTime,
        endTime: slot.endTime,
      })),
      exams: value.exams.map((exam) => ({
        id: exam.id,
        code: exam.code.trim(),
        name: exam.name.trim(),
        studentCount: exam.studentCount,
        durationMinutes: exam.durationMinutes,
        requiredInvigilators: exam.requiredInvigilators,
        studentGroups: parseStudentGroups(exam.studentGroups),
      })),
      rooms: value.rooms.map((room) => ({
        id: room.id,
        name: room.name.trim(),
        capacity: room.capacity,
        availableEntirePeriod: room.availableEntirePeriod,
        availability: room.availableEntirePeriod
          ? []
          : room.availability.map((window) => ({ ...window })),
      })),
      invigilators: value.invigilators.map((invigilator) => ({
        id: invigilator.id,
        name: invigilator.name.trim(),
        availableEntirePeriod: invigilator.availableEntirePeriod,
        availability: invigilator.availableEntirePeriod
          ? []
          : invigilator.availability.map((window) => ({ ...window })),
      })),
    };
  }

  private createRequest(): FacultyExamScheduleRequest {
    const value = this.form.getRawValue();
    const fullAvailability = fullPeriodAvailability(value.startDate, value.endDate);

    return {
      slots: generateExamSlots(value.startDate, value.endDate, value.dailySlots),
      exams: value.exams.map((exam): FacultyExamDto => ({
        id: exam.id,
        code: exam.code.trim(),
        name: exam.name.trim(),
        studentCount: exam.studentCount,
        durationMinutes: exam.durationMinutes,
        requiredInvigilators: exam.requiredInvigilators,
        studentGroups: parseStudentGroups(exam.studentGroups),
      })),
      rooms: value.rooms.map((room, index): FacultyRoomDto => ({
        id: room.id,
        name: room.name.trim(),
        capacity: room.capacity,
        availability: this.resourceAvailability(this.rooms.at(index), fullAvailability),
      })),
      invigilators: value.invigilators.map((invigilator, index): FacultyInvigilatorDto => ({
        id: invigilator.id,
        name: invigilator.name.trim(),
        availability: this.resourceAvailability(this.invigilators.at(index), fullAvailability),
      })),
    };
  }

  private refreshCalendarSlots(): void {
    const value = this.form.getRawValue();
    const validDailySlots = value.dailySlots.filter((slot) =>
      isValidDailySlot(slot.startTime, slot.endTime),
    );
    if (!isValidDateRange(value.startDate, value.endDate) || validDailySlots.length === 0) {
      this.currentCalendarSlots.set([]);
      return;
    }
    this.currentCalendarSlots.set(generateExamSlots(value.startDate, value.endDate, validDailySlots));
  }

  private resourceAvailability(
    resource: RoomForm | InvigilatorForm,
    fullAvailability: FacultyTimeWindowDto,
  ): FacultyTimeWindowDto[] {
    if (resource.controls.availableEntirePeriod.value) {
      return [{ ...fullAvailability }];
    }

    return resource.controls.availability.getRawValue().map((window) => ({
      start: normalizeLocalDateTime(window.start),
      end: normalizeLocalDateTime(window.end),
    }));
  }

  private createDailySlotForm(id: string, startTime: string, endTime: string): DailySlotForm {
    return this.formBuilder.group(
      {
        id: this.formBuilder.control(id),
        startTime: this.formBuilder.control(startTime, Validators.required),
        endTime: this.formBuilder.control(endTime, Validators.required),
      },
      { validators: dailySlotValidator },
    );
  }

  private nextDailySlotDefaults(): { startTime: string; endTime: string } {
    const usedStartTimes = new Set(
      this.dailySlots.controls.map((slot) => slot.controls.startTime.value),
    );

    for (let hour = 8; hour <= 20; hour++) {
      const startTime = `${hour.toString().padStart(2, '0')}:00`;
      if (!usedStartTimes.has(startTime)) {
        const endTime = `${Math.min(hour + 3, 23).toString().padStart(2, '0')}:00`;
        return { startTime, endTime };
      }
    }

    return { startTime: '', endTime: '' };
  }

  private nextAvailableId(
    prefix: string,
    separator: string,
    forms: { controls: ReadonlyArray<{ controls: { id: FormControl<string> } }> },
  ): string {
    const usedIds = new Set(forms.controls.map((form) => form.controls.id.value));
    let index = 1;
    while (usedIds.has(`${prefix}${separator}${index}`)) {
      index++;
    }
    return `${prefix}${separator}${index}`;
  }

  private createExamForm(
    id: string,
    code: string,
    name: string,
    studentCount: number,
    durationMinutes: number,
    requiredInvigilators: number,
    studentGroups: string,
  ): ExamForm {
    return this.formBuilder.group({
      id: this.formBuilder.control(id),
      code: this.formBuilder.control(code, nonBlankValidator),
      name: this.formBuilder.control(name, nonBlankValidator),
      studentCount: this.formBuilder.control(studentCount, javaIntegerValidator(1)),
      durationMinutes: this.formBuilder.control(durationMinutes, javaIntegerValidator(1)),
      requiredInvigilators: this.formBuilder.control(
        requiredInvigilators,
        javaIntegerValidator(0),
      ),
      studentGroups: this.formBuilder.control(studentGroups),
    });
  }

  private createRoomForm(
    id: string,
    name: string,
    capacity: number,
    availableEntirePeriod: boolean,
    windows: readonly FacultyTimeWindowDto[] = [],
  ): RoomForm {
    const availability = this.formBuilder.array<AvailabilityForm>(
      windows.map((window) => this.createAvailabilityForm(window.start, window.end)),
    );
    if (availableEntirePeriod) {
      availability.disable({ emitEvent: false });
    }

    return this.formBuilder.group({
      id: this.formBuilder.control(id),
      name: this.formBuilder.control(name, nonBlankValidator),
      capacity: this.formBuilder.control(capacity, javaIntegerValidator(1)),
      availableEntirePeriod: this.formBuilder.control(availableEntirePeriod),
      availability,
    });
  }

  private createInvigilatorForm(
    id: string,
    name: string,
    availableEntirePeriod: boolean,
    windows: readonly FacultyTimeWindowDto[] = [],
  ): InvigilatorForm {
    const availability = this.formBuilder.array<AvailabilityForm>(
      windows.map((window) => this.createAvailabilityForm(window.start, window.end)),
    );
    if (availableEntirePeriod) {
      availability.disable({ emitEvent: false });
    }

    return this.formBuilder.group({
      id: this.formBuilder.control(id),
      name: this.formBuilder.control(name, nonBlankValidator),
      availableEntirePeriod: this.formBuilder.control(availableEntirePeriod),
      availability,
    });
  }

  private createAvailabilityForm(start: string, end: string): AvailabilityForm {
    return this.formBuilder.group(
      {
        start: this.formBuilder.control(start, Validators.required),
        end: this.formBuilder.control(end, Validators.required),
      },
      { validators: availabilityValidator },
    );
  }

  private userFriendlyError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 400) {
      return 'Podaci nisu ispravni. Proverite unete vrednosti i pokušajte ponovo.';
    }
    return 'Raspored trenutno nije moguće generisati. Proverite vezu sa servisom i pokušajte ponovo.';
  }
}
