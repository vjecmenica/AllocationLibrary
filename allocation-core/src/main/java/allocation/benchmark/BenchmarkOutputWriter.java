package allocation.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BenchmarkOutputWriter {

    public static final String RAW_FILE_NAME = "raw-results.csv";
    public static final String SUMMARY_FILE_NAME = "summary-results.csv";
    public static final String METADATA_FILE_NAME = "metadata.json";

    private final BenchmarkCsvWriter csvWriter;
    private final BenchmarkMetadataWriter metadataWriter;

    public BenchmarkOutputWriter() {
        this(new BenchmarkCsvWriter(), new BenchmarkMetadataWriter());
    }

    BenchmarkOutputWriter(
            BenchmarkCsvWriter csvWriter,
            BenchmarkMetadataWriter metadataWriter
    ) {
        this.csvWriter = csvWriter;
        this.metadataWriter = metadataWriter;
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
                outputDirectory.resolve(METADATA_FILE_NAME)
        );

        csvWriter.writeRaw(paths.rawResults(), run.getRawResults());
        csvWriter.writeSummary(paths.summaryResults(), run.getSummaryResults());
        metadataWriter.write(paths.metadata(), run, paths);

        return paths;
    }
}
