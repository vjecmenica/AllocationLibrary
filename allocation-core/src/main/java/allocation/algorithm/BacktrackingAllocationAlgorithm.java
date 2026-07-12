package allocation.algorithm;

import allocation.constraint.ConstraintValidator;
import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.RejectedRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BacktrackingAllocationAlgorithm implements AllocationAlgorithm {

    private static final long DEFAULT_TIME_LIMIT_MS = 5000;

    private ConstraintValidator constraintValidator;
    private long maxExecutionTimeMs;

    private long exploredStates;
    private boolean stoppedByTimeLimit;

    public BacktrackingAllocationAlgorithm() {
        this(ConstraintValidator.defaultValidator(), DEFAULT_TIME_LIMIT_MS);
    }

    public BacktrackingAllocationAlgorithm(long maxExecutionTimeMs) {
        this(ConstraintValidator.defaultValidator(), maxExecutionTimeMs);
    }

    public BacktrackingAllocationAlgorithm(ConstraintValidator constraintValidator) {
        this(constraintValidator, DEFAULT_TIME_LIMIT_MS);
    }

    public BacktrackingAllocationAlgorithm(
            ConstraintValidator constraintValidator,
            long maxExecutionTimeMs
    ) {
        if (constraintValidator == null) {
            throw new IllegalArgumentException("ConstraintValidator must not be null.");
        }

        if (maxExecutionTimeMs <= 0) {
            throw new IllegalArgumentException("Time limit must be positive.");
        }

        this.constraintValidator = constraintValidator;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
    }

    @Override
    public AllocationResult allocate(List<Resource> resources, List<AllocationRequest> requests) {
        long startTime = System.currentTimeMillis();

        exploredStates = 0;
        stoppedByTimeLimit = false;

        List<AllocationRequest> sortedRequests = new ArrayList<>(requests);

        sortedRequests.sort(
                Comparator.comparingInt(AllocationRequest::getPriority)
                        .reversed()
                        .thenComparing(request -> request.getTimeWindow().getStart())
        );

        BestSolution bestSolution = new BestSolution();

        backtrack(
                0,
                sortedRequests,
                resources,
                new ArrayList<>(),
                bestSolution,
                startTime
        );

        List<Allocation> bestAllocations = bestSolution.getAllocations();

        List<RejectedRequest> rejectedRequests = buildRejectedRequests(
                sortedRequests,
                resources,
                bestAllocations
        );

        long endTime = System.currentTimeMillis();

        int totalPriorityScore = calculateTotalPriorityScore(bestAllocations);

        AllocationStatistics statistics = new AllocationStatistics(
                requests.size(),
                bestAllocations.size(),
                rejectedRequests.size(),
                endTime - startTime,
                totalPriorityScore,
                exploredStates,
                stoppedByTimeLimit
        );

        return new AllocationResult(
                bestAllocations,
                rejectedRequests,
                statistics
        );
    }

    @Override
    public String getName() {
        return "BACKTRACKING";
    }

    private void backtrack(
            int index,
            List<AllocationRequest> requests,
            List<Resource> resources,
            List<Allocation> currentAllocations,
            BestSolution bestSolution,
            long startTime
    ) {
        if (isTimeLimitExceeded(startTime)) {
            stoppedByTimeLimit = true;
            bestSolution.tryUpdate(currentAllocations);
            return;
        }

        exploredStates++;

        /*
         * Keep the best solution even before the search reaches the end.
         * This matters if the algorithm is stopped by the time limit.
         */
        bestSolution.tryUpdate(currentAllocations);

        if (index == requests.size()) {
            return;
        }

        if (!canStillBeatBest(index, requests, currentAllocations, bestSolution)) {
            return;
        }

        AllocationRequest currentRequest = requests.get(index);

        List<List<Resource>> candidates = generateCandidatesForRequest(
                currentRequest,
                resources,
                currentAllocations
        );

        for (List<Resource> candidateResources : candidates) {
            if (isTimeLimitExceeded(startTime)) {
                stoppedByTimeLimit = true;
                bestSolution.tryUpdate(currentAllocations);
                return;
            }

            Allocation allocation = new Allocation(
                    currentRequest,
                    candidateResources
            );

            currentAllocations.add(allocation);

            backtrack(
                    index + 1,
                    requests,
                    resources,
                    currentAllocations,
                    bestSolution,
                    startTime
            );

            currentAllocations.remove(currentAllocations.size() - 1);
        }

        /*
         * Option to skip the current request.
         * Sometimes skipping one request enables a better total result.
         */
        backtrack(
                index + 1,
                requests,
                resources,
                currentAllocations,
                bestSolution,
                startTime
        );
    }

    private boolean isTimeLimitExceeded(long startTime) {
        return System.currentTimeMillis() - startTime >= maxExecutionTimeMs;
    }

    private List<List<Resource>> generateCandidatesForRequest(
            AllocationRequest request,
            List<Resource> resources,
            List<Allocation> currentAllocations
    ) {
        List<List<Resource>> allCandidates = new ArrayList<>();

        buildCandidatesForRequirements(
                request,
                resources,
                currentAllocations,
                0,
                new ArrayList<>(),
                allCandidates
        );

        return allCandidates;
    }

    private void buildCandidatesForRequirements(
            AllocationRequest request,
            List<Resource> resources,
            List<Allocation> currentAllocations,
            int requirementIndex,
            List<Resource> selectedForRequest,
            List<List<Resource>> allCandidates
    ) {
        if (requirementIndex == request.getResourceRequirements().size()) {
            allCandidates.add(new ArrayList<>(selectedForRequest));
            return;
        }

        ResourceRequirement requirement = request.getResourceRequirements().get(requirementIndex);

        List<List<Resource>> combinationsForRequirement = generateCombinationsForRequirement(
                request,
                requirement,
                resources,
                currentAllocations,
                selectedForRequest
        );

        for (List<Resource> combination : combinationsForRequirement) {
            selectedForRequest.addAll(combination);

            buildCandidatesForRequirements(
                    request,
                    resources,
                    currentAllocations,
                    requirementIndex + 1,
                    selectedForRequest,
                    allCandidates
            );

            removeLastResources(selectedForRequest, combination.size());
        }
    }

    private List<List<Resource>> generateCombinationsForRequirement(
            AllocationRequest request,
            ResourceRequirement requirement,
            List<Resource> resources,
            List<Allocation> currentAllocations,
            List<Resource> alreadySelectedForRequest
    ) {
        List<Resource> validResources = new ArrayList<>();

        for (Resource resource : resources) {
            if (containsResource(alreadySelectedForRequest, resource)) {
                continue;
            }

            if (constraintValidator.isValid(
                    request,
                    requirement,
                    resource,
                    currentAllocations
            )) {
                validResources.add(resource);
            }
        }

        List<List<Resource>> combinations = new ArrayList<>();

        buildResourceCombinations(
                validResources,
                requirement.getQuantity(),
                0,
                new ArrayList<>(),
                combinations
        );

        return combinations;
    }

    private void buildResourceCombinations(
            List<Resource> validResources,
            int requiredQuantity,
            int startIndex,
            List<Resource> currentCombination,
            List<List<Resource>> combinations
    ) {
        if (currentCombination.size() == requiredQuantity) {
            combinations.add(new ArrayList<>(currentCombination));
            return;
        }

        for (int i = startIndex; i < validResources.size(); i++) {
            currentCombination.add(validResources.get(i));

            buildResourceCombinations(
                    validResources,
                    requiredQuantity,
                    i + 1,
                    currentCombination,
                    combinations
            );

            currentCombination.remove(currentCombination.size() - 1);
        }
    }

    private boolean canStillBeatBest(
            int index,
            List<AllocationRequest> requests,
            List<Allocation> currentAllocations,
            BestSolution bestSolution
    ) {
        int currentScore = calculateTotalPriorityScore(currentAllocations);
        int optimisticRemainingScore = 0;

        for (int i = index; i < requests.size(); i++) {
            optimisticRemainingScore += requests.get(i).getPriority();
        }

        int bestScore = calculateTotalPriorityScore(bestSolution.getAllocations());

        return currentScore + optimisticRemainingScore >= bestScore;
    }

    private List<RejectedRequest> buildRejectedRequests(
            List<AllocationRequest> requests,
            List<Resource> resources,
            List<Allocation> bestAllocations
    ) {
        List<RejectedRequest> rejectedRequests = new ArrayList<>();

        Set<String> allocatedRequestIds = new HashSet<>();

        for (Allocation allocation : bestAllocations) {
            allocatedRequestIds.add(allocation.getRequest().getId());
        }

        for (AllocationRequest request : requests) {
            if (!allocatedRequestIds.contains(request.getId())) {
                String reason = buildRejectionReason(
                        request,
                        resources,
                        bestAllocations
                );

                rejectedRequests.add(new RejectedRequest(request, reason));
            }
        }

        return rejectedRequests;
    }

    private String buildRejectionReason(
            AllocationRequest request,
            List<Resource> resources,
            List<Allocation> finalAllocations
    ) {
        List<List<Resource>> possibleCandidates = generateCandidatesForRequest(
                request,
                resources,
                finalAllocations
        );

        if (possibleCandidates.isEmpty()) {
            return "Request was not allocated because no available set of resources in the final schedule satisfies all its requirements.";
        }

        return "Request was not allocated because allocating it would reduce the total value of the best found solution.";
    }

    private boolean containsResource(List<Resource> resources, Resource targetResource) {
        for (Resource resource : resources) {
            if (resource.getId().equals(targetResource.getId())) {
                return true;
            }
        }

        return false;
    }

    private void removeLastResources(List<Resource> resources, int numberOfResourcesToRemove) {
        for (int i = 0; i < numberOfResourcesToRemove; i++) {
            resources.remove(resources.size() - 1);
        }
    }

    private int calculateTotalPriorityScore(List<Allocation> allocations) {
        int sum = 0;

        for (Allocation allocation : allocations) {
            sum += allocation.getRequest().getPriority();
        }

        return sum;
    }

    private static class BestSolution {

        private List<Allocation> allocations = new ArrayList<>();

        public void tryUpdate(List<Allocation> candidateAllocations) {
            if (isBetter(candidateAllocations, allocations)) {
                allocations = copyAllocations(candidateAllocations);
            }
        }

        public List<Allocation> getAllocations() {
            return allocations;
        }

        private boolean isBetter(
                List<Allocation> candidateAllocations,
                List<Allocation> currentBestAllocations
        ) {
            int candidateScore = calculateScore(candidateAllocations);
            int currentBestScore = calculateScore(currentBestAllocations);

            if (candidateScore > currentBestScore) {
                return true;
            }

            if (candidateScore < currentBestScore) {
                return false;
            }

            return candidateAllocations.size() > currentBestAllocations.size();
        }

        private int calculateScore(List<Allocation> allocations) {
            int sum = 0;

            for (Allocation allocation : allocations) {
                sum += allocation.getRequest().getPriority();
            }

            return sum;
        }

        private List<Allocation> copyAllocations(List<Allocation> originalAllocations) {
            List<Allocation> copy = new ArrayList<>();

            for (Allocation allocation : originalAllocations) {
                copy.add(
                        new Allocation(
                                allocation.getRequest(),
                                new ArrayList<>(allocation.getAssignedResources())
                        )
                );
            }

            return copy;
        }
    }
}
