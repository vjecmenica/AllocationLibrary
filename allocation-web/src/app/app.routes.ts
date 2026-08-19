import { Routes } from '@angular/router';

import { AllocationPageComponent } from './features/allocation/allocation-page.component';
import { FacultyExamSchedulerPageComponent } from './features/faculty-exam-scheduler/faculty-exam-scheduler-page.component';

export const routes: Routes = [
  {
    path: '',
    component: FacultyExamSchedulerPageComponent,
  },
  {
    path: 'analysis',
    component: AllocationPageComponent,
  },
  {
    path: '**',
    redirectTo: '',
  },
];
