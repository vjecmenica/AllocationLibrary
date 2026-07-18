import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AllocationApiService } from './allocation-api.service';
import {
  AllocationApiRequest,
  AllocationComparisonApiRequest,
} from '../models/allocation-api.models';

describe('AllocationApiService', () => {
  let service: AllocationApiService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(AllocationApiService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should send health request to /api/health', () => {
    service.getHealth().subscribe((response) => {
      expect(response.status).toBe('UP');
    });

    const request = httpTestingController.expectOne('/api/health');
    expect(request.request.method).toBe('GET');
    request.flush({ status: 'UP' });
  });

  it('should post allocation execution request to /api/allocations', () => {
    const body: AllocationApiRequest = {
      selectionMode: 'EXPLICIT',
      algorithm: 'GREEDY',
      resources: [],
      requests: [],
    };

    service.executeAllocation(body).subscribe();

    const request = httpTestingController.expectOne('/api/allocations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('should post comparison request to /api/allocations/compare', () => {
    const body: AllocationComparisonApiRequest = {
      resources: [],
      requests: [],
    };

    service.compareAllocations(body).subscribe();

    const request = httpTestingController.expectOne('/api/allocations/compare');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });
});
