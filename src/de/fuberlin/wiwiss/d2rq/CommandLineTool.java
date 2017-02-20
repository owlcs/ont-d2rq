package de.fuberlin.wiwiss.d2rq;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.BiConsumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.jena.shared.JenaException;
import org.apache.jena.util.FileManager;
import org.apache.jena.util.FileUtils;

import de.fuberlin.wiwiss.d2rq.mapgen.Filter;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterIncludeExclude;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterMatchAny;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterParser;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterParser.ParseException;


/**
 * Base class for the D2RQ command line tools. They share much of their
 * argument list and functionality, therefore this is extracted into
 * this superclass.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public abstract class CommandLineTool {
    private final static Log log = LogFactory.getLog(CommandLineTool.class);

    private final CommandLine cmd = new CommandLine();
    private final ArgDecl userArg = new ArgDecl(true, "u", "user", "username");
    private final ArgDecl passArg = new ArgDecl(true, "p", "pass", "password");
    private final ArgDecl driverArg = new ArgDecl(true, "d", "driver");
    private final ArgDecl sqlFileArg = new ArgDecl(true, "l", "load-sql");
    private final ArgDecl w3cArg = new ArgDecl(false, "w3c", "direct-mapping");
    private final ArgDecl verboseArg = new ArgDecl(false, "verbose");
    private final ArgDecl debugArg = new ArgDecl(false, "debug");
    private final ArgDecl schemasArg = new ArgDecl(true, "schema", "schemas");
    private final ArgDecl tablesArg = new ArgDecl(true, "table", "tables");
    private final ArgDecl columnsArg = new ArgDecl(true, "column", "columns");
    private final ArgDecl skipSchemasArg = new ArgDecl(true, "skip-schema", "skip-schemas");
    private final ArgDecl skipTablesArg = new ArgDecl(true, "skip-table", "skip-tables");
    private final ArgDecl skipColumnsArg = new ArgDecl(true, "skip-column", "skip-columns");
    private final SystemLoader loader = new SystemLoader();
    private boolean supportImplicitJdbcURL = true;
    private int minArguments = 0;
    private int maxArguments = 1;

    public abstract void usage();

    public abstract void initArgs(CommandLine cmd);

    public abstract void run(CommandLine cmd, SystemLoader loader)
            throws D2RQException, IOException;

    protected void setMinMaxArguments(int min, int max) {
        minArguments = min;
        maxArguments = max;
    }

    protected void setSupportImplicitJdbcURL(boolean flag) {
        supportImplicitJdbcURL = flag;
    }

    public void process(String[] args) {
        cmd.add(userArg);
        cmd.add(passArg);
        cmd.add(driverArg);
        cmd.add(sqlFileArg);
        cmd.add(w3cArg);
        cmd.add(verboseArg);
        cmd.add(debugArg);
        cmd.add(schemasArg);
        cmd.add(tablesArg);
        cmd.add(columnsArg);
        cmd.add(skipSchemasArg);
        cmd.add(skipTablesArg);
        cmd.add(skipColumnsArg);

        initArgs(cmd);

        try {
            cmd.process(args);
        } catch (IllegalArgumentException ex) {
            reportException(ex);
        }

        if (cmd.hasArg(verboseArg)) {
            Log4jHelper.setVerboseLogging();
        }
        if (cmd.hasArg(debugArg)) {
            Log4jHelper.setDebugLogging();
        }

        if (cmd.numItems() == minArguments && supportImplicitJdbcURL && cmd.hasArg(sqlFileArg)) {
            loader.setJdbcURL(SystemLoader.DEFAULT_JDBC_URL);
        } else if (cmd.numItems() == 0) {
            usage();
            System.exit(1);
        }
        if (cmd.numItems() < minArguments) {
            reportException(new IllegalArgumentException("Not enough arguments"));
        } else if (cmd.numItems() > maxArguments) {
            reportException(new IllegalArgumentException("Too many arguments"));
        }

        if (cmd.contains(userArg)) {
            loader.setUsername(cmd.getArg(userArg).getValue());
        }
        if (cmd.contains(passArg)) {
            loader.setPassword(cmd.getArg(passArg).getValue());
        }
        if (cmd.contains(driverArg)) {
            loader.setJDBCDriverClass(cmd.getArg(driverArg).getValue());
        }
        if (cmd.contains(sqlFileArg)) {
            loader.setStartupSQLScript(cmd.getArg(sqlFileArg).getValue());
        }
        if (cmd.contains(w3cArg)) {
            loader.setGenerateW3CDirectMapping(true);
        }
        try {
            Collection<Filter> includes = new ArrayList<>();
            Collection<Filter> excludes = new ArrayList<>();
            if (cmd.contains(schemasArg)) {
                String spec = withIndirection(cmd.getArg(schemasArg).getValue());
                includes.add(new FilterParser(spec).parseSchemaFilter());
            }
            if (cmd.contains(tablesArg)) {
                String spec = withIndirection(cmd.getArg(tablesArg).getValue());
                includes.add(new FilterParser(spec).parseTableFilter(true));
            }
            if (cmd.contains(columnsArg)) {
                String spec = withIndirection(cmd.getArg(columnsArg).getValue());
                includes.add(new FilterParser(spec).parseColumnFilter(true));
            }
            if (cmd.contains(skipSchemasArg)) {
                String spec = withIndirection(cmd.getArg(skipSchemasArg).getValue());
                excludes.add(new FilterParser(spec).parseSchemaFilter());
            }
            if (cmd.contains(skipTablesArg)) {
                String spec = withIndirection(cmd.getArg(skipTablesArg).getValue());
                excludes.add(new FilterParser(spec).parseTableFilter(false));
            }
            if (cmd.contains(skipColumnsArg)) {
                String spec = withIndirection(cmd.getArg(skipColumnsArg).getValue());
                excludes.add(new FilterParser(spec).parseColumnFilter(false));
            }
            if (!includes.isEmpty() || !excludes.isEmpty()) {
                loader.setFilter(new FilterIncludeExclude(
                        includes.isEmpty() ? Filter.ALL : FilterMatchAny.create(includes),
                        FilterMatchAny.create(excludes)));
            }
            run(cmd, loader);
        } catch (IllegalArgumentException | ParseException | IOException ex) {
            reportException(ex);
        } catch (D2RQException ex) {
            reportException(ex);
        } catch (JenaException ex) {
            reportException(ex);
        }
    }

    private static void reportException(D2RQException ex) {
        if (ex.getMessage() == null && ex.getCause() != null && ex.getCause().getMessage() != null) {
            if (ex.getCause() instanceof SQLException) {
                System.err.println("SQL error " + ex.getCause().getMessage());
            } else {
                System.err.println(ex.getCause().getMessage());
            }
        } else {
            System.err.println(ex.getMessage());
        }
        log.info("Command line tool exception", ex);
        System.exit(1);
    }

    private void reportException(Exception ex) {
        System.err.println(ex.getMessage());
        log.info("Command line tool exception", ex);
        System.exit(1);
    }

    protected void printStandardArguments(boolean withMappingFile) {
        System.err.println("  Arguments:");
        if (withMappingFile) {
            System.err.println("    mappingFile     Filename or URL of a D2RQ mapping file");
        }
        System.err.println("    jdbcURL         JDBC URL for the DB, e.g. jdbc:mysql://localhost/dbname");
        if (supportImplicitJdbcURL) {
            System.err.println("                    (If omitted with -l, set up a temporary in-memory DB)");
        }
    }

    protected void printConnectionOptions() {
        System.err.println("    -u username     Database user for connecting to the DB");
        System.err.println("    -p password     Database password for connecting to the DB");
        System.err.println("    -d driverclass  Java class name of the JDBC driver for the DB");
        System.err.println("    -l script.sql   Load a SQL script before processing");
        System.err.println("    --w3c           Produce W3C Direct Mapping compatible mapping file");
        System.err.println("    --[skip-](schemas|tables|columns) [schema.]table[.column]");
        System.err.println("                    Include or exclude specific database objects");
    }

    private static String withIndirection(String value) {
        if (value.startsWith("@")) {
            value = value.substring(1);
            try {
                value = FileManager.get().readWholeFileAsUTF8(value);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to read '" + value + "': " + ex.getMessage());
            }
        }
        return value;
    }

    /**
     * Command line argument processing based on a trigger model.
     * An action is called whenever an argument is encountered. Example:
     * <CODE>
     * public static void main (String[] args)
     * {
     * CommandLine cl = new CommandLine() ;
     * cl.add(false, "verbose")
     * .add(true, "--file") ;
     * cl.process(args) ;
     * <p>
     * for ( Iterator iter = cl.args() ; iter.hasNext() ; )
     * ...
     * }
     * </CODE>
     * A gloabl hook is provided to inspect arguments just before the
     * action.  Tracing is enabled by setting this to a suitable function
     * such as that provided by trace():
     * <CODE>
     * cl.setHook(cl.trace()) ;
     * </CODE>
     * <p>
     * <ul>
     * <li>Neutral as to whether options have - or --</li>
     * <li>Does not allow multiple single letter options to be concatenated.</li>
     * <li>Options may be ended with - or --</li>
     * <li>Arguments with values can use "="</li>
     * </ul>
     * <p>
     * Note: a updated copy-past from jena-core-3.0.1 {@link jena.rdfcat.CommandLine}
     */
    protected static class CommandLine {
        /* Extra processor called before the registered one when set.
         * Used for tracing.
         */
        BiConsumer<String, String> argHook = null;
        protected String usage = null;
        Map<String, ArgDecl> argMap = new HashMap<>();
        protected Map<String, Arg> args = new HashMap<>();
        //protected boolean ignoreUnknown = false ;

        // Rest of the items found on the command line
        String indirectionMarker = "@";
        boolean allowItemIndirect = false;   // Allow @ to mean contents of file
        boolean ignoreIndirectionMarker = false;       // Allow comand line items to have leading @ but strip it.
        List<String> items = new ArrayList<>();


        /**
         * Creates new CommandLine
         */
        CommandLine() {
        }

        /**
         * Set the global argument handler.  Called on every valid argument.
         *
         * @param argHandler Handler
         */
        public void setHook(BiConsumer<String, String> argHandler) {
            argHook = argHandler;
        }

        public void setUsage(String usageMessage) {
            usage = usageMessage;
        }

        public boolean hasArgs() {
            return args.size() > 0;
        }

        public boolean hasItems() {
            return items.size() > 0;
        }

        public Iterator<Arg> args() {
            return args.values().iterator();
        }
//        public Map args() { return args ; }
//        public List items() { return items ; }

        public int numArgs() {
            return args.size();
        }

        public int numItems() {
            return items.size();
        }

        public void pushItem(String s) {
            items.add(s);
        }

        public boolean isIndirectItem(int i) {
            return allowItemIndirect && items.get(i).startsWith(indirectionMarker);
        }

        public String getItem(int i) {
            return getItem(i, allowItemIndirect);
        }

        public String getItem(int i, boolean withIndirect) {
            if (i < 0 || i >= items.size())
                return null;


            String item = items.get(i);

            if (withIndirect && item.startsWith(indirectionMarker)) {
                item = item.substring(1);
                try {
                    item = FileUtils.readWholeFileAsUTF8(item);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Failed to read '" + item + "': " + ex.getMessage());
                }
            }
            return item;
        }


        /**
         * Process a set of command line arguments.
         *
         * @param argv The words of the command line.
         * @throws IllegalArgumentException Throw when something is wrong (no value found, action fails).
         */
        public void process(String[] argv) throws java.lang.IllegalArgumentException {
            List<String> argList = new ArrayList<>();
            argList.addAll(Arrays.asList(argv));

            int i = 0;
            for (; i < argList.size(); i++) {
                String argStr = argList.get(i);
                if (endProcessing(argStr))
                    break;

                if (ignoreArgument(argStr))
                    continue;

                // If the flag has a "=" or :, it is long form --arg=value.
                // Split and insert the arg
                int j1 = argStr.indexOf('=');
                int j2 = argStr.indexOf(':');
                int j = Integer.MAX_VALUE;

                if (j1 > 0 && j1 < j)
                    j = j1;
                if (j2 > 0 && j2 < j)
                    j = j2;

                if (j != Integer.MAX_VALUE) {
                    String a2 = argStr.substring(j + 1);
                    argList.add(i + 1, a2);
                    argStr = argStr.substring(0, j);
                }

                argStr = ArgDecl.canonicalForm(argStr);
                String val = null;

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
                        arg.setValue(val);
                        arg.addValue(val);
                    }

                    // Global hook
                    if (argHook != null)
                        argHook.accept(argStr, val);

                    argDecl.trigger(arg);
                } else
                    handleUnrecognizedArg(argList.get(i));
//                    if ( ! getIgnoreUnknown() )
//                        // Not recognized
//                        throw new IllegalArgumentException("Unknown argument: "+argStr) ;
            }

            // Remainder.
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
         * Hook to test whether this argument should be processed further
         */
        public boolean ignoreArgument(String argStr) {
            return false;
        }

        /**
         * Answer true if this argument terminates argument processing for the rest
         * of the command line. Default is to stop just before the first arg that
         * does not start with "-", or is "-" or "--".
         */
        public boolean endProcessing(String argStr) {
            return !argStr.startsWith("-") || argStr.equals("--") || argStr.equals("-");
        }

        /**
         * Handle an unrecognised argument; default is to throw an exception
         *
         * @param argStr The string image of the unrecognised argument
         */
        public void handleUnrecognizedArg(String argStr) {
            throw new IllegalArgumentException("Unknown argument: " + argStr);
        }


        /**
         * Test whether an argument was seen.
         */

        public boolean contains(ArgDecl argDecl) {
            return getArg(argDecl) != null;
        }

        /**
         * Test whether an argument was seen.
         */

        public boolean contains(String s) {
            return getArg(s) != null;
        }


        /**
         * Test whether the command line had a particular argument
         *
         * @param argName String
         */
        public boolean hasArg(String argName) {
            return getArg(argName) != null;
        }

        /**
         * Test whether the command line had a particular argument
         *
         * @param argDecl ArgDecl
         */

        public boolean hasArg(ArgDecl argDecl) {
            return getArg(argDecl) != null;
        }


        /**
         * Get the argument associated with the argument declaration.
         * Actually returns the LAST one seen
         *
         * @param argDecl Argument declaration to find
         * @return Last argument that matched.
         */

        public Arg getArg(ArgDecl argDecl) {
            Arg arg = null;
            for (Arg a : args.values()) {
                if (argDecl.matches(a)) {
                    arg = a;
                }
            }
            return arg;
        }

        /**
         * Get the argument associated with the arguement name.
         * Actually returns the LAST one seen
         *
         * @param arg Argument declaration to find
         * @return Arg - Last argument that matched.
         */

        public Arg getArg(String arg) {
            arg = ArgDecl.canonicalForm(arg);
            return args.get(arg);
        }

        /**
         * Returns the value (a string) for an argument with a value -
         * returns null for no argument and no value.
         *
         * @param argDecl
         * @return String
         */
        public String getValue(ArgDecl argDecl) {
            Arg arg = getArg(argDecl);
            if (arg == null)
                return null;
            if (arg.hasValue())
                return arg.getValue();
            return null;
        }

        /**
         * Returns the value (a string) for an argument with a value -
         * returns null for no argument and no value.
         *
         * @param argName String
         * @return String
         */
        public String getValue(String argName) {
            Arg arg = getArg(argName);
            if (arg == null)
                return null;
            return arg.getValue();
        }

        /**
         * Returns all the values (0 or more strings) for an argument.
         *
         * @param argDecl ArgDecl
         * @return List
         */
        public List<String> getValues(ArgDecl argDecl) {
            Arg arg = getArg(argDecl);
            if (arg == null)
                return null;
            return arg.getValues();
        }

        /**
         * Returns all the values (0 or more strings) for an argument.
         *
         * @param argName
         * @return List
         */
        public List<String> getValues(String argName) {
            Arg arg = getArg(argName);
            if (arg == null)
                return null;
            return arg.getValues();
        }


        /**
         * Add an argument to those to be accepted on the command line.
         *
         * @param argName  Name
         * @param hasValue True if the command takes a (string) value
         * @return The CommandLine processor object
         */

        public CommandLine add(String argName, boolean hasValue) {
            return add(new ArgDecl(hasValue, argName));
        }

        /**
         * Add an argument to those to be accepted on the command line.
         * Argument order reflects ArgDecl.
         *
         * @param hasValue True if the command takes a (string) value
         * @param argName  Name
         * @return The CommandLine processor object
         */

        public CommandLine add(boolean hasValue, String argName) {
            return add(new ArgDecl(hasValue, argName));
        }

        /**
         * Add an argument object
         *
         * @param arg Argument to add
         * @return The CommandLine processor object
         */

        public CommandLine add(ArgDecl arg) {
            for (Iterator<String> iter = arg.names(); iter.hasNext(); )
                argMap.put(iter.next(), arg);
            return this;
        }

