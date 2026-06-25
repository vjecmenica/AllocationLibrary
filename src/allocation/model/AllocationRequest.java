package allocation.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AllocationRequest {

    private String id;
    private String name;
    private TimeWindow timeWindow;
    private int priority;
    private Map<String, Integer> requirements;
    private List<String> allowedResourceTypes;

    public AllocationRequest(
            String id,
            String name,
            LocalDateTime startTime,
            int durationMinutes,
            int priority,
            Map<String, Integer> requirements,
            List<String> allowedResourceTypes
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID zahteva ne sme biti prazan.");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Naziv zahteva ne sme biti prazan.");
        }

        if (startTime == null) {
            throw new IllegalArgumentException("Vreme početka ne sme biti null.");
        }

        if (durationMinutes <= 0) {
            throw new IllegalArgumentException("Trajanje mora biti pozitivno.");
        }

        this.id = id;
        this.name = name;
        this.timeWindow = new TimeWindow(startTime, startTime.plusMinutes(durationMinutes));
        this.priority = priority;
        this.requirements = requirements;
        this.allowedResourceTypes = allowedResourceTypes;
    }

    public boolean allowsResourceType(String resourceType) {
        if (allowedResourceTypes == null || allowedResourceTypes.isEmpty()) {
            return true;
        }

        return allowedResourceTypes.contains(resourceType);
    }

    public int getRequiredCapacity(String capacityName) {
        if (requirements == null) {
            return 0;
        }

        return requirements.getOrDefault(capacityName, 0);
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

    public Map<String, Integer> getRequirements() {
        return requirements;
    }

    public List<String> getAllowedResourceTypes() {
        return allowedResourceTypes;
    }
}