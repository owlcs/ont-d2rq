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
import org.apache.jena.util.FileManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;


/**
 * Base class for the D2RQ command line tools.
 * They share much of their argument list and functionality, therefore this is extracted into this superclass.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public abstract class CommandLineTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandLineTool.class);

    protected final PrintStream console;

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

    protected CommandLineTool(PrintStream out) {
        this.console = Objects.requireNonNull(out);
    }

    private static String withIndirection(String value) {
        if (value.startsWith("@")) {
            value = value.substring(1);
            try {
                value = FileManager.get().readWholeFileAsUTF8(value);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Failed to read '" + value + "': " + ex.getMessage(), ex);
            }
        }
        return value;
    }

    static boolean isHelpOption(String arg) {
        return Stream.of("-h", "--h", "-help", "--help", "/?").anyMatch(h -> h.equalsIgnoreCase(arg));
    }

    public abstract void usage() throws Exit;

    public abstract void initArgs(CommandLine cmd);

    public abstract void run(CommandLine cmd, SystemLoader loader) throws D2RQException, IOException;

    protected void setMinMaxArguments(int min, int max) {
        minArguments = min;
        maxArguments = max;
    }

    protected void setSupportImplicitJdbcURL(boolean flag) {
        supportImplicitJdbcURL = flag;
    }

    public void process(String[] args) throws Exit {
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

        initArgs(cmd);

        try {
            cmd.process(args);
        } catch (IllegalArgumentException ex) {
            reportException(ex);
        }

        if (cmd.hasArg(verboseArg)) {
            LogHelper.setVerboseLogging();
        }
        if (cmd.hasArg(debugArg)) {
            LogHelper.setDebugLogging();
        }

        if (cmd.numItems() == minArguments && supportImplicitJdbcURL && cmd.hasArg(sqlFileArg)) {
            loader.setJdbcURL(SystemLoader.DEFAULT_JDBC_URL);
        } else if (cmd.numItems() == 0 || isHelpOption(args[0])) {
            usage();
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
        } catch (IllegalArgumentException | ParseException | IOException | JenaException ex) {
            reportException(ex);
        }
    }

    private void reportException(Exception ex) {
        console.println(getMessage(ex));
        LOGGER.error("Command line tool exception", ex);
        throw new Exit(2, ex);
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
        private final int code;

        public Exit(int code) {
            this.code = code;
        }

        public Exit(int code, Throwable cause) {
            super(cause);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
