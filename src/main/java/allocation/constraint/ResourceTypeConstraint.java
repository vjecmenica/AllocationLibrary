package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.List;

public class ResourceTypeConstraint implements AllocationConstraint {

    @Override
    public ConstraintResult validate(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        if (!requirement.getResourceType().equals(resource.getType())) {
            return ConstraintResult.violated(
                    "Resurs '" + resource.getName() + "' nije odgovarajućeg tipa. " +
                            "Potreban tip: " + requirement.getResourceType() +
                            ", tip resursa: " + resource.getType()
            );
        }

        return ConstraintResult.satisfied();
    }

    @Override
    public String getName() {
        return "RESOURCE_TYPE";
    }
}