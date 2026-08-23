import {
  afterNextRender,
  Component,
  ElementRef,
  HostListener,
  inject,
  Injector,
  Input,
  signal,
  viewChild,
} from '@angular/core';

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

const SCHEDULE_VIEWS: readonly ScheduleView[] = ['CALENDAR', 'DETAILS', 'UNSCHEDULED'];

@Component({
  selector: 'app-faculty-exam-schedule-result',
  templateUrl: './faculty-exam-schedule-result.component.html',
  styleUrl: './faculty-exam-schedule-result.component.scss',
})
export class FacultyExamScheduleResultComponent {
  private readonly injector = inject(Injector);
  private readonly detailsCloseButton = viewChild<ElementRef<HTMLButtonElement>>(
    'detailsCloseButton',
  );
  private slotsValue: FacultyExamSlotDto[] = [];
  private resultValue: FacultyExamScheduleResponse | null = null;
  private detailsTrigger: HTMLButtonElement | null = null;

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
    this.closeDetails(false);
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
    this.closeDetails(false);
  }

  onViewTabKeydown(event: KeyboardEvent, view: ScheduleView): void {
    const currentIndex = SCHEDULE_VIEWS.indexOf(view);
    let targetIndex: number;

    switch (event.key) {
      case 'ArrowLeft':
        targetIndex = (currentIndex - 1 + SCHEDULE_VIEWS.length) % SCHEDULE_VIEWS.length;
        break;
      case 'ArrowRight':
        targetIndex = (currentIndex + 1) % SCHEDULE_VIEWS.length;
        break;
      case 'Home':
        targetIndex = 0;
        break;
      case 'End':
        targetIndex = SCHEDULE_VIEWS.length - 1;
        break;
      default:
        return;
    }

    event.preventDefault();
    this.selectView(SCHEDULE_VIEWS[targetIndex]);
    const tabs = (event.currentTarget as HTMLElement).parentElement
      ?.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs?.[targetIndex]?.focus();
  }

  openDetails(assignment: FacultyExamAssignmentDto, trigger: HTMLButtonElement): void {
    this.detailsTrigger = trigger;
    this.selectedAssignment.set(assignment);
    afterNextRender(() => this.detailsCloseButton()?.nativeElement.focus(), {
      injector: this.injector,
    });
  }

  closeDetails(restoreFocus = true): void {
    const trigger = this.detailsTrigger;
    this.selectedAssignment.set(null);
    this.detailsTrigger = null;

    if (restoreFocus && trigger?.isConnected) {
      afterNextRender(() => trigger.focus(), { injector: this.injector });
    }
  }

  @HostListener('keydown.escape', ['$event'])
  closeDetailsWithEscape(event: Event): void {
    if (this.selectedAssignment()) {
      event.preventDefault();
      this.closeDetails();
    }
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
