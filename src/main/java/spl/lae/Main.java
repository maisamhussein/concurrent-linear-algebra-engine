package spl.lae;

import parser.ComputationNode;
import parser.InputParser;
import parser.OutputWriter;

import java.io.IOException;
import java.text.ParseException;

public class Main {

    public static void main(String[] args) throws IOException {
        // Expected args:
        // args[0] = numThreads
        // args[1] = inputPath
        // args[2] = outputPath
        if (args == null || args.length != 3) {
            System.out.println("Usage: java -jar target/lga-1.0.jar <numThreads> <inputPath> <outputPath>");
            return;
        }

        final int numThreads;
        final String inputPath = args[1];
        final String outputPath = args[2];

        try {
            numThreads = Integer.parseInt(args[0]);
            if (numThreads <= 0) throw new NumberFormatException("numThreads must be > 0");
        } catch (NumberFormatException e) {
            OutputWriter.write("Invalid numThreads: " + args[0], outputPath);
            return;
        }

        LinearAlgebraEngine engine = null;

        try {
            // 1) Parse input JSON -> computation tree
            InputParser parser = new InputParser();
            ComputationNode root = parser.parse(inputPath);

            // 2) Create engine (threads)
            engine = new LinearAlgebraEngine(numThreads);

            // 3) Compute
            ComputationNode resultNode = engine.run(root);

            // 4) Write output
            double[][] result = resultNode.getMatrix();
            OutputWriter.write(result, outputPath);

            // 5) Optional: print worker report to stdout
            System.out.println(engine.getWorkerReport());

        } catch (ParseException e) {
            OutputWriter.write("Parse error: " + e.getMessage(), outputPath);
        } catch (RuntimeException e) {
            // includes your ComputationFailedException etc.
            OutputWriter.write("Computation error: " + e.getMessage(), outputPath);
        } finally {
            // 6) Shutdown threads cleanly
            if (engine != null) {
                try {
                    engine.shutdown();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    OutputWriter.write("Shutdown interrupted: " + ex.getMessage(), outputPath);
                }
            }
        }
    }
}