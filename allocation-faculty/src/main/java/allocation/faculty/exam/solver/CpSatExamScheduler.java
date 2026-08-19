package allocation.faculty.exam.solver;

import allocation.faculty.exam.model.Exam;
import allocation.faculty.exam.model.ExamAssignment;
import allocation.faculty.exam.model.ExamScheduleRequest;
import allocation.faculty.exam.model.ExamScheduleResult;
import allocation.faculty.exam.model.ExamScheduleStatistics;
import allocation.faculty.exam.model.ExamSlot;
import allocation.faculty.exam.model.FacultyRoom;
import allocation.faculty.exam.model.Invigilator;
import allocation.faculty.exam.model.UnscheduledExam;
import allocation.model.TimeWindow;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CP-SAT scheduler for discrete faculty exam slots and faculty resources.
 */
public final class CpSatExamScheduler {

    public static final double DEFAULT_MAX_TIME_IN_SECONDS = 5.0;

    private static final String UNSCHEDULED_REASON =
            "No permitted combination of slot, room, and invigilators was selected under the current constraints.";
    private static final String NO_SOLUTION_REASON =
            "The solver did not produce a feasible exam schedule.";

    private final double maxTimeInSeconds;

    public CpSatExamScheduler() {
        this(DEFAULT_MAX_TIME_IN_SECONDS);
    }

    public CpSatExamScheduler(double maxTimeInSeconds) {
        if (!Double.isFinite(maxTimeInSeconds) || maxTimeInSeconds <= 0) {
            throw new IllegalArgumentException("CP-SAT time limit must be positive and finite.");
        }
        this.maxTimeInSeconds = maxTimeInSeconds;
    }

    public ExamScheduleResult schedule(ExamScheduleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Exam schedule request must not be null.");
        }

        long startedAt = System.nanoTime();
        Loader.loadNativeLibraries();

        List<Exam> exams = request.getExams();
        List<ExamSlot> slots = request.getSlots();
        List<FacultyRoom> rooms = request.getRooms();
        List<Invigilator> invigilators = request.getInvigilators();

        CpModel model = new CpModel();
        TimeWindow[][] actualWindows = createActualWindows(exams, slots);
        ModelVariables variables = createVariables(model, exams, slots, rooms, invigilators);

        addExamSchedulingConstraints(model, variables, exams, slots, actualWindows);
        addRoomConstraints(model, variables, exams, slots, rooms, actualWindows);
        addInvigilatorConstraints(model, variables, exams, slots, invigilators, actualWindows);
        addConflictConstraints(model, variables, exams, slots, rooms, invigilators, actualWindows);
        addObjective(model, variables.scheduled());

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(maxTimeInSeconds);
        solver.getParameters().setNumSearchWorkers(1);

        CpSolverStatus status = solver.solve(model);
        boolean hasSolution = status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE;

        List<ExamAssignment> assignments = hasSolution
                ? buildAssignments(exams, slots, rooms, invigilators, variables, solver)
                : List.of();
        List<UnscheduledExam> unscheduledExams = buildUnscheduledExams(exams, assignments, hasSolution);

        long executionTimeMs = (System.nanoTime() - startedAt) / 1_000_000L;
        ExamScheduleStatistics statistics = new ExamScheduleStatistics(
                exams.size(),
                assignments.size(),
                unscheduledExams.size(),
                status.name(),
                executionTimeMs,
                status == CpSolverStatus.FEASIBLE || status == CpSolverStatus.UNKNOWN
        );

