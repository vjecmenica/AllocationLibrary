package allocation.model;

import java.util.List;
import java.util.Map;

public class Resource {

    private String id;
    private String name;
    private String type;
    private Map<String, Integer> capacities;
    private List<TimeWindow> availability;

    public Resource(
            String id,
            String name,
            String type,
            Map<String, Integer> capacities,
            List<TimeWindow> availability
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID resursa ne sme biti prazan.");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Naziv resursa ne sme biti prazan.");
        }

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Tip resursa ne sme biti prazan.");
        }

        this.id = id;
        this.name = name;
        this.type = type;
        this.capacities = capacities;
        this.availability = availability;
    }

    public boolean isAvailableFor(TimeWindow requestedTime) {
        if (availability == null || availability.isEmpty()) {
            return false;
        }

        for (TimeWindow window : availability) {
            if (window.contains(requestedTime)) {
                return true;
            }
        }

        return false;
    }

    public int getCapacity(String capacityName) {
        if (capacities == null) {
            return 0;
        }

        return capacities.getOrDefault(capacityName, 0);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Map<String, Integer> getCapacities() {
        return capacities;
    }

    public List<TimeWindow> getAvailability() {
        return availability;
    }
}