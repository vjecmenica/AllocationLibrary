import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AllocationApiRequest,
  AllocationApiResponse,
  AllocationComparisonApiRequest,
  AllocationComparisonApiResponse,
  HealthResponse,
} from '../models/allocation-api.models';

@Injectable({
  providedIn: 'root',
})
export class AllocationApiService {
  private readonly http = inject(HttpClient);

  getHealth(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>('/api/health');
  }

  executeAllocation(request: AllocationApiRequest): Observable<AllocationApiResponse> {
    return this.http.post<AllocationApiResponse>('/api/allocations', request);
  }

  compareAllocations(request: AllocationComparisonApiRequest): Observable<AllocationComparisonApiResponse> {
    return this.http.post<AllocationComparisonApiResponse>('/api/allocations/compare', request);
  }
}
