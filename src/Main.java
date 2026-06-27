import java.io.*;
import java.util.Locale;

/**
 * Entry point of MatrixNet.
 * <p>
 * Reads commands from the input file, sends each command to Core,
 * and writes Core’s responses to the output file.
 *
 * @author Gülsüm Yazıcı
 */
public class Main {

    /** Single Core instance that stores all platform data and executes all operations. */
    private static final Core core = new Core();

    /**
     * Initializes I/O, reads the input line by line, and processes each command.
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        if (args.length != 2) {
            System.err.println("Usage: java Main <input_file> <output_file>");
            System.exit(1);
        }

        String inputFile  = args[0];
        String outputFile = args[1];

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    processCommand(line, writer);
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading/writing files: " + e.getMessage());
        }
    }

    /**
     * Parses a single command, dispatches it to the appropriate Core method,
     * and writes the returned output string into the output file.
     */
    private static void processCommand(String command, BufferedWriter writer)
            throws IOException {

        String[] parts = command.split("\\s+");
        String op = parts[0];
        String result = "";

        try {
            switch (op) {

                case "spawn_host":
                    result = core.spawnHost(
                            parts[1],                          // hostId
                            Integer.parseInt(parts[2])         // clearanceLevel
                    );
                    break;

                case "link_backdoor":
                    result = core.linkBackdoor(
                            parts[1],                          // hostId1
                            parts[2],                          // hostId2
                            Integer.parseInt(parts[3]),        // latency
                            Integer.parseInt(parts[4]),        // bandwidth
                            Integer.parseInt(parts[5])         // firewall level
                    );
                    break;

                case "seal_backdoor":
                    result = core.sealBackdoor(
                            parts[1],                          // hostId1
                            parts[2]                           // hostId2
                    );
                    break;

                case "trace_route":
                    result = core.traceRoute(
                            parts[1],                          // sourceId
                            parts[2],                          // destId
                            Integer.parseInt(parts[3]),        // min_bandwidth
                            Integer.parseInt(parts[4])         // lambda
                    );
                    break;

                case "scan_connectivity":
                    result = core.scanConnectivity();
                    break;

                case "simulate_breach":
                    if (parts.length == 2) {
                        result = core.simulateBreachHost(
                                parts[1]                       // hostId
                        );
                    } else if (parts.length == 3) {
                        result = core.simulateBreachBackdoor(
                                parts[1],                      // hostId1
                                parts[2]                       // hostId2
                        );
                    } else {
                        result = "Some error occurred in simulate breach.";
                    }
                    break;

                case "oracle_report":
                    result = core.oracleReport();
                    break;

                default:
                    result = "Unknown command: " + op;
            }

            writer.write(result);
            writer.write("\n");

        } catch (Exception ex) {
            writer.write("Error processing command: " + command);
            writer.write("\n");
        }
    }
}
