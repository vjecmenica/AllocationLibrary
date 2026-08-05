package allocation.benchmark;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Command-line entry point for reproducible allocation experiments.
 */
public class BenchmarkMain {

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, PrintStream output, PrintStream error) {
        try {
            BenchmarkCliArguments.ParsedArguments parsed = BenchmarkCliArguments.parse(args);

            if (parsed.helpRequested()) {
                output.print(BenchmarkCliArguments.usage());
                return 0;
            }

            BenchmarkRun run = new BenchmarkRunner().run(parsed.configuration());
            BenchmarkOutputPaths paths = new BenchmarkOutputWriter().write(run);

            output.println(BenchmarkSummaryReport.fromResults(run.getRawResults()).formatForConsole());
            output.println();
            output.println("Raw results: " + paths.rawResults().toAbsolutePath());
            output.println("Summary results: " + paths.summaryResults().toAbsolutePath());
            output.println("Request outcomes: " + paths.requestOutcomes().toAbsolutePath());
            output.println("Scenario snapshots: " + paths.scenarioSnapshots().toAbsolutePath());
            output.println("Metadata: " + paths.metadata().toAbsolutePath());
            return 0;
        } catch (IllegalArgumentException exception) {
            error.println("Error: " + exception.getMessage());
            error.println();
            error.print(BenchmarkCliArguments.usage());
            return 2;
        } catch (IOException exception) {
            error.println("Error: Benchmark output could not be written: " + exception.getMessage());
            return 1;
        }
    }
}
