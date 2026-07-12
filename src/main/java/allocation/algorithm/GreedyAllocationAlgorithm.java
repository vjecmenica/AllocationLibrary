package allocation.algorithm;

import allocation.constraint.ConstraintResult;
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
import java.util.List;
import java.util.Map;

public class GreedyAllocationAlgorithm implements AllocationAlgorithm {

    private ConstraintValidator constraintValidator;

    public GreedyAllocationAlgorithm() {
        this(ConstraintValidator.defaultValidator());
    }

    public GreedyAllocationAlgorithm(ConstraintValidator constraintValidator) {
        if (constraintValidator == null) {
            throw new IllegalArgumentException("ConstraintValidator must not be null.");
        }

        this.constraintValidator = constraintValidator;
    }

    @Override
    public AllocationResult allocate(List<Resource> resources, List<AllocationRequest> requests) {
        long startTime = System.currentTimeMillis();

        List<Allocation> allocations = new ArrayList<>();
        List<RejectedRequest> rejectedRequests = new ArrayList<>();

        List<AllocationRequest> sortedRequests = new ArrayList<>(requests);

        sortedRequests.sort(
                Comparator.comparingInt(AllocationRequest::getPriority)
                        .reversed()
                        .thenComparing(request -> request.getTimeWindow().getStart())
        );

        for (AllocationRequest request : sortedRequests) {
            AllocationAttempt attempt = findResourcesForRequest(
                    request,
                    resources,
                    allocations
            );

            if (attempt.isSuccessful()) {
                allocations.add(new Allocation(request, attempt.getSelectedResources()));
            } else {
                rejectedRequests.add(
                        new RejectedRequest(
                                request,
                                attempt.getRejectionReason()
                        )
                );
            }
        }

        long endTime = System.currentTimeMillis();

        int totalPriorityScore = calculateTotalPriorityScore(allocations);

        AllocationStatistics statistics = new AllocationStatistics(
                requests.size(),
                allocations.size(),
                rejectedRequests.size(),
                endTime - startTime,
                totalPriorityScore
        );

        return new AllocationResult(allocations, rejectedRequests, statistics);
    }

    @Override
    public String getName() {
        return "GREEDY";
    }

    private AllocationAttempt findResourcesForRequest(
            AllocationRequest request,
            List<Resource> resources,
            List<Allocation> currentAllocations
    ) {
        List<Resource> selectedResources = new ArrayList<>();

        for (ResourceRequirement requirement : request.getResourceRequirements()) {
            RequirementSearchResult searchResult = findResourcesForRequirement(
                    request,
                    requirement,
                    resources,
                    currentAllocations,
                    selectedResources
            );

            if (!searchResult.isSuccessful(requirement.getQuantity())) {
                return AllocationAttempt.rejected(searchResult.getRejectionReason());
            }

            selectedResources.addAll(searchResult.getSelectedResources());
        }

        return AllocationAttempt.successful(selectedResources);
    }

    private RequirementSearchResult findResourcesForRequirement(
            AllocationRequest request,
            ResourceRequirement requirement,
            List<Resource> resources,
            List<Allocation> currentAllocations,
            List<Resource> alreadySelectedResources
    ) {
        List<Resource> selected = new ArrayList<>();
        List<String> violationMessages = new ArrayList<>();

        for (Resource resource : resources) {
            if (selected.size() == requirement.getQuantity()) {
                break;
            }

            if (isAlreadySelected(resource, alreadySelectedResources)) {
                continue;
            }

            ConstraintResult constraintResult = constraintValidator.validate(
                    request,
                    requirement,
                    resource,
                    currentAllocations
            );

            if (constraintResult.isSatisfied()) {
                selected.add(resource);
            } else {
                if (resource.getType().equals(requirement.getResourceType())) {
                    violationMessages.add(
                            resource.getName() + " [" + resource.getType() + "]: " +
                                    constraintResult.getMessage()
                    );
                }
            }
        }

        if (selected.size() < requirement.getQuantity()) {
            String reason = buildRejectionReason(
                    request,
                    requirement,
                    selected.size(),
                    violationMessages
            );

            return RequirementSearchResult.rejected(selected, reason);
        }

        return RequirementSearchResult.successful(selected);
    }

    private String buildRejectionReason(
            AllocationRequest request,
            ResourceRequirement requirement,
            int foundCount,
            List<String> violationMessages
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("Request '")
                .append(request.getName())
                .append("' cannot be allocated. ");

        builder.append("Not enough resources found for requirement: type=")
                .append(requirement.getResourceType())
                .append(", required=")
                .append(requirement.getQuantity())
                .append(", found=")
                .append(foundCount)
                .append(".");

        Map<String, Integer> requiredCapacities = requirement.getRequiredCapacities();

        if (requiredCapacities != null && !requiredCapacities.isEmpty()) {
            builder.append(" Required capacities: ")
                    .append(requiredCapacities)
                    .append(".");
        }

        if (violationMessages != null && !violationMessages.isEmpty()) {
            builder.append(" Example reason: ");

            int maxMessages = Math.min(3, violationMessages.size());

            for (int i = 0; i < maxMessages; i++) {
                builder.append(violationMessages.get(i));

                if (i < maxMessages - 1) {
                    builder.append(" | ");
                }
            }

            if (violationMessages.size() > maxMessages) {
                builder.append(" | ...");
            }
        }

        return builder.toString();
    }

    private boolean isAlreadySelected(Resource resource, List<Resource> alreadySelectedResources) {
        for (Resource selectedResource : alreadySelectedResources) {
            if (selectedResource.getId().equals(resource.getId())) {
                return true;
            }
        }

        return false;
    }

    private int calculateTotalPriorityScore(List<Allocation> allocations) {
        int sum = 0;

        for (Allocation allocation : allocations) {
            sum += allocation.getRequest().getPriority();
        }

        return sum;
    }

    private static class AllocationAttempt {

        private boolean successful;
        private List<Resource> selectedResources;
        private String rejectionReason;

        private AllocationAttempt(
                boolean successful,
                List<Resource> selectedResources,
                String rejectionReason
        ) {
            this.successful = successful;
            this.selectedResources = selectedResources;
            this.rejectionReason = rejectionReason;
        }

        public static AllocationAttempt successful(List<Resource> selectedResources) {
            return new AllocationAttempt(true, selectedResources, null);
        }

        public static AllocationAttempt rejected(String rejectionReason) {
            return new AllocationAttempt(false, new ArrayList<>(), rejectionReason);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public List<Resource> getSelectedResources() {
            return selectedResources;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }

    private static class RequirementSearchResult {

        private List<Resource> selectedResources;
        private String rejectionReason;

        private RequirementSearchResult(
                List<Resource> selectedResources,
                String rejectionReason
        ) {
            this.selectedResources = selectedResources;
            this.rejectionReason = rejectionReason;
        }

        public static RequirementSearchResult successful(List<Resource> selectedResources) {
            return new RequirementSearchResult(selectedResources, null);
        }

        public static RequirementSearchResult rejected(
                List<Resource> selectedResources,
                String rejectionReason
        ) {
            return new RequirementSearchResult(selectedResources, rejectionReason);
        }

        public boolean isSuccessful(int requiredQuantity) {
            return selectedResources.size() >= requiredQuantity;
        }

        public List<Resource> getSelectedResources() {
            return selectedResources;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }
    }
}
