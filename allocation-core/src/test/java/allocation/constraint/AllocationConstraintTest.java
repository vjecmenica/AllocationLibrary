package allocation.constraint;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationConstraintTest {

    @Test
    void resourceTypeConstraintAcceptsMatchingType() {
        ResourceTypeConstraint constraint = new ResourceTypeConstraint();

        ConstraintResult result = constraint.validate(
                requestAt(10),
                new ResourceRequirement("ROOM", 1, Map.of()),
                room("R1", 40),
                List.of()
        );

        assertTrue(result.isSatisfied());
    }

    @Test
    void resourceTypeConstraintRejectsWrongType() {
        ResourceTypeConstraint constraint = new ResourceTypeConstraint();

        ConstraintResult result = constraint.validate(
                requestAt(10),
                new ResourceRequirement("STAFF", 1, Map.of()),
                room("R1", 40),
                List.of()
        );

        assertFalse(result.isSatisfied());
    }

    @Test
    void capacityConstraintAcceptsEnoughCapacity() {
        CapacityConstraint constraint = new CapacityConstraint();

        ConstraintResult result = constraint.validate(
                requestAt(10),
                new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                room("R1", 40),
                List.of()
        );

        assertTrue(result.isSatisfied());
    }

    @Test
    void capacityConstraintRejectsInsufficientCapacity() {
        CapacityConstraint constraint = new CapacityConstraint();

        ConstraintResult result = constraint.validate(
                requestAt(10),
                new ResourceRequirement("ROOM", 1, Map.of("people", 60)),
                room("R1", 40),
                List.of()
        );

        assertFalse(result.isSatisfied());
    }

    @Test
    void availabilityConstraintAcceptsAvailableResource() {
        AvailabilityConstraint constraint = new AvailabilityConstraint();

        ConstraintResult result = constraint.validate(
                requestAt(10),
                new ResourceRequirement("ROOM", 1, Map.of()),
                room("R1", 40),
                List.of()
        );

        assertTrue(result.isSatisfied());
    }

    @Test
    void availabilityConstraintRejectsUnavailableResource() {
        AvailabilityConstraint constraint = new AvailabilityConstraint();
        Resource resource = new Resource(
                "R1",
                "Room",
                "ROOM",
                Map.of("people", 40),
                List.of(new TimeWindow(
                        LocalDateTime.of(2026, 7, 1, 13, 0),
                        LocalDateTime.of(2026, 7, 1, 16, 0)
                ))
        );

        ConstraintResult result = constraint.validate(
                requestAt(10),
                new ResourceRequirement("ROOM", 1, Map.of()),
                resource,
                List.of()
        );

        assertFalse(result.isSatisfied());
    }

    @Test
    void timeConflictConstraintAcceptsNonOverlappingUseOfSameResource() {
        TimeConflictConstraint constraint = new TimeConflictConstraint();
        Resource room = room("R1", 40);
        Allocation existingAllocation = new Allocation(requestAt(10), List.of(room));

        ConstraintResult result = constraint.validate(
                requestAt(12),
                new ResourceRequirement("ROOM", 1, Map.of()),
                room,
                List.of(existingAllocation)
        );

        assertTrue(result.isSatisfied());
    }

    @Test
    void timeConflictConstraintRejectsOverlappingUseOfSameResource() {
        TimeConflictConstraint constraint = new TimeConflictConstraint();
        Resource room = room("R1", 40);
        Allocation existingAllocation = new Allocation(requestAt(10), List.of(room));

        ConstraintResult result = constraint.validate(
                requestAt(11),
                new ResourceRequirement("ROOM", 1, Map.of()),
                room,
                List.of(existingAllocation)
        );

        assertFalse(result.isSatisfied());
    }

    private AllocationRequest requestAt(int hour) {
        return new AllocationRequest(
                "REQ_" + hour,
                "Request " + hour,
                LocalDateTime.of(2026, 7, 1, hour, 0),
                120,
                1,
                List.of(new ResourceRequirement("ROOM", 1, Map.of("people", 30)))
        );
    }

    private Resource room(String id, int capacity) {
        return new Resource(
                id,
                "Room " + id,
                "ROOM",
                Map.of("people", capacity),
                List.of(new TimeWindow(
                        LocalDateTime.of(2026, 7, 1, 8, 0),
                        LocalDateTime.of(2026, 7, 1, 18, 0)
                ))
        );
    }
}
