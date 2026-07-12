package allocation.model;

import java.util.Map;

public class ResourceRequirement {

    private String resourceType;
    private int quantity;
    private Map<String, Integer> requiredCapacities;

    public ResourceRequirement(
            String resourceType,
            int quantity,
            Map<String, Integer> requiredCapacities
    ) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Resource type must not be blank.");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Resource quantity must be positive.");
        }

        if (requiredCapacities != null) {
            for (Map.Entry<String, Integer> entry : requiredCapacities.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    throw new IllegalArgumentException("Capacity name must not be blank.");
                }

                if (entry.getValue() == null || entry.getValue() < 0) {
                    throw new IllegalArgumentException("Capacity value must not be negative.");
                }
            }
        }

        this.resourceType = resourceType;
        this.quantity = quantity;
        this.requiredCapacities = requiredCapacities;
    }

    public boolean isSatisfiedBy(Resource resource) {
        if (resource == null) {
            return false;
        }

        if (!resourceType.equals(resource.getType())) {
            return false;
        }

        if (requiredCapacities == null || requiredCapacities.isEmpty()) {
            return true;
        }

        for (Map.Entry<String, Integer> entry : requiredCapacities.entrySet()) {
            String capacityName = entry.getKey();
            int requiredValue = entry.getValue();

            if (resource.getCapacity(capacityName) < requiredValue) {
                return false;
            }
        }

        return true;
    }

    public String getResourceType() {
        return resourceType;
    }

    public int getQuantity() {
        return quantity;
    }

    public Map<String, Integer> getRequiredCapacities() {
        return requiredCapacities;
    }
}
