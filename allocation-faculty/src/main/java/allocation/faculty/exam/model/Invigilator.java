package allocation.faculty.exam.model;

import allocation.model.TimeWindow;

import java.util.List;

/**
 * An invigilator who can supervise at most one overlapping exam.
 */
public final class Invigilator {

    private final String id;
    private final String name;
    private final List<TimeWindow> availability;

    public Invigilator(String id, String name, List<TimeWindow> availability) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Invigilator ID must not be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Invigilator name must not be blank.");
        }

        List<TimeWindow> providedAvailability = availability == null ? List.of() : availability;
        if (providedAvailability.stream().anyMatch(window -> window == null)) {
            throw new IllegalArgumentException("Invigilator availability must not contain null elements.");
        }
        List<TimeWindow> safeAvailability = List.copyOf(providedAvailability);

        this.id = id;
        this.name = name;
        this.availability = safeAvailability;
    }

    public boolean isAvailableFor(TimeWindow examWindow) {
        return availability.stream().anyMatch(window -> window.contains(examWindow));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<TimeWindow> getAvailability() {
        return availability;
    }
}
