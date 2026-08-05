package allocation.benchmark;

import allocation.model.AllocationRequest;
import allocation.model.Resource;

import java.util.List;

/**
 * One unique logical scenario retained for versioned benchmark snapshot export.
 */
public class BenchmarkScenarioSnapshot {

    public static final int SCHEMA_VERSION = 1;

    private final BenchmarkProfile profile;
    private final long seed;
    private final String scenarioFingerprint;
    private final List<Resource> resources;
    private final List<AllocationRequest> requests;

    public BenchmarkScenarioSnapshot(
            BenchmarkProfile profile,
            long seed,
            String scenarioFingerprint,
            List<Resource> resources,
            List<AllocationRequest> requests
    ) {
        if (profile == null || scenarioFingerprint == null || resources == null || requests == null) {
            throw new IllegalArgumentException("Scenario snapshot arguments must not be null.");
        }

        this.profile = profile;
        this.seed = seed;
        this.scenarioFingerprint = scenarioFingerprint;
        this.resources = List.copyOf(resources);
        this.requests = List.copyOf(requests);
    }

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public BenchmarkProfile getProfile() {
        return profile;
    }

    public long getSeed() {
        return seed;
    }

    public String getScenarioFingerprint() {
        return scenarioFingerprint;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public List<AllocationRequest> getRequests() {
        return requests;
    }
}
