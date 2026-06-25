package allocation.algorithm;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.RejectedRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.constraint.ConstraintValidator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GreedyAllocationAlgorithm implements AllocationAlgorithm {

    private ConstraintValidator constraintValidator;

    public GreedyAllocationAlgorithm() {
        this(ConstraintValidator.defaultValidator());
    }

    public GreedyAllocationAlgorithm(ConstraintValidator constraintValidator) {
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
            List<Resource> selectedResources = findResourcesForRequest(
                    request,
                    resources,
                    allocations
            );

            if (selectedResources != null) {
                allocations.add(new Allocation(request, selectedResources));
            } else {
                rejectedRequests.add(
                        new RejectedRequest(
                                request,
                                "Nije pronađen skup resursa koji zadovoljava sve potrebe zahteva."
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

    private List<Resource> findResourcesForRequest(
            AllocationRequest request,
            List<Resource> resources,
            List<Allocation> currentAllocations
    ) {
        List<Resource> selectedResources = new ArrayList<>();

        for (ResourceRequirement requirement : request.getResourceRequirements()) {
            List<Resource> resourcesForRequirement = findResourcesForRequirement(
                    request,
                    requirement,
                    resources,
                    currentAllocations,
                    selectedResources
            );

            if (resourcesForRequirement.size() < requirement.getQuantity()) {
                return null;
            }

            selectedResources.addAll(resourcesForRequirement);
        }

        return selectedResources;
    }

    private List<Resource> findResourcesForRequirement(
            AllocationRequest request,
            ResourceRequirement requirement,
            List<Resource> resources,
            List<Allocation> currentAllocations,
            List<Resource> alreadySelectedResources
    ) {
        List<Resource> selected = new ArrayList<>();

        for (Resource resource : resources) {
            if (selected.size() == requirement.getQuantity()) {
                break;
            }

            if (isAlreadySelected(resource, alreadySelectedResources)) {
                continue;
            }

            if (canAllocateResourceToRequirement(
                    request,
                    requirement,
                    resource,
                    currentAllocations
            )) {
                selected.add(resource);
            }
        }

        return selected;
    }

    private boolean canAllocateResourceToRequirement(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        return constraintValidator.isValid(
                request,
                requirement,
                resource,
                currentAllocations
        );
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
}