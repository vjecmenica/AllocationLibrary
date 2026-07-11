package allocation.testutil;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class AllocationResultValidator {

    private AllocationResultValidator() {
    }

    public static void assertValid(
            AllocationResult result,
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        assertNotNull(result);
        assertNotNull(result.getAllocations());
        assertNotNull(result.getRejectedRequests());
        assertNotNull(result.getStatistics());

        Set<String> knownResourceIds = new HashSet<>();
        for (Resource resource : resources) {
            knownResourceIds.add(resource.getId());
        }

        Set<String> knownRequestIds = new HashSet<>();
        for (AllocationRequest request : requests) {
            knownRequestIds.add(request.getId());
        }

        Set<String> allocatedRequestIds = new HashSet<>();
        for (Allocation allocation : result.getAllocations()) {
            AllocationRequest request = allocation.getRequest();
            assertNotNull(request);
            assertTrue(knownRequestIds.contains(request.getId()));
            assertTrue(allocatedRequestIds.add(request.getId()));

            validateSingleAllocation(allocation, knownResourceIds);
        }

        Set<String> rejectedRequestIds = new HashSet<>();
        result.getRejectedRequests().forEach(rejectedRequest -> {
            assertNotNull(rejectedRequest.getRequest());
            String requestId = rejectedRequest.getRequest().getId();

            assertTrue(knownRequestIds.contains(requestId));
            assertTrue(rejectedRequestIds.add(requestId));
            assertFalse(allocatedRequestIds.contains(requestId));
            assertNotBlank(rejectedRequest.getReason());
        });

        assertEquals(
                requests.size(),
                allocatedRequestIds.size() + rejectedRequestIds.size()
        );

        assertEquals(requests.size(), result.getStatistics().getTotalRequests());
        assertEquals(result.getAllocations().size(), result.getStatistics().getAllocatedRequests());
        assertEquals(result.getRejectedRequests().size(), result.getStatistics().getRejectedRequests());
        assertEquals(
                requests.size(),
                result.getStatistics().getAllocatedRequests() + result.getStatistics().getRejectedRequests()
        );
        assertEquals(calculateTotalPriorityScore(result.getAllocations()), result.getStatistics().getTotalPriorityScore());

        validateNoSharedResourcesForOverlappingRequests(result.getAllocations());
    }

    private static void validateSingleAllocation(
            Allocation allocation,
            Set<String> knownResourceIds
    ) {
        AllocationRequest request = allocation.getRequest();
        List<Resource> assignedResources = allocation.getAssignedResources();

        assertNotNull(assignedResources);

        int requiredResourceCount = 0;
        for (ResourceRequirement requirement : request.getResourceRequirements()) {
            requiredResourceCount += requirement.getQuantity();
        }

        assertEquals(requiredResourceCount, assignedResources.size());

        Set<String> assignedResourceIds = new HashSet<>();
        for (Resource resource : assignedResources) {
            assertNotNull(resource);
            assertTrue(knownResourceIds.contains(resource.getId()));
            assertTrue(assignedResourceIds.add(resource.getId()));
            assertTrue(resource.isAvailableFor(request.getTimeWindow()));
        }

        assertTrue(canAssignResourcesToRequirements(request.getResourceRequirements(), assignedResources));
    }

    private static boolean satisfiesRequirement(
            Resource resource,
            ResourceRequirement requirement
    ) {
        if (!requirement.getResourceType().equals(resource.getType())) {
            return false;
        }

        Map<String, Integer> requiredCapacities = requirement.getRequiredCapacities();

        if (requiredCapacities == null || requiredCapacities.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Integer> entry : requiredCapacities.entrySet()) {
            if (resource.getCapacity(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }

        return true;
    }

    private static boolean canAssignResourcesToRequirements(
            List<ResourceRequirement> requirements,
            List<Resource> resources
    ) {
        List<ResourceRequirement> requirementSlots = new ArrayList<>();

        for (ResourceRequirement requirement : requirements) {
            for (int i = 0; i < requirement.getQuantity(); i++) {
                requirementSlots.add(requirement);
            }
        }

        return canAssignRequirementSlot(requirementSlots, resources, new HashSet<>(), 0);
    }

    private static boolean canAssignRequirementSlot(
            List<ResourceRequirement> requirementSlots,
            List<Resource> resources,
            Set<Integer> usedResourceIndexes,
            int slotIndex
    ) {
        if (slotIndex == requirementSlots.size()) {
            return true;
        }

        ResourceRequirement requirement = requirementSlots.get(slotIndex);

        for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
            if (usedResourceIndexes.contains(resourceIndex)) {
                continue;
            }

            Resource resource = resources.get(resourceIndex);

            if (!satisfiesRequirement(resource, requirement)) {
                continue;
            }

            usedResourceIndexes.add(resourceIndex);

            if (canAssignRequirementSlot(requirementSlots, resources, usedResourceIndexes, slotIndex + 1)) {
                return true;
            }

            usedResourceIndexes.remove(resourceIndex);
        }

        return false;
    }

    private static int calculateTotalPriorityScore(List<Allocation> allocations) {
        int sum = 0;

        for (Allocation allocation : allocations) {
            sum += allocation.getRequest().getPriority();
        }

        return sum;
    }

    private static void assertNotBlank(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
    }

    private static void validateNoSharedResourcesForOverlappingRequests(
            List<Allocation> allocations
    ) {
        for (int i = 0; i < allocations.size(); i++) {
            Allocation first = allocations.get(i);

            for (int j = i + 1; j < allocations.size(); j++) {
                Allocation second = allocations.get(j);

                if (!first.getRequest().getTimeWindow().overlaps(second.getRequest().getTimeWindow())) {
                    continue;
                }

                Set<String> firstResourceIds = new HashSet<>();
                for (Resource resource : first.getAssignedResources()) {
                    firstResourceIds.add(resource.getId());
                }

                for (Resource resource : second.getAssignedResources()) {
                    assertFalse(firstResourceIds.contains(resource.getId()));
                }
            }
        }
    }
}
