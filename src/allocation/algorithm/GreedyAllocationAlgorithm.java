package allocation.algorithm;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.RejectedRequest;
import allocation.model.Resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class GreedyAllocationAlgorithm implements AllocationAlgorithm {

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
            Resource selectedResource = findFirstAvailableResource(request, resources, allocations);

            if (selectedResource != null) {
                Allocation allocation = new Allocation(request, List.of(selectedResource));
                allocations.add(allocation);
            } else {
                rejectedRequests.add(
                        new RejectedRequest(
                                request,
                                "Nije pronađen slobodan resurs koji zadovoljava tip, kapacitet i vremensku dostupnost."
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

    private Resource findFirstAvailableResource(
            AllocationRequest request,
            List<Resource> resources,
            List<Allocation> currentAllocations
    ) {
        for (Resource resource : resources) {
            if (canAllocate(request, resource, currentAllocations)) {
                return resource;
            }
        }

        return null;
    }

    private boolean canAllocate(
            AllocationRequest request,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        return hasAllowedType(request, resource)
                && isAvailableInRequestedTime(request, resource)
                && hasEnoughCapacity(request, resource)
                && hasNoTimeConflict(request, resource, currentAllocations);
    }

    private boolean hasAllowedType(AllocationRequest request, Resource resource) {
        return request.allowsResourceType(resource.getType());
    }

    private boolean isAvailableInRequestedTime(AllocationRequest request, Resource resource) {
        return resource.isAvailableFor(request.getTimeWindow());
    }

    private boolean hasEnoughCapacity(AllocationRequest request, Resource resource) {
        Map<String, Integer> requirements = request.getRequirements();

        if (requirements == null || requirements.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String capacityName = entry.getKey();
            int requiredValue = entry.getValue();

            int resourceCapacity = resource.getCapacity(capacityName);

            if (resourceCapacity < requiredValue) {
                return false;
            }
        }

        return true;
    }

    private boolean hasNoTimeConflict(
            AllocationRequest request,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        for (Allocation allocation : currentAllocations) {
            boolean sameResourceUsed = allocation.getAssignedResources()
                    .stream()
                    .anyMatch(assignedResource -> assignedResource.getId().equals(resource.getId()));

            if (sameResourceUsed) {
                boolean timeOverlaps = allocation.getRequest()
                        .getTimeWindow()
                        .overlaps(request.getTimeWindow());

                if (timeOverlaps) {
                    return false;
                }
            }
        }

        return true;
    }

    private int calculateTotalPriorityScore(List<Allocation> allocations) {
        int sum = 0;

        for (Allocation allocation : allocations) {
            sum += allocation.getRequest().getPriority();
        }

        return sum;
    }
}