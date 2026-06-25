package allocation.algorithm;

import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;

import java.util.List;

public interface AllocationAlgorithm {
    String getName();
    AllocationResult allocate(List<Resource> resources, List<AllocationRequest> requests);
}
