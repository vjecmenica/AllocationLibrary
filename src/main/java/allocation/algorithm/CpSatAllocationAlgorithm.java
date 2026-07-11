package allocation.algorithm;

import allocation.model.Allocation;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.AllocationStatistics;
import allocation.model.RejectedRequest;
import allocation.model.Resource;
import allocation.model.ResourceRequirement;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CpSatAllocationAlgorithm implements AllocationAlgorithm {

    private static final double DEFAULT_MAX_TIME_IN_SECONDS = 5.0;

    private double maxTimeInSeconds;

    public CpSatAllocationAlgorithm() {
        this(DEFAULT_MAX_TIME_IN_SECONDS);
    }

    public CpSatAllocationAlgorithm(double maxTimeInSeconds) {
        if (maxTimeInSeconds <= 0) {
            throw new IllegalArgumentException("Vremenski limit mora biti pozitivan.");
        }

        this.maxTimeInSeconds = maxTimeInSeconds;
    }

    @Override
    public String getName() {
        return "CP-SAT";
    }

    @Override
    public AllocationResult allocate(List<Resource> resources, List<AllocationRequest> requests) {
        long startTime = System.currentTimeMillis();

        Loader.loadNativeLibraries();

        List<AllocationRequest> sortedRequests = new ArrayList<>(requests);

        sortedRequests.sort(
                Comparator.comparingInt(AllocationRequest::getPriority)
                        .reversed()
                        .thenComparing(request -> request.getTimeWindow().getStart())
        );

        CpModel model = new CpModel();

        BoolVar[] accepted = createAcceptedVariables(model, sortedRequests);
        BoolVar[][][] assignments = createAssignmentVariables(model, sortedRequests, resources);

        addEligibilityConstraints(model, sortedRequests, resources, assignments);
        addRequirementQuantityConstraints(model, sortedRequests, accepted, assignments);
        addSameRequestResourceConstraints(model, sortedRequests, resources, assignments);
        addTimeConflictConstraints(model, sortedRequests, resources, assignments);
        addObjective(model, sortedRequests, accepted);

        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(maxTimeInSeconds);
        solver.getParameters().setNumSearchWorkers(1);

        CpSolverStatus status = solver.solve(model);

        List<Allocation> allocations = buildAllocations(
                sortedRequests,
                resources,
                accepted,
                assignments,
                solver,
                status
        );

        List<RejectedRequest> rejectedRequests = buildRejectedRequests(
                sortedRequests,
                allocations,
                status
        );

        long endTime = System.currentTimeMillis();

        int totalPriorityScore = calculateTotalPriorityScore(allocations);
        double objectiveValue = getObjectiveValue(solver, status);
        boolean stoppedByLimit = status == CpSolverStatus.FEASIBLE
                || status == CpSolverStatus.UNKNOWN;

        AllocationStatistics statistics = new AllocationStatistics(
                requests.size(),
                allocations.size(),
                rejectedRequests.size(),
                endTime - startTime,
                totalPriorityScore,
                0,
                stoppedByLimit,
                status.name(),
                objectiveValue
        );

        return new AllocationResult(allocations, rejectedRequests, statistics);
    }

    private BoolVar[] createAcceptedVariables(
            CpModel model,
            List<AllocationRequest> requests
    ) {
        BoolVar[] accepted = new BoolVar[requests.size()];

        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            accepted[requestIndex] = model.newBoolVar(
                    "accepted_" + requests.get(requestIndex).getId()
            );
        }

        return accepted;
    }

    private BoolVar[][][] createAssignmentVariables(
            CpModel model,
            List<AllocationRequest> requests,
            List<Resource> resources
    ) {
        BoolVar[][][] assignments = new BoolVar[requests.size()][][];

        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            AllocationRequest request = requests.get(requestIndex);
            assignments[requestIndex] = new BoolVar[request.getResourceRequirements().size()][resources.size()];

            for (int requirementIndex = 0; requirementIndex < request.getResourceRequirements().size(); requirementIndex++) {
                for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                    assignments[requestIndex][requirementIndex][resourceIndex] =
                            model.newBoolVar(
                                    "x_"
                                            + request.getId()
                                            + "_req_"
                                            + requirementIndex
                                            + "_res_"
                                            + resources.get(resourceIndex).getId()
                            );
                }
            }
        }

        return assignments;
    }

    private void addEligibilityConstraints(
            CpModel model,
            List<AllocationRequest> requests,
            List<Resource> resources,
            BoolVar[][][] assignments
    ) {
        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            AllocationRequest request = requests.get(requestIndex);

            for (int requirementIndex = 0; requirementIndex < request.getResourceRequirements().size(); requirementIndex++) {
                ResourceRequirement requirement = request.getResourceRequirements().get(requirementIndex);

                for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                    Resource resource = resources.get(resourceIndex);

                    if (!canResourceSatisfyRequirement(request, requirement, resource)) {
                        model.addEquality(assignments[requestIndex][requirementIndex][resourceIndex], 0);
                    }
                }
            }
        }
    }

    private void addRequirementQuantityConstraints(
            CpModel model,
            List<AllocationRequest> requests,
            BoolVar[] accepted,
            BoolVar[][][] assignments
    ) {
        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            AllocationRequest request = requests.get(requestIndex);

            for (int requirementIndex = 0; requirementIndex < request.getResourceRequirements().size(); requirementIndex++) {
                ResourceRequirement requirement = request.getResourceRequirements().get(requirementIndex);
                LinearArgument[] variables = assignments[requestIndex][requirementIndex];

                model.addEquality(
                        LinearExpr.sum(variables),
                        LinearExpr.term(accepted[requestIndex], requirement.getQuantity())
                );
            }
        }
    }

    private void addSameRequestResourceConstraints(
            CpModel model,
            List<AllocationRequest> requests,
            List<Resource> resources,
            BoolVar[][][] assignments
    ) {
        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            AllocationRequest request = requests.get(requestIndex);

            for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                List<LinearArgument> resourceAssignments = new ArrayList<>();

                for (int requirementIndex = 0; requirementIndex < request.getResourceRequirements().size(); requirementIndex++) {
                    resourceAssignments.add(assignments[requestIndex][requirementIndex][resourceIndex]);
                }

                model.addLessOrEqual(
                        LinearExpr.sum(toArray(resourceAssignments)),
                        1
                );
            }
        }
    }

    private void addTimeConflictConstraints(
            CpModel model,
            List<AllocationRequest> requests,
            List<Resource> resources,
            BoolVar[][][] assignments
    ) {
        for (int firstRequestIndex = 0; firstRequestIndex < requests.size(); firstRequestIndex++) {
            AllocationRequest firstRequest = requests.get(firstRequestIndex);

            for (int secondRequestIndex = firstRequestIndex + 1; secondRequestIndex < requests.size(); secondRequestIndex++) {
                AllocationRequest secondRequest = requests.get(secondRequestIndex);

                if (!firstRequest.getTimeWindow().overlaps(secondRequest.getTimeWindow())) {
                    continue;
                }

                for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                    List<LinearArgument> conflictingAssignments = new ArrayList<>();

                    addResourceAssignmentsForRequest(
                            conflictingAssignments,
                            assignments,
                            firstRequestIndex,
                            firstRequest,
                            resourceIndex
                    );

                    addResourceAssignmentsForRequest(
                            conflictingAssignments,
                            assignments,
                            secondRequestIndex,
                            secondRequest,
                            resourceIndex
                    );

                    model.addLessOrEqual(
                            LinearExpr.sum(toArray(conflictingAssignments)),
                            1
                    );
                }
            }
        }
    }

    private void addObjective(
            CpModel model,
            List<AllocationRequest> requests,
            BoolVar[] accepted
    ) {
        LinearArgument[] variables = new LinearArgument[accepted.length];
        long[] weights = new long[accepted.length];

        long priorityMultiplier = requests.size() + 1L;

        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            variables[requestIndex] = accepted[requestIndex];
            weights[requestIndex] = requests.get(requestIndex).getPriority() * priorityMultiplier + 1;
        }

        model.maximize(LinearExpr.weightedSum(variables, weights));
    }

    private boolean canResourceSatisfyRequirement(
            AllocationRequest request,
            ResourceRequirement requirement,
            Resource resource
    ) {
        return requirement.isSatisfiedBy(resource)
                && resource.isAvailableFor(request.getTimeWindow());
    }

    private void addResourceAssignmentsForRequest(
            List<LinearArgument> target,
            BoolVar[][][] assignments,
            int requestIndex,
            AllocationRequest request,
            int resourceIndex
    ) {
        for (int requirementIndex = 0; requirementIndex < request.getResourceRequirements().size(); requirementIndex++) {
            target.add(assignments[requestIndex][requirementIndex][resourceIndex]);
        }
    }

    private List<Allocation> buildAllocations(
            List<AllocationRequest> requests,
            List<Resource> resources,
            BoolVar[] accepted,
            BoolVar[][][] assignments,
            CpSolver solver,
            CpSolverStatus status
    ) {
        List<Allocation> allocations = new ArrayList<>();

        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return allocations;
        }

        for (int requestIndex = 0; requestIndex < requests.size(); requestIndex++) {
            if (solver.value(accepted[requestIndex]) != 1) {
                continue;
            }

            AllocationRequest request = requests.get(requestIndex);
            List<Resource> assignedResources = new ArrayList<>();
            Set<String> assignedResourceIds = new HashSet<>();

            for (int requirementIndex = 0; requirementIndex < request.getResourceRequirements().size(); requirementIndex++) {
                for (int resourceIndex = 0; resourceIndex < resources.size(); resourceIndex++) {
                    if (solver.value(assignments[requestIndex][requirementIndex][resourceIndex]) == 1) {
                        Resource resource = resources.get(resourceIndex);

                        if (assignedResourceIds.add(resource.getId())) {
                            assignedResources.add(resource);
                        }
                    }
                }
            }

            allocations.add(new Allocation(request, assignedResources));
        }

        return allocations;
    }

    private List<RejectedRequest> buildRejectedRequests(
            List<AllocationRequest> requests,
            List<Allocation> allocations,
            CpSolverStatus status
    ) {
        List<RejectedRequest> rejectedRequests = new ArrayList<>();
        Set<String> allocatedRequestIds = new HashSet<>();

        for (Allocation allocation : allocations) {
            allocatedRequestIds.add(allocation.getRequest().getId());
        }

        for (AllocationRequest request : requests) {
            if (!allocatedRequestIds.contains(request.getId())) {
                rejectedRequests.add(
                        new RejectedRequest(
                                request,
                                buildRejectionReason(status)
                        )
                );
            }
        }

        return rejectedRequests;
    }

    private String buildRejectionReason(CpSolverStatus status) {
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return "CP-SAT solver nije pronašao izvodljivo rešenje.";
        }

        return "Zahtev nije alociran u optimalnom CP-SAT rešenju.";
    }

    private int calculateTotalPriorityScore(List<Allocation> allocations) {
        int sum = 0;

        for (Allocation allocation : allocations) {
            sum += allocation.getRequest().getPriority();
        }

        return sum;
    }

    private double getObjectiveValue(CpSolver solver, CpSolverStatus status) {
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            return solver.objectiveValue();
        }

        return 0;
    }

    private LinearArgument[] toArray(List<LinearArgument> variables) {
        return variables.toArray(new LinearArgument[0]);
    }
}
