package allocation.faculty.exam.model;

import allocation.model.TimeWindow;

import java.util.List;

/**
 * A faculty room that can host one exam at a time.
 */
public final class FacultyRoom {

    private final String id;
    private final String name;
    private final int capacity;
    private final List<TimeWindow> availability;

    public FacultyRoom(String id, String name, int capacity, List<TimeWindow> availability) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Room ID must not be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Room capacity must be positive.");
        }

        List<TimeWindow> providedAvailability = availability == null ? List.of() : availability;
        if (providedAvailability.stream().anyMatch(window -> window == null)) {
            throw new IllegalArgumentException("Room availability must not contain null elements.");
        }
        List<TimeWindow> safeAvailability = List.copyOf(providedAvailability);

        this.id = id;
        this.name = name;
        this.capacity = capacity;
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

    public int getCapacity() {
        return capacity;
    }

    public List<TimeWindow> getAvailability() {
        return availability;
    }
}
