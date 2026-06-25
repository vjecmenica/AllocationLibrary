package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;

import java.util.List;
import java.util.Map;

public class CapacityConstraint implements AllocationConstraint {

    @Override
    public ConstraintResult validate(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource,
            List<Allocation> currentAllocations
    ) {
        Map<String, Integer> requiredCapacities = requirement.getRequiredCapacities();

        if (requiredCapacities == null || requiredCapacities.isEmpty()) {
            return ConstraintResult.satisfied();
        }

        for (Map.Entry<String, Integer> entry : requiredCapacities.entrySet()) {
            String capacityName = entry.getKey();
            int requiredValue = entry.getValue();

            int actualValue = resource.getCapacity(capacityName);

            if (actualValue < requiredValue) {
                return ConstraintResult.violated(
                        "Resurs '" + resource.getName() + "' nema dovoljan kapacitet za '" +
                                capacityName + "'. Potrebno: " + requiredValue +
                                ", dostupno: " + actualValue
                );
            }
        }

        return ConstraintResult.satisfied();
    }

    @Override
    public String getName() {
        return "CAPACITY";
    }
}