package d2rq;

import d2rq.utils.LogHelper;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Created by @szuev on 21.03.2018.
 */
public class AppRunner {

    public static void main(String... args) {
        LogHelper.turnLoggingOff();
        PrintStream out = System.err;
        if (args.length == 0 || CommandLineTool.isHelpOption(args[0])) {
            out.println("usage:");
            ToolFactory.printUsage(out);
            System.exit(0);
        }
        Optional<ToolFactory> c = ToolFactory.commands().filter(k -> k.is(args[0])).findFirst();
        if (!c.isPresent()) {
            out.println("Missing required command. Must be one of the following:");
            ToolFactory.printUsage(out);
            System.exit(1);
        }
        CommandLineTool tool = c.get().getTool(out);
        try {
            tool.process(ArrayUtils.remove(args, 0));
        } catch (CommandLineTool.Exit exit) {
            System.exit(exit.getCode());
        }
    }

    enum ToolFactory {
        QUERY("d2rq-query") {
            @Override
            public CommandLineTool getTool(PrintStream out) {
                return new QueryTool(out);
            }
        },
        DUMP("dump-rdf") {
            @Override
            public CommandLineTool getTool(PrintStream out) {
                return new DumpTool(out);
            }
        },
        MAPPING("generate-mapping") {
            @Override
            public CommandLineTool getTool(PrintStream out) {
                return new MappingTool(out);
            }
        },
        SERVER("d2rq-server") {
            @Override
            public CommandLineTool getTool(PrintStream out) {
                return new ServerTool(out);
            }
        };
        private final String key;

        ToolFactory(String s) {
            key = s;
        }

        public abstract CommandLineTool getTool(PrintStream out);

        public boolean is(String str) {
            return str != null && key.equalsIgnoreCase(str.trim());
        }

        public static void printUsage(PrintStream out) {
            commands().forEach(c -> out.println("\t" + StringUtils.rightPad(c.key, 16) + " [options...]"));
        }

        public static Stream<ToolFactory> commands() {
            return Arrays.stream(ToolFactory.values());
        }
    }

}
