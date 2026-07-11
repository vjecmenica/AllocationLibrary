package allocation.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceRequirementTest {

    @Test
    void isSatisfiedByReturnsTrueForMatchingTypeAndCapacity() {
        Resource room = roomWithCapacity(80);
        ResourceRequirement requirement = new ResourceRequirement("ROOM", 1, Map.of("people", 60));

        assertTrue(requirement.isSatisfiedBy(room));
    }

    @Test
    void isSatisfiedByReturnsFalseForWrongType() {
        Resource staff = new Resource("S1", "Assistant", "STAFF", Map.of(), List.of(wholeDay()));
        ResourceRequirement requirement = new ResourceRequirement("ROOM", 1, Map.of("people", 30));

        assertFalse(requirement.isSatisfiedBy(staff));
    }

    @Test
    void isSatisfiedByReturnsFalseForInsufficientCapacity() {
        Resource room = roomWithCapacity(30);
        ResourceRequirement requirement = new ResourceRequirement("ROOM", 1, Map.of("people", 60));

        assertFalse(requirement.isSatisfiedBy(room));
    }

    @Test
    void isSatisfiedByReturnsFalseForMissingCapacityDimension() {
        Resource room = new Resource("R1", "Room", "ROOM", Map.of("people", 80), List.of(wholeDay()));
        ResourceRequirement requirement = new ResourceRequirement("ROOM", 1, Map.of("computers", 20));

        assertFalse(requirement.isSatisfiedBy(room));
    }

    private Resource roomWithCapacity(int capacity) {
        return new Resource("R1", "Room", "ROOM", Map.of("people", capacity), List.of(wholeDay()));
    }

    private TimeWindow wholeDay() {
        return new TimeWindow(
                LocalDateTime.of(2026, 7, 1, 8, 0),
                LocalDateTime.of(2026, 7, 1, 18, 0)
        );
    }
}