//        public boolean getIgnoreUnknown() { return ignoreUnknown ; }
//        public void setIgnoreUnknown(boolean ign) { ignoreUnknown = ign ; }

        /**
         * @return Returns whether items starting "@" have the value of named file.
         */
        public boolean allowItemIndirect() {
            return allowItemIndirect;
        }

        /**
         * @param allowItemIndirect Set whether items starting "@" have the value of named file.
         */
        public void setAllowItemIndirect(boolean allowItemIndirect) {
            this.allowItemIndirect = allowItemIndirect;
        }

        /**
         * @return Returns the ignoreIndirectionMarker.
         */
        public boolean isIgnoreIndirectionMarker() {
            return ignoreIndirectionMarker;
        }

        /**
         * @return Returns the indirectionMarker.
         */
        public String getIndirectionMarker() {
            return indirectionMarker;
        }

        /**
         * @param indirectionMarker The indirectionMarker to set.
         */
        public void setIndirectionMarker(String indirectionMarker) {
            this.indirectionMarker = indirectionMarker;
        }

        /**
         * @param ignoreIndirectionMarker The ignoreIndirectionMarker to set.
         */
        public void setIgnoreIndirectionMarker(boolean ignoreIndirectionMarker) {
            this.ignoreIndirectionMarker = ignoreIndirectionMarker;
        }

        public BiConsumer<String, String> trace() {
            return (arg, val) -> {
                System.err.println("Seen: " + arg + (val != null ? " = " + val : ""));
            };
        }

    }

    /**
     * A command line argument that has been found specification.
     * <p>
     * Note: a copy-past from jena-core-3.0.1 {@link jena.rdfcat.Arg}
     */
    protected static class Arg {
        String name;
        String value;
        List<String> values = new ArrayList<>();

        Arg() {
            name = null;
            value = null;
        }

        Arg(String _name) {
            this();
            setName(_name);
        }

        Arg(String _name, String _value) {
            this();
            setName(_name);
            setValue(_value);
        }

        void setName(String n) {
            name = n;
        }

        void setValue(String v) {
            value = v;
        }

        void addValue(String v) {
            values.add(v);
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public List<String> getValues() {
            return values;
        }

        public boolean hasValue() {
            return value != null;
        }

        public boolean matches(ArgDecl decl) {
            return decl.getNames().contains(name);
        }

    }

    /**
     * A command line argument specification.
     */
    protected static class ArgDecl {
        boolean takesValue;
        Set<String> names = new HashSet<>();
        boolean takesArg = false;
        List<BiConsumer<String, String>> argHooks = new ArrayList<>();
        public static final boolean HasValue = true;
        public static final boolean NoValue = false;

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         */

        public ArgDecl(boolean hasValue) {
            takesValue = hasValue;
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name     Name of argument
         */

        public ArgDecl(boolean hasValue, String name) {
            this(hasValue);
            addName(name);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name     Name of argument
         * @param handler  BiConsumer<String, String>
         */

        public ArgDecl(boolean hasValue, String name, BiConsumer<String, String> handler) {
            this(hasValue);
            addName(name);
            addHook(handler);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         */

        public ArgDecl(boolean hasValue, String name1, String name2) {
            this(hasValue);
            addName(name1);
            addName(name2);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         * @param handler  BiConsumer<String, String>
         */

        public ArgDecl(boolean hasValue, String name1, String name2, BiConsumer<String, String> handler) {
            this(hasValue);
            addName(name1);
            addName(name2);
            addHook(handler);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         * @param name3    Name of argument
         */

        public ArgDecl(boolean hasValue, String name1, String name2, String name3) {
            this(hasValue);
            addName(name1);
            addName(name2);
            addName(name3);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         * @param name3    Name of argument
         * @param handler  BiConsumer<String, String>
         */

        public ArgDecl(boolean hasValue, String name1, String name2, String name3, BiConsumer<String, String> handler) {
            this(hasValue);
            addName(name1);
            addName(name2);
            addName(name3);
            addHook(handler);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         * @param name3    Name of argument
         * @param name4    Name of argument
         */

        public ArgDecl(boolean hasValue, String name1, String name2, String name3, String name4) {
            this(hasValue);
            addName(name1);
            addName(name2);
            addName(name3);
            addName(name4);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         * @param name3    Name of argument
         * @param name4    Name of argument
         * @param handler  BiConsumer<String, String>
         */

        public ArgDecl(boolean hasValue, String name1, String name2, String name3, String name4, BiConsumer<String, String> handler) {
            this(hasValue);
            addName(name1);
            addName(name2);
            addName(name3);
            addName(name4);
            addHook(handler);
        }

        /**
         * Create a declaration for a command argument.
         *
         * @param hasValue Does it take a value or not?
         * @param name1    Name of argument
         * @param name2    Name of argument
         * @param name3    Name of argument
         * @param name4    Name of argument
         * @param name5    Name of argument
         * @param handler  BiConsumer<String, String>
         */

        public ArgDecl(boolean hasValue, String name1, String name2, String name3, String name4, String name5, BiConsumer<String, String> handler) {
            this(hasValue);
            addName(name1);
            addName(name2);
            addName(name3);
            addName(name4);
            addName(name5);
            addHook(handler);
        }

        public void addName(String name) {
            name = canonicalForm(name);
            names.add(name);
        }

        public Set<String> getNames() {
            return names;
        }

        public Iterator<String> names() {
            return names.iterator();
        }

        // Callback model

        public void addHook(BiConsumer<String, String> argHandler) {
            argHooks.add(argHandler);
        }

        protected void trigger(Arg arg) {
            argHooks.forEach(action -> action.accept(arg.getName(), arg.getValue()));
        }

        public boolean takesValue() {
            return takesValue;
        }

        public boolean matches(Arg a) {
            for (String n : names) {
                if (a.getName().equals(n)) {
                    return true;
                }
            }
            return false;
        }

        public boolean matches(String arg) {
            arg = canonicalForm(arg);
            return names.contains(arg);
        }

        static String canonicalForm(String str) {
            if (str.startsWith("--"))
                return str.substring(2);

            if (str.startsWith("-"))
                return str.substring(1);

            return str;
        }
    }
}
