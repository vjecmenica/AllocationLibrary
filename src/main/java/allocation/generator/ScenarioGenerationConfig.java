package allocation.generator;

import java.time.LocalDateTime;

public class ScenarioGenerationConfig {

    private String scenarioName;
    private int resourceCount;
    private int requestCount;
    private long seed;
    private LocalDateTime scenarioStart;
    private int timeSlotCount;
    private double staffRequirementProbability;
    private double projectorRequirementProbability;

    public ScenarioGenerationConfig(
            String scenarioName,
            int resourceCount,
            int requestCount,
            long seed,
            LocalDateTime scenarioStart,
            int timeSlotCount,
            double staffRequirementProbability,
            double projectorRequirementProbability
    ) {
        if (scenarioName == null || scenarioName.isBlank()) {
            throw new IllegalArgumentException("Naziv scenarija ne sme biti prazan.");
        }

        if (resourceCount <= 0) {
            throw new IllegalArgumentException("Broj resursa mora biti pozitivan.");
        }

        if (requestCount <= 0) {
            throw new IllegalArgumentException("Broj zahteva mora biti pozitivan.");
        }

        if (scenarioStart == null) {
            throw new IllegalArgumentException("Pocetak scenarija ne sme biti null.");
        }

        if (timeSlotCount <= 0) {
            throw new IllegalArgumentException("Broj vremenskih slotova mora biti pozitivan.");
        }

        validateProbability(staffRequirementProbability, "Verovatnoca STAFF zahteva");
        validateProbability(projectorRequirementProbability, "Verovatnoca PROJECTOR zahteva");

        this.scenarioName = scenarioName;
        this.resourceCount = resourceCount;
        this.requestCount = requestCount;
        this.seed = seed;
        this.scenarioStart = scenarioStart;
        this.timeSlotCount = timeSlotCount;
        this.staffRequirementProbability = staffRequirementProbability;
        this.projectorRequirementProbability = projectorRequirementProbability;
    }

    private void validateProbability(double probability, String fieldName) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(fieldName + " mora biti izmedju 0.0 i 1.0.");
        }
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public int getResourceCount() {
        return resourceCount;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public long getSeed() {
        return seed;
    }

    public LocalDateTime getScenarioStart() {
        return scenarioStart;
    }

    public int getTimeSlotCount() {
        return timeSlotCount;
    }

    public double getStaffRequirementProbability() {
        return staffRequirementProbability;
    }

    public double getProjectorRequirementProbability() {
        return projectorRequirementProbability;
    }
}
