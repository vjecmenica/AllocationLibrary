import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
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
import { finalize } from 'rxjs';

import { FacultyExamScheduleApiService } from '../../core/api/faculty-exam-schedule-api.service';
import {
  FacultyExamDto,
  FacultyExamScheduleRequest,
  FacultyExamScheduleResponse,
  FacultyInvigilatorDto,
  FacultyRoomDto,
  FacultyTimeWindowDto,
} from '../../core/models/faculty-exam-schedule.models';
import { FacultyExamScheduleResultComponent } from './faculty-exam-schedule-result.component';
import {
  fullPeriodAvailability,
  generateExamSlots,
  isValidDailySlot,
  isValidDateRange,
  normalizeLocalDateTime,
  parseStudentGroups,
} from './faculty-schedule.utils';

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

const availabilityValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const start = control.get('start')?.value;
  const end = control.get('end')?.value;
  return typeof start === 'string' && typeof end === 'string' && start.length > 0 && start < end
    ? null
    : { timeRange: true };
};

const nonBlankValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { blank: true };

@Component({
  selector: 'app-faculty-exam-scheduler-page',
  imports: [ReactiveFormsModule, FacultyExamScheduleResultComponent],
  templateUrl: './faculty-exam-scheduler-page.component.html',
  styleUrl: './faculty-exam-scheduler-page.component.scss',
})
export class FacultyExamSchedulerPageComponent {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly api = inject(FacultyExamScheduleApiService);

  private nextDailySlotId = 4;
  private nextExamId = 4;
  private nextRoomId = 3;
  private nextInvigilatorId = 4;

  readonly isLoading = signal(false);
  readonly submitted = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly result = signal<FacultyExamScheduleResponse | null>(null);
  readonly resultPeriodName = signal('');

  readonly form = this.formBuilder.group(
    {
      periodName: this.formBuilder.control('Junski ispitni rok 2026', nonBlankValidator),
      startDate: this.formBuilder.control('2026-06-01', Validators.required),
      endDate: this.formBuilder.control('2026-06-02', Validators.required),
      dailySlots: this.formBuilder.array<DailySlotForm>([
        this.createDailySlotForm('D1', '09:00', '12:00'),
        this.createDailySlotForm('D2', '13:00', '16:00'),
        this.createDailySlotForm('D3', '17:00', '20:00'),
      ], Validators.minLength(1)),
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
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.errorMessage.set(null);
      this.result.set(null);
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

  addDailySlot(): void {
    this.dailySlots.push(this.createDailySlotForm(`D${this.nextDailySlotId++}`, '09:00', '12:00'));
  }

  removeDailySlot(index: number): void {
    this.dailySlots.removeAt(index);
    this.dailySlots.markAsTouched();
  }

  addExam(): void {
    this.exams.push(this.createExamForm(`EXAM_${this.nextExamId++}`, '', '', 1, 60, 0, ''));
  }

  removeExam(index: number): void {
    this.exams.removeAt(index);
    this.exams.markAsTouched();
  }

  addRoom(): void {
    this.rooms.push(this.createRoomForm(`ROOM_${this.nextRoomId++}`, '', 1, true));
  }

  removeRoom(index: number): void {
    this.rooms.removeAt(index);
    this.rooms.markAsTouched();
  }

  addInvigilator(): void {
    this.invigilators.push(
      this.createInvigilatorForm(`INV_${this.nextInvigilatorId++}`, '', true),
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

  showError(control: AbstractControl): boolean {
    return control.invalid && (control.touched || this.submitted());
  }

  generateSchedule(): void {
    if (this.isLoading()) {
      return;
    }

    this.submitted.set(true);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.createRequest();
    const periodName = this.form.controls.periodName.value.trim();
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.result.set(null);

    this.api.scheduleExams(request)
      .pipe(finalize(() => this.isLoading.set(false)))
      .subscribe({
        next: (response) => {
          this.resultPeriodName.set(periodName);
          this.result.set(response);
        },
        error: (error: unknown) => {
          this.errorMessage.set(this.userFriendlyError(error));
        },
      });
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
      studentCount: this.formBuilder.control(studentCount, [Validators.required, Validators.min(1)]),
      durationMinutes: this.formBuilder.control(durationMinutes, [Validators.required, Validators.min(1)]),
      requiredInvigilators: this.formBuilder.control(requiredInvigilators, [Validators.required, Validators.min(0)]),
      studentGroups: this.formBuilder.control(studentGroups),
    });
  }

  private createRoomForm(
    id: string,
    name: string,
    capacity: number,
    availableEntirePeriod: boolean,
  ): RoomForm {
    const availability = this.formBuilder.array<AvailabilityForm>([]);
    if (availableEntirePeriod) {
      availability.disable({ emitEvent: false });
    }

    return this.formBuilder.group({
      id: this.formBuilder.control(id),
      name: this.formBuilder.control(name, nonBlankValidator),
      capacity: this.formBuilder.control(capacity, [Validators.required, Validators.min(1)]),
      availableEntirePeriod: this.formBuilder.control(availableEntirePeriod),
      availability,
    });
  }

  private createInvigilatorForm(
    id: string,
    name: string,
    availableEntirePeriod: boolean,
  ): InvigilatorForm {
    const availability = this.formBuilder.array<AvailabilityForm>([]);
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