        return new ExamScheduleResult(assignments, unscheduledExams, statistics);
    }

    private TimeWindow[][] createActualWindows(List<Exam> exams, List<ExamSlot> slots) {
        TimeWindow[][] windows = new TimeWindow[exams.size()][slots.size()];
        for (int examIndex = 0; examIndex < exams.size(); examIndex++) {
            Exam exam = exams.get(examIndex);
            for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                ExamSlot slot = slots.get(slotIndex);
                if (slot.canFit(exam.getDurationMinutes())) {
                    windows[examIndex][slotIndex] = slot.examTimeWindow(exam.getDurationMinutes());
                }
            }
        }
        return windows;
    }

    private ModelVariables createVariables(
            CpModel model,
            List<Exam> exams,
            List<ExamSlot> slots,
            List<FacultyRoom> rooms,
            List<Invigilator> invigilators
    ) {
        BoolVar[] scheduled = new BoolVar[exams.size()];
        BoolVar[][] examAtSlot = new BoolVar[exams.size()][slots.size()];
        BoolVar[][][] roomAssignments = new BoolVar[exams.size()][slots.size()][rooms.size()];
        BoolVar[][][] invigilatorAssignments =
                new BoolVar[exams.size()][slots.size()][invigilators.size()];

        for (int examIndex = 0; examIndex < exams.size(); examIndex++) {
            scheduled[examIndex] = model.newBoolVar("scheduled_" + examIndex);
            for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                examAtSlot[examIndex][slotIndex] = model.newBoolVar(
                        "exam_" + examIndex + "_slot_" + slotIndex
                );
                for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
                    roomAssignments[examIndex][slotIndex][roomIndex] = model.newBoolVar(
                            "exam_" + examIndex + "_slot_" + slotIndex + "_room_" + roomIndex
                    );
                }
                for (int invigilatorIndex = 0; invigilatorIndex < invigilators.size(); invigilatorIndex++) {
                    invigilatorAssignments[examIndex][slotIndex][invigilatorIndex] = model.newBoolVar(
                            "exam_" + examIndex + "_slot_" + slotIndex + "_inv_" + invigilatorIndex
                    );
                }
            }
        }

        return new ModelVariables(scheduled, examAtSlot, roomAssignments, invigilatorAssignments);
    }

    private void addExamSchedulingConstraints(
            CpModel model,
            ModelVariables variables,
            List<Exam> exams,
            List<ExamSlot> slots,
            TimeWindow[][] actualWindows
    ) {
        for (int examIndex = 0; examIndex < exams.size(); examIndex++) {
            if (slots.isEmpty()) {
                model.addEquality(variables.scheduled()[examIndex], 0);
                continue;
            }

            model.addEquality(
                    LinearExpr.sum(variables.examAtSlot()[examIndex]),
                    variables.scheduled()[examIndex]
            );
            for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                if (actualWindows[examIndex][slotIndex] == null) {
                    model.addEquality(variables.examAtSlot()[examIndex][slotIndex], 0);
                }
            }
        }
    }

    private void addRoomConstraints(
            CpModel model,
            ModelVariables variables,
            List<Exam> exams,
            List<ExamSlot> slots,
            List<FacultyRoom> rooms,
            TimeWindow[][] actualWindows
    ) {
        for (int examIndex = 0; examIndex < exams.size(); examIndex++) {
            Exam exam = exams.get(examIndex);
            for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                BoolVar[] assignments = variables.roomAssignments()[examIndex][slotIndex];
                TimeWindow actualWindow = actualWindows[examIndex][slotIndex];

                if (rooms.isEmpty()) {
                    model.addEquality(variables.examAtSlot()[examIndex][slotIndex], 0);
                    continue;
                }

                for (int roomIndex = 0; roomIndex < rooms.size(); roomIndex++) {
                    FacultyRoom room = rooms.get(roomIndex);
                    boolean eligible = actualWindow != null
                            && room.getCapacity() >= exam.getStudentCount()
                            && room.isAvailableFor(actualWindow);
                    if (!eligible) {
                        model.addEquality(assignments[roomIndex], 0);
                    }
                }

                model.addEquality(
                        LinearExpr.sum(assignments),
                        variables.examAtSlot()[examIndex][slotIndex]
                );
            }
        }
    }

    private void addInvigilatorConstraints(
            CpModel model,
            ModelVariables variables,
            List<Exam> exams,
            List<ExamSlot> slots,
            List<Invigilator> invigilators,
            TimeWindow[][] actualWindows
    ) {
        for (int examIndex = 0; examIndex < exams.size(); examIndex++) {
            Exam exam = exams.get(examIndex);
            for (int slotIndex = 0; slotIndex < slots.size(); slotIndex++) {
                BoolVar[] assignments = variables.invigilatorAssignments()[examIndex][slotIndex];
                TimeWindow actualWindow = actualWindows[examIndex][slotIndex];

                for (int invigilatorIndex = 0; invigilatorIndex < invigilators.size(); invigilatorIndex++) {
                    Invigilator invigilator = invigilators.get(invigilatorIndex);
                    if (actualWindow == null || !invigilator.isAvailableFor(actualWindow)) {
                        model.addEquality(assignments[invigilatorIndex], 0);
                    }
                }

                if (exam.getRequiredInvigilators() == 0) {
                    for (BoolVar assignment : assignments) {
                        model.addEquality(assignment, 0);
                    }
                } else if (invigilators.isEmpty()) {
                    model.addEquality(variables.examAtSlot()[examIndex][slotIndex], 0);
                } else {
                    model.addEquality(
                            LinearExpr.sum(assignments),
                            LinearExpr.term(
                                    variables.examAtSlot()[examIndex][slotIndex],
                                    exam.getRequiredInvigilators()
                            )
                    );
                }
            }
        }
    }

    private void addConflictConstraints(
            CpModel model,
            ModelVariables variables,
            List<Exam> exams,
            List<ExamSlot> slots,
            List<FacultyRoom> rooms,
            List<Invigilator> invigilators,
            TimeWindow[][] actualWindows
    ) {
        for (int firstExamIndex = 0; firstExamIndex < exams.size(); firstExamIndex++) {
            for (int secondExamIndex = firstExamIndex + 1; secondExamIndex < exams.size(); secondExamIndex++) {
                boolean sharedStudentGroup = shareStudentGroup(
                        exams.get(firstExamIndex),
                        exams.get(secondExamIndex)
                );

                for (int firstSlotIndex = 0; firstSlotIndex < slots.size(); firstSlotIndex++) {
                    for (int secondSlotIndex = 0; secondSlotIndex < slots.size(); secondSlotIndex++) {
                        if (!overlap(
                                actualWindows[firstExamIndex][firstSlotIndex],
                                actualWindows[secondExamIndex][secondSlotIndex]
                        )) {
                            continue;
                        }

                        addSharedRoomConstraints(
                                model,
                                variables,
                                rooms.size(),
                                firstExamIndex,
                                firstSlotIndex,
                                secondExamIndex,
                                secondSlotIndex
                        );
                        addSharedInvigilatorConstraints(
                                model,
                                variables,
                                invigilators.size(),
                                firstExamIndex,
                                firstSlotIndex,
                                secondExamIndex,
                                secondSlotIndex
                        );
                        if (sharedStudentGroup) {
                            model.addLessOrEqual(
                                    LinearExpr.sum(new LinearArgument[]{
                                            variables.examAtSlot()[firstExamIndex][firstSlotIndex],
                                            variables.examAtSlot()[secondExamIndex][secondSlotIndex]
                                    }),
                                    1
                            );
                        }
                    }
                }
            }
        }
    }

    private void addSharedRoomConstraints(
            CpModel model,
            ModelVariables variables,
            int roomCount,
            int firstExamIndex,
            int firstSlotIndex,
            int secondExamIndex,
            int secondSlotIndex
    ) {
        for (int roomIndex = 0; roomIndex < roomCount; roomIndex++) {
            model.addLessOrEqual(
                    LinearExpr.sum(new LinearArgument[]{
                            variables.roomAssignments()[firstExamIndex][firstSlotIndex][roomIndex],
                            variables.roomAssignments()[secondExamIndex][secondSlotIndex][roomIndex]
                    }),
                    1
            );
        }
    }

    private void addSharedInvigilatorConstraints(
            CpModel model,
            ModelVariables variables,
            int invigilatorCount,
            int firstExamIndex,
            int firstSlotIndex,
            int secondExamIndex,
            int secondSlotIndex
    ) {
        for (int invigilatorIndex = 0; invigilatorIndex < invigilatorCount; invigilatorIndex++) {
            model.addLessOrEqual(
                    LinearExpr.sum(new LinearArgument[]{
                            variables.invigilatorAssignments()[firstExamIndex][firstSlotIndex][invigilatorIndex],
                            variables.invigilatorAssignments()[secondExamIndex][secondSlotIndex][invigilatorIndex]
                    }),
                    1
            );
        }
    }

    private boolean overlap(TimeWindow first, TimeWindow second) {
        return first != null && second != null && first.overlaps(second);
    }

    private boolean shareStudentGroup(Exam first, Exam second) {
        Set<String> firstGroups = new HashSet<>(first.getStudentGroups());
        return second.getStudentGroups().stream().anyMatch(firstGroups::contains);
    }

    private void addObjective(CpModel model, BoolVar[] scheduled) {
        if (scheduled.length > 0) {
            model.maximize(LinearExpr.sum(scheduled));
        }
    }

    private List<ExamAssignment> buildAssignments(
            List<Exam> exams,
            List<ExamSlot> slots,
            List<FacultyRoom> rooms,
            List<Invigilator> invigilators,
            ModelVariables variables,
            CpSolver solver
    ) {
        List<ExamAssignment> assignments = new ArrayList<>();
        for (int examIndex = 0; examIndex < exams.size(); examIndex++) {
            if (solver.value(variables.scheduled()[examIndex]) != 1) {
                continue;
            }

            int selectedSlotIndex = selectedIndex(variables.examAtSlot()[examIndex], solver);
            int selectedRoomIndex = selectedIndex(
                    variables.roomAssignments()[examIndex][selectedSlotIndex],
                    solver
            );
            List<Invigilator> selectedInvigilators = new ArrayList<>();
            BoolVar[] invigilatorVariables =
                    variables.invigilatorAssignments()[examIndex][selectedSlotIndex];
            for (int invigilatorIndex = 0; invigilatorIndex < invigilatorVariables.length; invigilatorIndex++) {
                if (solver.value(invigilatorVariables[invigilatorIndex]) == 1) {
                    selectedInvigilators.add(invigilators.get(invigilatorIndex));
                }
            }

            assignments.add(new ExamAssignment(
                    exams.get(examIndex),
                    slots.get(selectedSlotIndex),
                    rooms.get(selectedRoomIndex),
                    selectedInvigilators
            ));
        }
        return assignments;
    }

    private int selectedIndex(BoolVar[] variables, CpSolver solver) {
        for (int index = 0; index < variables.length; index++) {
            if (solver.value(variables[index]) == 1) {
                return index;
            }
        }
        throw new IllegalStateException("CP-SAT returned an incomplete exam assignment.");
    }

    private List<UnscheduledExam> buildUnscheduledExams(
            List<Exam> exams,
            List<ExamAssignment> assignments,
            boolean hasSolution
    ) {
        Set<String> scheduledExamIds = new HashSet<>();
        assignments.forEach(assignment -> scheduledExamIds.add(assignment.getExam().getId()));

        String reason = hasSolution ? UNSCHEDULED_REASON : NO_SOLUTION_REASON;
        return exams.stream()
                .filter(exam -> !scheduledExamIds.contains(exam.getId()))
                .map(exam -> new UnscheduledExam(exam, reason))
                .toList();
    }

    private record ModelVariables(
            BoolVar[] scheduled,
            BoolVar[][] examAtSlot,
            BoolVar[][][] roomAssignments,
            BoolVar[][][] invigilatorAssignments
    ) {
    }
}
