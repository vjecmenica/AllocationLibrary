package allocation.algorithm;

import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;

import java.util.List;

public class CpSatAllocationAlgorithm implements AllocationAlgorithm {
    @Override
    public String getName() {
        return "CpSat Allocation Algorithm";
    }

    @Override
    public AllocationResult allocate(List<Resource> resources, List<AllocationRequest> requests) {
        return null;
    }
}
