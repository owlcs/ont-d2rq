package d2rq.utils;

import org.apache.jena.util.FileUtils;

import java.util.*;
import java.util.stream.Stream;

/**
 * Command line processing helper.
 * A modified copy-paste from some old Jena.
 * todo: better to replace with apache commons-cli.
 */
public class CommandLine {

    private final Map<String, Arg> args = new HashMap<>();
    private final Map<String, ArgDecl> argMap = new HashMap<>();
    private final List<String> items = new ArrayList<>();

    public static boolean isHelpOption(String arg) {
        return Stream.of("-h", "--h", "-help", "--help", "/?").anyMatch(h -> h.equalsIgnoreCase(arg));
    }

    public static String withIndirection(String value, String indirectionMarker) {
        if (!value.startsWith(indirectionMarker)) {
            return value;
        }
        try {
            return FileUtils.readWholeFileAsUTF8(value.substring(1));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to read '" + value + "': " + ex.getMessage(), ex);
        }
    }

    private static String canonicalForm(String str) {
        if (str.startsWith("--"))
            return str.substring(2);

        if (str.startsWith("-"))
            return str.substring(1);

        return str;
    }

    private static boolean matches(ArgDecl declaration, Arg value) {
        Iterator<String> names = declaration.names();
        while (names.hasNext()) {
            if (names.next().equals(value.getName())) return true;
        }
        return false;
    }

    /**
     * Processes a set of command line arguments.
     *
     * @param argv The words of the command line
     * @throws IllegalArgumentException Throw when something is wrong (no value found, action fails)
     */
    public void process(String... argv) throws IllegalArgumentException {
        List<String> argList = new ArrayList<>(Arrays.asList(argv));

        int i = 0;
        for (; i < argList.size(); i++) {
            String argStr = argList.get(i);
            if (endProcessing(argStr))
                break;

            // If the flag has a "=" or :, it is long form --arg=value.
            // Split and insert the arg
            int j1 = argStr.indexOf('=');
            int j2 = argStr.indexOf(':');
            int j = Integer.MAX_VALUE;

            if (j1 > 0)
                j = j1;
            if (j2 > 0 && j2 < j)
                j = j2;

            if (j != Integer.MAX_VALUE) {
                String a2 = argStr.substring(j + 1);
                argList.add(i + 1, a2);
                argStr = argStr.substring(0, j);
            }

            argStr = canonicalForm(argStr);
            String val;

            if (argMap.containsKey(argStr)) {
                if (!args.containsKey(argStr))
                    args.put(argStr, new Arg(argStr));

                Arg arg = args.get(argStr);
                ArgDecl argDecl = argMap.get(argStr);

                if (argDecl.takesValue()) {
                    if (i == (argList.size() - 1))
                        throw new IllegalArgumentException("No value for argument: " + arg.getName());
                    i++;
                    val = argList.get(i);
                    arg.addValue(val);
                }

            } else {
                throw new IllegalArgumentException("Unknown argument: " + argList.get(i));
            }
        }
        // Remainder
        if (i < argList.size()) {
            if (argList.get(i).equals("-") || argList.get(i).equals("--"))
                i++;
            for (; i < argList.size(); i++) {
                String item = argList.get(i);
                items.add(item);
            }
        }
    }

    /**
     * Answers {@code true} if the argument terminates argument processing for the rest
     * of the command line. Default is to stop just before the first arg that
     * does not start with "-", or is "-" or "--".
     *
     * @param argStr String
     * @return boolean
     */
    private boolean endProcessing(String argStr) {
        return !argStr.startsWith("-") || argStr.equals("--") || argStr.equals("-");
    }

    /**
     * Test whether an argument was seen.
     *
     * @param argDecl String
     * @return boolean
     */
    public boolean contains(ArgDecl argDecl) {
        return getArg(argDecl) != null;
    }

    /**
     * Gets the argument associated with the argument declaration.
     * Actually returns the LAST one seen
     *
     * @param argDecl Argument declaration to find
     * @return Last argument that matched.
     */
    private Arg getArg(ArgDecl argDecl) {
        for (Arg a : args.values()) {
            if (matches(argDecl, a)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Returns the value (a string) for an argument with a value -
     * returns null for no argument and no value.
     *
     * @param argDecl {@link ArgDecl}
     * @return String
     */
    public String getArgValue(ArgDecl argDecl) {
        Arg arg = getArg(argDecl);
        if (arg == null)
            return null;
        return arg.getValue();
    }

    /**
     * Adds an argument declaration object.
     *
     * @param arg Argument to add
     * @return The CommandLine processor object
     */
    public CommandLine add(ArgDecl arg) {
        for (Iterator<String> iter = arg.names(); iter.hasNext(); )
            argMap.put(iter.next(), arg);
        return this;
    }

    public int numItems() {
        return items.size();
    }

    public String getItem(int i) {
        return getItem(i, null);
    }

    public String getItem(int i, String indirectionMarker) {
        if (i < 0 || i >= items.size())
            return null;
        String item = items.get(i);
        return indirectionMarker == null ? item : withIndirection(item, indirectionMarker);
    }

    /**
     * A command line argument that has been found specification.
     */
    static class Arg {
        private final String name;
        private final List<String> values = new ArrayList<>();

        Arg(String name) {
            this.name = name;
        }

        void addValue(String v) {
            values.add(v);
        }

        String getName() {
            return name;
        }

        String getValue() {
            return values.isEmpty() ? null : values.get(0);
        }
    }
}
