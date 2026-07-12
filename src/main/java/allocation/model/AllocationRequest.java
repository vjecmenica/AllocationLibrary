package allocation.model;

import java.time.LocalDateTime;
import java.util.List;

public class AllocationRequest {

    private String id;
    private String name;
    private TimeWindow timeWindow;
    private int priority;
    private List<ResourceRequirement> resourceRequirements;

    public AllocationRequest(
            String id,
            String name,
            LocalDateTime startTime,
            int durationMinutes,
            int priority,
            List<ResourceRequirement> resourceRequirements
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Allocation request ID must not be blank.");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Allocation request name must not be blank.");
        }

        if (startTime == null) {
            throw new IllegalArgumentException("Start time must not be null.");
        }

        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Duration must be positive.");
        }

        if (resourceRequirements == null || resourceRequirements.isEmpty()) {
            throw new IllegalArgumentException("Allocation request must have at least one resource requirement.");
        }

        this.id = id;
        this.name = name;
        this.timeWindow = new TimeWindow(startTime, startTime.plusMinutes(durationMinutes));
        this.priority = priority;
        this.resourceRequirements = resourceRequirements;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public TimeWindow getTimeWindow() {
        return timeWindow;
    }

    public int getPriority() {
        return priority;
    }

    public List<ResourceRequirement> getResourceRequirements() {
        return resourceRequirements;
    }
}
