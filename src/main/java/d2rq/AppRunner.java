package d2rq;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import sun.misc.Unsafe;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Created by @szuev on 21.03.2018.
 */
public class AppRunner {

    public static void main(String... args) {
        forceDisableExternalLogging();
        Log4jHelper.turnLoggingOff();
        PrintStream out = CommandLineTool.CONSOLE;
        if (args.length == 0 || Stream.of("-h", "--h", "-help", "--help", "/?").anyMatch(h -> h.equalsIgnoreCase(args[0]))) {
            out.println("usage:");
            Mode.printUsage(out);
            System.exit(0);
        }
        Optional<Mode> c = Mode.commands().filter(k -> k.is(args[0])).findFirst();
        if (!c.isPresent()) {
            out.println("Missing required command. Must be one of the following:");
            Mode.printUsage(out);
            System.exit(1);
        }
        CommandLineTool tool = c.get().getTool();
        try {
            tool.process(ArrayUtils.remove(args, 0));
        } catch (CommandLineTool.ExitException exit) {
            System.exit(exit.getCode());
        }
    }

    private enum Mode {
        QUERY("d2rq-query") {
            @Override
            public CommandLineTool getTool() {
                return new QueryTool();
            }
        },
        DUMP("dump-rdf") {
            @Override
            public CommandLineTool getTool() {
                return new DumpTool();
            }
        },
        MAPPING("generate-mapping") {
            @Override
            public CommandLineTool getTool() {
                return new MappingTool();
            }
        },;
        private final String key;

        Mode(String s) {
            key = s;
        }

        public abstract CommandLineTool getTool();

        public boolean is(String str) {
            return str != null && key.equalsIgnoreCase(str.trim());
        }

        public static void printUsage(PrintStream out) {
            commands().forEach(c -> out.println("\t" + StringUtils.rightPad(c.key, 16) + " [options...]"));
        }

        public static Stream<Mode> commands() {
            return Arrays.stream(Mode.values());
        }
    }

    private static void forceDisableExternalLogging() {
        try {
            // java9:
            Class clazz = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = clazz.getDeclaredField("logger");
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe) theUnsafe.get(null);
            unsafe.putObjectVolatile(clazz, unsafe.staticFieldOffset(logger), null);
        } catch (Exception e) {
            // ignore
        }
    }
}
