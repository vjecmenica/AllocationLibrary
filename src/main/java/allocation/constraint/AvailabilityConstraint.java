package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.List;

public class AvailabilityConstraint implements AllocationConstraint {

    @Override
    public ConstraintResult validate(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        if (!resource.isAvailableFor(request.getTimeWindow())) {
            return ConstraintResult.violated(
                    "Resource '" + resource.getName() + "' is not available in the requested time window."
            );
        }

        return ConstraintResult.satisfied();
    }

    @Override
    public String getName() {
        return "AVAILABILITY";
    }
}
