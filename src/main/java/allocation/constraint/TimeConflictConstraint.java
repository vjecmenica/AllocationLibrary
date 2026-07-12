package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.List;

public class TimeConflictConstraint implements AllocationConstraint {

    @Override
    public ConstraintResult validate(
            AllocationRequest request,
            ResourceRequirement requirement,
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
                    return ConstraintResult.violated(
                            "Resource '" + resource.getName() + "' is already allocated during the requested time window."
                    );
                }
            }
        }

        return ConstraintResult.satisfied();
    }

    @Override
    public String getName() {
        return "TIME_CONFLICT";
    }
}
