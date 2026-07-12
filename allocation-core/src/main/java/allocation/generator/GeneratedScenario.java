package allocation.generator;

import allocation.model.AllocationRequest;
import allocation.model.Resource;

import java.util.List;

public class GeneratedScenario {

    private String name;
    private long seed;
    private List<Resource> resources;
    private List<AllocationRequest> requests;

    public GeneratedScenario(
            String name,
            long seed,
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Scenario name must not be blank.");
        }

        if (resources == null) {
            throw new IllegalArgumentException("Resource list must not be null.");
        }

        if (requests == null) {
            throw new IllegalArgumentException("Request list must not be null.");
        }

        this.name = name;
        this.seed = seed;
        this.resources = List.copyOf(resources);
        this.requests = List.copyOf(requests);
    }

    public String getName() {
        return name;
    }

    public long getSeed() {
        return seed;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public List<AllocationRequest> getRequests() {
        return requests;
    }
}
