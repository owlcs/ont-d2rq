package d2rq;

import d2rq.utils.ArgDecl;
import d2rq.utils.CommandLine;
import d2rq.utils.LogHelper;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.SystemLoader;
import de.fuberlin.wiwiss.d2rq.mapgen.Filter;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterIncludeExclude;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterMatchAny;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterParser;
import de.fuberlin.wiwiss.d2rq.mapgen.FilterParser.ParseException;
import org.apache.jena.shared.JenaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;


/**
 * Base class for the D2RQ command line tools.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public abstract class CommandLineTool {

    static final Logger LOGGER = LoggerFactory.getLogger(CommandLineTool.class);
    static final String INDIRECTION_MARKER = "@";

    protected final PrintStream console;
    protected final CommandLine cmd;
    protected final SystemLoader loader;
    protected final boolean supportImplicitJdbcURL;
    protected final int minArguments;
    protected final int maxArguments;

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

    protected CommandLineTool(PrintStream out) {
        this(out, 0, 1);
    }

    protected CommandLineTool(PrintStream out, int minArguments, int maxArguments) {
        this(out, minArguments, maxArguments, true);
    }

    protected CommandLineTool(PrintStream out, int minArguments, int maxArguments, boolean implicitJdbcURL) {
        this.console = Objects.requireNonNull(out);
        this.loader = new SystemLoader();
        this.cmd = new CommandLine();
        this.supportImplicitJdbcURL = implicitJdbcURL;
        this.minArguments = minArguments;
        this.maxArguments = maxArguments;
    }

    private static String getMessage(Exception ex) {
        String res = null;
        if (ex instanceof D2RQException) {
            Throwable cause;
            if (ex.getMessage() == null && (cause = ex.getCause()) != null && cause.getMessage() != null) {
                res = cause.getMessage();
                if (cause instanceof SQLException) {
                    res = "SQL error " + res;
                }
            }
        }
        if (res == null) {
            res = ex.getMessage();
        }
        return res;
    }

    private static String withIndirection(String value) {
        return CommandLine.withIndirection(value, INDIRECTION_MARKER);
    }

    public abstract void printUsage();

    public abstract void initArgs();

    public abstract void run() throws D2RQException, IOException;

    /**
     * Runs process.
     *
     * @param args array of arguments
     * @throws Exit with error code inside
     */
    void process(String[] args) throws Exit {
        if (args.length == 0 || CommandLine.isHelpOption(args[args.length - 1])) {
            printUsage();
            throw new Exit(Exit.Code.USAGE);
        }
        initCommonArgs();
        initArgs();
        try {
            cmd.process(args);
        } catch (IllegalArgumentException ex) {
            console.println(ex.getMessage());
            throw new Exit(Exit.Code.WRONG_INPUT);
        }
        setLogging();
        if (cmd.numItems() == minArguments && supportImplicitJdbcURL && cmd.contains(sqlFileArg)) {
            loader.setJdbcURL(SystemLoader.DEFAULT_JDBC_URL);
        } else if (cmd.numItems() == 0) {
            printUsage();
            throw new Exit(Exit.Code.USAGE);
        }
        validateItems();
        if (cmd.contains(userArg)) {
            loader.setUsername(cmd.getArgValue(userArg));
        }
        if (cmd.contains(passArg)) {
            loader.setPassword(cmd.getArgValue(passArg));
        }
        if (cmd.contains(driverArg)) {
            loader.setJDBCDriverClass(cmd.getArgValue(driverArg));
        }
        if (cmd.contains(sqlFileArg)) {
            loader.setStartupSQLScript(cmd.getArgValue(sqlFileArg));
        }
        if (cmd.contains(w3cArg)) {
            loader.setGenerateW3CDirectMapping(true);
        }
        parseFilters();
        try {
            run();
        } catch (IOException | JenaException ex) {
            console.println(getMessage(ex));
            LOGGER.info("Command line tool exception", ex);
            throw new Exit(ex);
        }
    }

    private void initCommonArgs() {
        cmd.add(userArg)
                .add(passArg)
                .add(driverArg)
                .add(sqlFileArg)
                .add(w3cArg)
                .add(verboseArg)
                .add(debugArg)
                .add(schemasArg)
                .add(tablesArg)
                .add(columnsArg)
                .add(skipSchemasArg)
                .add(skipTablesArg)
                .add(skipColumnsArg);
    }

    private void setLogging() {
        if (cmd.contains(verboseArg)) {
            LogHelper.setVerboseLogging();
        }
        if (cmd.contains(debugArg)) {
            LogHelper.setDebugLogging();
        }
    }

    private void validateItems() throws Exit {
        String error = null;
        if (cmd.numItems() < minArguments) {
            error = "Not enough arguments";
        } else if (cmd.numItems() > maxArguments) {
            error = "Too many arguments";
        }
        if (error != null) {
            console.println(error);
            throw new Exit(Exit.Code.WRONG_INPUT);
        }
    }

    private void parseFilters() throws Exit {
        try {
            Collection<Filter> includes = new ArrayList<>();
            Collection<Filter> excludes = new ArrayList<>();
            if (cmd.contains(schemasArg)) {
                String spec = withIndirection(cmd.getArgValue(schemasArg));
                includes.add(new FilterParser(spec).parseSchemaFilter());
            }
            if (cmd.contains(tablesArg)) {
                String spec = withIndirection(cmd.getArgValue(tablesArg));
                includes.add(new FilterParser(spec).parseTableFilter(true));
            }
            if (cmd.contains(columnsArg)) {
                String spec = withIndirection(cmd.getArgValue(columnsArg));
                includes.add(new FilterParser(spec).parseColumnFilter(true));
            }
            if (cmd.contains(skipSchemasArg)) {
                String spec = withIndirection(cmd.getArgValue(skipSchemasArg));
                excludes.add(new FilterParser(spec).parseSchemaFilter());
            }
            if (cmd.contains(skipTablesArg)) {
                String spec = withIndirection(cmd.getArgValue(skipTablesArg));
                excludes.add(new FilterParser(spec).parseTableFilter(false));
            }
            if (cmd.contains(skipColumnsArg)) {
                String spec = withIndirection(cmd.getArgValue(skipColumnsArg));
                excludes.add(new FilterParser(spec).parseColumnFilter(false));
            }
            if (!includes.isEmpty() || !excludes.isEmpty()) {
                loader.setFilter(new FilterIncludeExclude(
                        includes.isEmpty() ? Filter.ALL : FilterMatchAny.create(includes),
                        FilterMatchAny.create(excludes)));
            }
        } catch (ParseException | IllegalArgumentException ex) {
            console.println(ex.getMessage());
            throw new Exit(Exit.Code.WRONG_INPUT);
        }
    }

    protected void printStandardArguments(boolean withMappingFile) {
        console.println("  Arguments:");
        if (withMappingFile) {
            console.println("    mappingFile     Filename or URL of a D2RQ mapping file");
        }
        console.println("    jdbcURL         JDBC URL for the DB, e.g. jdbc:mysql://localhost/dbname");
        if (supportImplicitJdbcURL) {
            console.println("                    (If omitted with -l, set up a temporary in-memory DB)");
        }
    }

    protected void printConnectionOptions() {
        console.println("    -u username     Database user for connecting to the DB");
        console.println("    -p password     Database password for connecting to the DB");
        console.println("    -d driverclass  Java class name of the JDBC driver for the DB");
        console.println("    -l script.sql   Load a SQL script before processing");
        console.println("    --w3c           Produce W3C Direct Mapping compatible mapping file");
        console.println("    --[skip-](schemas|tables|columns) [schema.]table[.column]");
        console.println("                    Include or exclude specific database objects");
    }

    public static class Exit extends RuntimeException {
        private final Code code;

        public Exit(Code code) {
            this.code = code;
        }

        public Exit(Throwable cause) {
            super(cause);
            this.code = Code.ERROR;
        }

        public int getCode() {
            return code.ordinal() + 1;
        }

        public enum Code {
            USAGE,
            WRONG_INPUT,
            ERROR,
            ;
        }
    }
}
