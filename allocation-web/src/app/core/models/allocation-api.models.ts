export type AllocationSelectionMode = 'EXPLICIT' | 'AUTO';
export type AllocationAlgorithmType = 'GREEDY' | 'BACKTRACKING' | 'CP_SAT';
export type AllocationGoal = 'FASTEST' | 'BALANCED' | 'BEST_QUALITY';

export interface HealthResponse {
  status: string;
}

export interface TimeWindowDto {
  start: string;
  end: string;
}

export interface ResourceDto {
  id: string;
  name: string;
  type: string;
  capacities: Record<string, number>;
  availability: TimeWindowDto[];
}

export interface ResourceRequirementDto {
  resourceType: string;
  quantity: number;
  requiredCapacities: Record<string, number>;
}

export interface AllocationRequestDto {
  id: string;
  name: string;
  startTime: string;
  durationMinutes: number;
  priority: number;
  resourceRequirements: ResourceRequirementDto[];
}

export interface AllocationApiRequest {
  selectionMode: AllocationSelectionMode;
  algorithm?: AllocationAlgorithmType;
  goal?: AllocationGoal;
  backtrackingTimeLimitMs?: number;
  cpSatTimeLimitSeconds?: number;
  resources: ResourceDto[];
  requests: AllocationRequestDto[];
}

export interface AllocationStatistics {
  totalRequests: number;
  allocatedRequests: number;
  rejectedRequests: number;
  algorithmExecutionTimeMs: number;
  totalPriorityScore: number;
  exploredStates: number;
  stoppedByLimit: boolean;
  algorithmStatus: string | null;
  objectiveValue: number;
}

export interface AllocationDto {
  request: AllocationRequestDto;
  assignedResources: ResourceDto[];
}

export interface RejectedRequestDto {
  request: AllocationRequestDto;
  reason: string;
}

export interface AllocationApiResponse {
  selectionMode: AllocationSelectionMode;
  requestedAlgorithm: AllocationAlgorithmType | null;
  executedAlgorithm: AllocationAlgorithmType;
  goal: AllocationGoal | null;
  selectionReason: string;
  measuredExecutionTimeMs: number;
  allocations: AllocationDto[];
  rejectedRequests: RejectedRequestDto[];
  statistics: AllocationStatistics;
}

export interface AllocationComparisonEntry {
  algorithm: AllocationAlgorithmType;
  measuredExecutionTimeMs: number;
  allocationResult: {
    allocations: AllocationDto[];
    rejectedRequests: RejectedRequestDto[];
    statistics: AllocationStatistics;
  };
}

export interface AllocationComparisonApiRequest {
  backtrackingTimeLimitMs?: number;
  cpSatTimeLimitSeconds?: number;
  resources: ResourceDto[];
  requests: AllocationRequestDto[];
}

export interface AllocationComparisonApiResponse {
  results: Record<AllocationAlgorithmType, AllocationComparisonEntry>;
  bestTotalPriorityScore: number;
  bestScoreAlgorithms: AllocationAlgorithmType[];
  fastestAlgorithm: AllocationAlgorithmType;
}

export interface ApiErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
}
