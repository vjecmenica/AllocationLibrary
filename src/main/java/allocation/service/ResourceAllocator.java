package allocation.service;

import allocation.algorithm.AllocationAlgorithm;
import allocation.algorithm.BacktrackingAllocationAlgorithm;
import allocation.algorithm.CpSatAllocationAlgorithm;
import allocation.algorithm.GreedyAllocationAlgorithm;
import allocation.model.AllocationRequest;
import allocation.model.AllocationResult;
import allocation.model.Resource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main public entry point for running allocation algorithms from the library.
 */
public class ResourceAllocator {

    public AllocationResult allocate(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationAlgorithmType algorithmType
    ) {
        return allocate(resources, requests, new AllocationOptions(algorithmType));
    }

    public AllocationResult allocate(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationOptions options
    ) {
        validateInput(resources, requests, options);

        AllocationAlgorithm algorithm = createAlgorithm(options);

        return algorithm.allocate(resources, requests);
    }

    private AllocationAlgorithm createAlgorithm(AllocationOptions options) {
        switch (options.getAlgorithmType()) {
            case GREEDY:
                return new GreedyAllocationAlgorithm();
            case BACKTRACKING:
                return new BacktrackingAllocationAlgorithm(options.getBacktrackingTimeLimitMs());
            case CP_SAT:
                return new CpSatAllocationAlgorithm(options.getCpSatTimeLimitSeconds());
            default:
                throw new IllegalArgumentException("Nepodrzan tip algoritma: " + options.getAlgorithmType());
        }
    }

    private void validateInput(
            List<Resource> resources,
            List<AllocationRequest> requests,
            AllocationOptions options
    ) {
        if (resources == null) {
            throw new IllegalArgumentException("Lista resursa ne sme biti null.");
        }

        if (requests == null) {
            throw new IllegalArgumentException("Lista zahteva ne sme biti null.");
        }

        if (options == null) {
            throw new IllegalArgumentException("Opcije alokacije ne smeju biti null.");
        }

        validateResources(resources);
        validateRequests(requests);
    }

    private void validateResources(List<Resource> resources) {
        Set<String> resourceIds = new HashSet<>();

        for (Resource resource : resources) {
            if (resource == null) {
                throw new IllegalArgumentException("Lista resursa ne sme sadrzati null elemente.");
            }

            if (!resourceIds.add(resource.getId())) {
                throw new IllegalArgumentException("Resource id mora biti jedinstven: " + resource.getId());
            }
        }
    }

    private void validateRequests(List<AllocationRequest> requests) {
        Set<String> requestIds = new HashSet<>();

        for (AllocationRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("Lista zahteva ne sme sadrzati null elemente.");
            }

            if (!requestIds.add(request.getId())) {
                throw new IllegalArgumentException("AllocationRequest id mora biti jedinstven: " + request.getId());
            }
        }
    }
}
