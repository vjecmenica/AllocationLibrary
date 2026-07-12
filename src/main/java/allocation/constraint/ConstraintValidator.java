package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.List;

public class ConstraintValidator {

    private List<AllocationConstraint> constraints;

    public ConstraintValidator(List<AllocationConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            throw new IllegalArgumentException("Constraint list must not be empty.");
        }

        this.constraints = constraints;
    }

    public static ConstraintValidator defaultValidator() {
        return new ConstraintValidator(
                List.of(
                        new ResourceTypeConstraint(),
                        new CapacityConstraint(),
                        new AvailabilityConstraint(),
                        new TimeConflictConstraint()
                )
        );
    }

    public ConstraintResult validate(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        for (AllocationConstraint constraint : constraints) {
            ConstraintResult result = constraint.validate(
                    request,
                    requirement,
                    resource,
                    currentAllocations
            );

            if (!result.isSatisfied()) {
                return result;
            }
        }

        return ConstraintResult.satisfied();
    }

    public boolean isValid(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        return validate(request, requirement, resource, currentAllocations).isSatisfied();
    }
}
