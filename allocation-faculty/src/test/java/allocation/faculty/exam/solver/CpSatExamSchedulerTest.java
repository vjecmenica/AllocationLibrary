package allocation.faculty.exam.solver;

import allocation.faculty.exam.model.Exam;
import allocation.faculty.exam.model.ExamAssignment;
import allocation.faculty.exam.model.ExamScheduleRequest;
import allocation.faculty.exam.model.ExamScheduleResult;
import allocation.faculty.exam.model.ExamSlot;
import allocation.faculty.exam.model.FacultyRoom;
import allocation.faculty.exam.model.Invigilator;
import allocation.model.TimeWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpSatExamSchedulerTest {

    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 7, 1, 8, 0);
    private static final LocalDateTime DAY_END = LocalDateTime.of(2026, 7, 1, 18, 0);

    private final CpSatExamScheduler scheduler = new CpSatExamScheduler(2.0);

    @Test
    void schedulesSingleExamWithValidSlotAndRoom() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 90, 0, "SI2")),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals(1, result.getAssignments().size());
        assertEquals(0, result.getUnscheduledExams().size());
        assertEquals("OPTIMAL", result.getStatistics().getSolverStatus());
        assertFalse(result.getStatistics().isStoppedByLimit());
    }

    @Test
    void schedulerSelectsAnEligibleSlotInsteadOfReceivingAFixedExamStart() {
        FacultyRoom room = new FacultyRoom(
                "R1",
                "Room R1",
                50,
                List.of(window(12, 14))
        );

        ExamScheduleResult result = schedule(
                List.of(slot("MORNING", 10, 12), slot("AFTERNOON", 12, 14)),
                List.of(exam("E1", 30, 90, 0)),
                List.of(room),
                List.of()
        );

        assertEquals("AFTERNOON", result.getAssignments().get(0).getSlot().getId());
    }

    @Test
    void scheduledExamStartsAtSelectedSlotStart() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 60, 0)),
                List.of(room("R1", 50)),
                List.of()
        );

        ExamAssignment assignment = result.getAssignments().get(0);
        assertEquals(assignment.getSlot().getStart(), assignment.getActualTimeWindow().getStart());
    }

    @Test
    void actualExamEndUsesDurationMinutesInsteadOfSlotEnd() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 13)),
                List.of(exam("E1", 30, 90, 0)),
                List.of(room("R1", 50)),
                List.of()
        );

        ExamAssignment assignment = result.getAssignments().get(0);
        assertEquals(LocalDateTime.of(2026, 7, 1, 11, 30), assignment.getActualTimeWindow().getEnd());
        assertEquals(LocalDateTime.of(2026, 7, 1, 13, 0), assignment.getSlot().getEnd());
    }

    @Test
    void roomWithInsufficientCapacityCannotBeAssigned() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 60, 60, 0)),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals(0, result.getAssignments().size());
        assertEquals(1, result.getUnscheduledExams().size());
    }

    @Test
    void roomMustBeAvailableForTheCompleteActualInterval() {
        FacultyRoom room = new FacultyRoom("R1", "Room R1", 50, List.of(window(10, 11)));

        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 120, 0)),
                List.of(room),
                List.of()
        );

        assertEquals(0, result.getAssignments().size());
    }

    @Test
    void overlappingExamsCannotUseTheSameRoom() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(
                        exam("E1", 30, 120, 0, "G1"),
                        exam("E2", 30, 120, 0, "G2")
                ),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals(1, result.getAssignments().size());
        assertEquals(1, result.getUnscheduledExams().size());
    }

    @Test
    void differentSlotIdsStillConflictWhenActualIntervalsOverlap() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12), slot("S2", 11, 13)),
                List.of(
                        exam("E1", 30, 120, 0, "G1"),
                        exam("E2", 30, 120, 0, "G2")
                ),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals(1, result.getAssignments().size());
    }

    @Test
    void invigilatorMustBeAvailableForTheCompleteActualInterval() {
        Invigilator invigilator = new Invigilator("I1", "Invigilator 1", List.of(window(10, 11)));

        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 120, 1)),
                List.of(room("R1", 50)),
                List.of(invigilator)
        );

        assertEquals(0, result.getAssignments().size());
    }

    @Test
    void oneInvigilatorCannotSuperviseOverlappingExams() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(
                        exam("E1", 30, 120, 1, "G1"),
                        exam("E2", 30, 120, 1, "G2")
                ),
                List.of(room("R1", 50), room("R2", 50)),
                List.of(invigilator("I1"))
        );

        assertEquals(1, result.getAssignments().size());
    }

    @Test
    void assignsExactlyTheRequiredNumberOfInvigilators() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 120, 2)),
                List.of(room("R1", 50)),
                List.of(invigilator("I1"), invigilator("I2"), invigilator("I3"))
        );

        assertEquals(2, result.getAssignments().get(0).getInvigilators().size());
    }

    @Test
    void zeroRequiredInvigilatorsNeedsNoAssignment() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 120, 0)),
                List.of(room("R1", 50)),
                List.of()
        );

        assertTrue(result.getAssignments().get(0).getInvigilators().isEmpty());
    }

    @Test
    void examsSharingAStudentGroupCannotOverlap() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(
                        exam("E1", 30, 120, 0, "SI2", "RTI2"),
                        exam("E2", 30, 120, 0, "RTI2")
                ),
                List.of(room("R1", 50), room("R2", 50)),
                List.of()
        );

        assertEquals(1, result.getAssignments().size());
    }

    @Test
    void examsWithDifferentStudentGroupsMayOverlapWhenResourcesExist() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(
                        exam("E1", 30, 120, 0, "G1"),
                        exam("E2", 30, 120, 0, "G2")
                ),
                List.of(room("R1", 50), room("R2", 50)),
                List.of()
        );

        assertEquals(2, result.getAssignments().size());
        assertNoConflicts(result);
    }

    @Test
    void examLongerThanSlotCannotUseThatSlot() {
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 11)),
                List.of(exam("E1", 30, 90, 0)),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals(0, result.getAssignments().size());
        assertEquals(1, result.getUnscheduledExams().size());
    }

    @Test
    void impossibleExamAppearsInUnscheduledExamsWithAReason() {
        Exam exam = exam("E1", 100, 60, 0);
        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals("E1", result.getUnscheduledExams().get(0).getExam().getId());
        assertFalse(result.getUnscheduledExams().get(0).getReason().isBlank());
    }

    @Test
    void maximizesScheduledExamCountWhenNotAllExamsFit() {
        ExamScheduleResult result = schedule(
                List.of(slot("LONG", 10, 12), slot("FIRST", 10, 11), slot("SECOND", 11, 12)),
                List.of(
                        exam("LONG_EXAM", 30, 120, 0, "G1"),
                        exam("SHORT_1", 30, 60, 0, "G2"),
                        exam("SHORT_2", 30, 60, 0, "G3")
                ),
                List.of(room("R1", 50)),
                List.of()
        );

        assertEquals(2, result.getAssignments().size());
        assertEquals(Set.of("SHORT_1", "SHORT_2"), assignmentIds(result));
    }

    @Test
    void emptyAvailabilityMakesResourceUnavailable() {
        FacultyRoom unavailableRoom = new FacultyRoom("R1", "Room R1", 50, List.of());

        ExamScheduleResult result = schedule(
                List.of(slot("S1", 10, 12)),
                List.of(exam("E1", 30, 60, 0)),
                List.of(unavailableRoom),
                List.of()
        );

        assertEquals(0, result.getAssignments().size());
    }

    @Test
    void invalidDomainDataAndDuplicateIdsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Exam(" ", "CS101", "Algorithms", 30, 60, 0, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FacultyRoom("R1", "Room", 0, List.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new Exam("E1", "CS101", "Algorithms", 30, 60, 0, Arrays.asList("G1", null))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new FacultyRoom("R1", "Room", 30, Arrays.asList(window(10, 12), null))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExamScheduleRequest(
                        List.of(slot("S1", 10, 12), slot("S1", 12, 14)),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    @Test
    void nonPositiveOrNonFiniteSchedulerTimeLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CpSatExamScheduler(0));
        assertThrows(IllegalArgumentException.class, () -> new CpSatExamScheduler(-1));
        assertThrows(IllegalArgumentException.class, () -> new CpSatExamScheduler(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new CpSatExamScheduler(Double.POSITIVE_INFINITY));
    }

    @Test
    void nullRequestIsRejectedBeforeSolverExecution() {
        assertThrows(IllegalArgumentException.class, () -> scheduler.schedule(null));
    }

    private ExamScheduleResult schedule(
            List<ExamSlot> slots,
            List<Exam> exams,
            List<FacultyRoom> rooms,
            List<Invigilator> invigilators
    ) {
        ExamScheduleResult result = scheduler.schedule(
                new ExamScheduleRequest(slots, exams, rooms, invigilators)
        );
        assertNotNull(result);
        assertEquals(exams.size(), result.getStatistics().getTotalExams());
        assertEquals(
                exams.size(),
                result.getStatistics().getScheduledExams()
                        + result.getStatistics().getUnscheduledExams()
        );
        assertNoConflicts(result);
        return result;
    }

    private void assertNoConflicts(ExamScheduleResult result) {
        List<ExamAssignment> assignments = result.getAssignments();
        for (ExamAssignment assignment : assignments) {
            assertTrue(assignment.getRoom().getCapacity() >= assignment.getExam().getStudentCount());
            assertTrue(assignment.getRoom().isAvailableFor(assignment.getActualTimeWindow()));
            assertEquals(
                    assignment.getExam().getRequiredInvigilators(),
                    assignment.getInvigilators().size()
            );
            assignment.getInvigilators().forEach(invigilator ->
                    assertTrue(invigilator.isAvailableFor(assignment.getActualTimeWindow()))
            );
        }

        for (int firstIndex = 0; firstIndex < assignments.size(); firstIndex++) {
            ExamAssignment first = assignments.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < assignments.size(); secondIndex++) {
                ExamAssignment second = assignments.get(secondIndex);
                if (!first.getActualTimeWindow().overlaps(second.getActualTimeWindow())) {
                    continue;
                }

                assertFalse(first.getRoom().getId().equals(second.getRoom().getId()));
                Set<String> firstInvigilatorIds = new HashSet<>();
                first.getInvigilators().forEach(invigilator -> firstInvigilatorIds.add(invigilator.getId()));
                assertTrue(second.getInvigilators().stream()
                        .noneMatch(invigilator -> firstInvigilatorIds.contains(invigilator.getId())));
                assertTrue(first.getExam().getStudentGroups().stream()
                        .noneMatch(second.getExam().getStudentGroups()::contains));
            }
        }
    }

    private Set<String> assignmentIds(ExamScheduleResult result) {
        Set<String> ids = new HashSet<>();
        result.getAssignments().forEach(assignment -> ids.add(assignment.getExam().getId()));
        return ids;
    }

    private ExamSlot slot(String id, int startHour, int endHour) {
        return new ExamSlot(
                id,
                LocalDateTime.of(2026, 7, 1, startHour, 0),
                LocalDateTime.of(2026, 7, 1, endHour, 0)
        );
    }

    private Exam exam(
            String id,
            int studentCount,
            int durationMinutes,
            int requiredInvigilators,
            String... studentGroups
    ) {
        return new Exam(
                id,
                "CODE_" + id,
                "Exam " + id,
                studentCount,
                durationMinutes,
                requiredInvigilators,
                List.of(studentGroups)
        );
    }

    private FacultyRoom room(String id, int capacity) {
        return new FacultyRoom(id, "Room " + id, capacity, List.of(new TimeWindow(DAY_START, DAY_END)));
    }

    private Invigilator invigilator(String id) {
        return new Invigilator(id, "Invigilator " + id, List.of(new TimeWindow(DAY_START, DAY_END)));
    }

    private TimeWindow window(int startHour, int endHour) {
        return new TimeWindow(
                LocalDateTime.of(2026, 7, 1, startHour, 0),
                LocalDateTime.of(2026, 7, 1, endHour, 0)
        );
    }
}
