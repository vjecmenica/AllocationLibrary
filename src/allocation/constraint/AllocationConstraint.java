package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.List;

public interface AllocationConstraint {

    ConstraintResult validate(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    );

    String getName();
}