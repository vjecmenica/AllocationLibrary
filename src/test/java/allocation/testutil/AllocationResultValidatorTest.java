package allocation.testutil;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import allocation.model.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AllocationResultValidatorTest {

    @Test
    void validatorRejectsUsingOneResourceForTwoRequirementsOfSameRequest() {
        TimeWindow wholeDay = new TimeWindow(
                LocalDateTime.of(2026, 7, 1, 8, 0),
                LocalDateTime.of(2026, 7, 1, 18, 0)
        );

        Resource largeRoom = new Resource(
                "R_BIG",
                "Large room",
                "ROOM",
                Map.of("people", 120),
                List.of(wholeDay)
        );
        Resource staff = new Resource(
                "S1",
                "Staff",
                "STAFF",
                Map.of(),
                List.of(wholeDay)
        );

        AllocationRequest request = new AllocationRequest(
                "REQ_DOUBLE_ROOM",
                "Double room request",
                LocalDateTime.of(2026, 7, 1, 10, 0),
                120,
                10,
                List.of(
                        new ResourceRequirement("ROOM", 1, Map.of("people", 30)),
                        new ResourceRequirement("ROOM", 1, Map.of("people", 100))
                )
        );

        AllocationResult invalidResult = new AllocationResult(
                List.of(new Allocation(request, List.of(largeRoom, staff))),
                List.of(),
                new AllocationStatistics(1, 1, 0, 0, 10)
        );

        assertThrows(
                AssertionError.class,
                () -> AllocationResultValidator.assertValid(
                        invalidResult,
                        List.of(largeRoom, staff),
                        List.of(request)
                )
        );
    }
}
