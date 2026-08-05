package allocation.benchmark;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BenchmarkOutputWriter {

    public static final String RAW_FILE_NAME = "raw-results.csv";
    public static final String SUMMARY_FILE_NAME = "summary-results.csv";
    public static final String REQUEST_OUTCOMES_FILE_NAME = "request-outcomes.csv";
    public static final String SCENARIO_SNAPSHOTS_FILE_NAME = "scenario-snapshots.json";
    public static final String METADATA_FILE_NAME = "metadata.json";

    private final BenchmarkCsvWriter csvWriter;
    private final BenchmarkMetadataWriter metadataWriter;
    private final BenchmarkScenarioSnapshotWriter scenarioSnapshotWriter;

    public BenchmarkOutputWriter() {
        this(
                new BenchmarkCsvWriter(),
                new BenchmarkMetadataWriter(),
                new BenchmarkScenarioSnapshotWriter()
        );
    }

    BenchmarkOutputWriter(
            BenchmarkCsvWriter csvWriter,
            BenchmarkMetadataWriter metadataWriter,
            BenchmarkScenarioSnapshotWriter scenarioSnapshotWriter
    ) {
        this.csvWriter = csvWriter;
        this.metadataWriter = metadataWriter;
        this.scenarioSnapshotWriter = scenarioSnapshotWriter;
    }

    public BenchmarkOutputPaths write(BenchmarkRun run) throws IOException {
        if (run == null) {
            throw new IllegalArgumentException("Benchmark run must not be null.");
        }

        Path outputDirectory = run.getConfiguration().getOutputDirectory();
        Files.createDirectories(outputDirectory);

        BenchmarkOutputPaths paths = new BenchmarkOutputPaths(
                outputDirectory.resolve(RAW_FILE_NAME),
                outputDirectory.resolve(SUMMARY_FILE_NAME),
                outputDirectory.resolve(REQUEST_OUTCOMES_FILE_NAME),
                outputDirectory.resolve(SCENARIO_SNAPSHOTS_FILE_NAME),
                outputDirectory.resolve(METADATA_FILE_NAME)
        );

        validateOutputAvailability(paths, run.getConfiguration().isOverwrite());

        csvWriter.writeRaw(paths.rawResults(), run.getRawResults());
        csvWriter.writeSummary(paths.summaryResults(), run.getSummaryResults());
        csvWriter.writeRequestOutcomes(paths.requestOutcomes(), run.getRequestOutcomes());
        scenarioSnapshotWriter.write(paths.scenarioSnapshots(), run.getScenarioSnapshots());
        metadataWriter.write(paths.metadata(), run, paths);

        return paths;
    }

    private void validateOutputAvailability(
            BenchmarkOutputPaths paths,
            boolean overwrite
    ) throws FileAlreadyExistsException {
        if (overwrite) {
            return;
        }

        for (Path path : List.of(
                paths.rawResults(),
                paths.summaryResults(),
                paths.requestOutcomes(),
                paths.scenarioSnapshots(),
                paths.metadata()
        )) {
            if (Files.exists(path)) {
                throw new FileAlreadyExistsException(
                        path.toString(),
                        null,
                        "Benchmark output already exists. Choose another --output directory or use --overwrite."
                );
            }
        }
    }
}
