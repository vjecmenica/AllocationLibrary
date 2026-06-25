package allocation.algorithm;

import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;

import java.util.List;

public class BacktrackingAllocationAlgorithm implements AllocationAlgorithm {
    @Override
    public String getName() {
        return "Backtracking Allocation Algorithm";
    }

    @Override
    public AllocationResult allocate(List<Resource> resources, List<AllocationRequest> requests) {
        return null;
    }
}
