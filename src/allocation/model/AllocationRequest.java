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

        if (resourceRequirements == null || resourceRequirements.isEmpty()) {
            throw new IllegalArgumentException("Zahtev mora imati bar jednu potrebu za resursima.");
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