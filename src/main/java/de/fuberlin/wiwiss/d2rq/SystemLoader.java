package de.fuberlin.wiwiss.d2rq;

import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.mapgen.Filter;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;
import org.apache.jena.atlas.AtlasException;
import org.apache.jena.n3.turtle.TurtleParseException;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shared.JenaException;
import org.apache.jena.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.MalformedInputException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Objects;

/**
 * Builder for MappingGenerators, ModelD2RQs and the like.
 * Many of these artifacts can be configured in multiple ways (from the command line, from configuration files, etc.),
 * and creating one may require that others are previously created and configured correctly.
 * This class helps setting everything up correctly.
 * <p>
 * TODO: {@link MapParser#absolutizeURI(String)} and {@code WebappInitListener#absolutize} need to be consolidated and/or folded into this class
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @see MappingGenerator
 * @see MappingFactory
 * @see ConnectedDB
 */
@SuppressWarnings({"UnusedReturnValue", "unused", "WeakerAccess"})
public class SystemLoader implements AutoCloseable {
    private final static Logger LOGGER = LoggerFactory.getLogger(SystemLoader.class);

    public static final String DEFAULT_JDBC_URL = "jdbc:hsqldb:mem:temp";

    private String username;
    private String password;
    private String jdbcDriverClass;
    private int resultSizeLimit = Database.NO_LIMIT;
    private int fetchSize = Database.NO_FETCH_SIZE;
    private String sqlScript;
    private boolean generateDirectMapping;
    private String jdbcURL;
    private String mappingFile;
    private String baseURI;
    private String resourceStem = "";
    private Filter filter;
    private boolean fastMode;

    private ConnectedDB connectedDB;

    public SystemLoader setJdbcURL(String jdbcURL) {
        this.jdbcURL = Objects.requireNonNull(jdbcURL);
        return this;
    }

    public SystemLoader setUsername(String username) {
        this.username = username;
        return this;
    }

    public SystemLoader setPassword(String password) {
        this.password = password;
        return this;
    }

    public SystemLoader setFilter(Filter filter) {
        this.filter = Objects.requireNonNull(filter);
        return this;
    }

    public SystemLoader setJDBCDriverClass(String driver) {
        this.jdbcDriverClass = driver;
        return this;
    }

    public SystemLoader setStartupSQLScript(String sqlFile) {
        this.sqlScript = sqlFile;
        return this;
    }

    public SystemLoader setGenerateW3CDirectMapping(boolean flag) {
        this.generateDirectMapping = flag;
        return this;
    }

    public SystemLoader setMappingFileOrJdbcURL(String value) {
        if (Objects.requireNonNull(value).toLowerCase().startsWith("jdbc:")) {
            return setJdbcURL(value);
        }
        return setMappingURL(value);
    }

    /**
     * By default, the base URI for resolving relative URIs
     * in data is the same as the system base URI where the server
     * is assumed to run.
     * <p>
     * The resource stem can be set to something like <code>resource/</code>
     * in order to put the resources into a subdirectory of the
     * system base.
     *
     * @param value A string relative to the system base URI
     * @return this instance
     */
    public SystemLoader setResourceStem(String value) {
        resourceStem = Objects.requireNonNull(value);
        return this;
    }

    public SystemLoader setSystemBaseURI(String baseURI) {
        if (!URI.create(baseURI).isAbsolute()) {
            throw new D2RQException("Base URI '" + baseURI + "' must be an absolute URI",
                    D2RQException.STARTUP_BASE_URI_NOT_ABSOLUTE);
        }
        this.baseURI = baseURI;
        return this;
    }

    public SystemLoader setFastMode(boolean flag) {
        this.fastMode = flag;
        return this;
    }

    public SystemLoader setMappingURL(String mappingURL) {
        this.mappingFile = mappingURL;
        return this;
    }

    public SystemLoader setResultSizeLimit(int value) {
        this.resultSizeLimit = value;
        return this;
    }

    public SystemLoader setFetchSize(int value) {
        this.fetchSize = value;
        return this;
    }

    /**
     * @return Base URI where the server is assumed to run
     */
    public String getSystemBaseURI() {
        return baseURI == null ? null : MapParser.absolutizeURI(baseURI);
    }

    /**
     * @return Base URI for making relative URIs in the RDF data absolute
     */
    public String getResourceBaseURI() {
        String base = getSystemBaseURI();
        return base != null ? base + resourceStem : resourceStem;
    }

    private ConnectedDB getConnectedDB() {
        return connectedDB == null ? connectedDB = createConnectedDB() : connectedDB;
    }

    private ConnectedDB createConnectedDB() {
        ConnectedDB res = new ConnectedDB(jdbcURL, username, password,
                Collections.emptyMap(), resultSizeLimit, fetchSize, null);
        if (sqlScript != null) {
            try {
                SQLScriptLoader.loadFile(Paths.get(sqlScript), res.connection());
            } catch (IOException ex) {
                res.close();
                throw new D2RQException("Error accessing SQL startup script: " + sqlScript,
                        D2RQException.STARTUP_SQL_SCRIPT_ACCESS);
            } catch (SQLException ex) {
                res.close();
                throw new D2RQException("Error importing " + sqlScript + " " + ex.getMessage(),
                        D2RQException.STARTUP_SQL_SCRIPT_SYNTAX);
            }
        }
        return res;
    }

    /**
     * Returns a mapping generator.
     * A connection needs to be explicitly closed using {@link #close()}.
     *
     * @return {@link MappingGenerator}
     */
    private MappingGenerator getMappingGenerator() {
        ConnectedDB conn = getConnectedDB();
        MappingGenerator res = generateDirectMapping ? new W3CMappingGenerator(conn) : new MappingGenerator(conn);
        if (jdbcDriverClass != null) {
            res.setJDBCDriverClass(jdbcDriverClass);
        }
        if (filter != null) {
            res.setFilter(filter);
        }
        if (sqlScript != null) {
            // If there's a startup SQL script, copy its name into the generated mapping
            res.setStartupSQLScript(Paths.get(sqlScript).toUri());
        }
        return res;
    }

    @Override
    public void close() {
        if (connectedDB == null) {
            return;
        }
        connectedDB.close();
        connectedDB = null;
    }

    public Mapping build() {
        Mapping res = fetchMapping();
        res.getConfiguration().setUseAllOptimizations(fastMode);
        if (fetchSize != Database.NO_FETCH_SIZE || resultSizeLimit != Database.NO_LIMIT) {
            res.listDatabases()
                    .filter(d -> Objects.equals(d.getJDBCDSN(), jdbcURL))
                    .forEach(d -> d.setResultSizeLimit(resultSizeLimit).setFetchSize(fetchSize));
        }
        if (connectedDB != null) {
            // Hack! We don't want the Database to open another ConnectedDB,
            // so we check if it's connected to the same DB, and in that case
            // make it use the existing ConnectedDB that we already have opened.
            // Otherwise we get problems where D2RQ is trying to import a SQL
            // script twice on startup.
            MappingHelper.useConnectedDB(res, connectedDB);
        }
        return res;
    }

    /**
     * Makes a {@link Mapping} using the given settings.
     *
     * @return {@link Mapping} fresh instance, either generated or loaded
     * @throws D2RQException in case some arguments are incorrect
     */
    private Mapping fetchMapping() throws D2RQException {
        if (jdbcURL != null && mappingFile != null) {
            throw new D2RQException("conflicting mapping locations " + mappingFile + " and " + jdbcURL + "; specify at most one");
        }
        if (jdbcURL == null && mappingFile == null) {
            throw new D2RQException("no mapping file or JDBC URL specified");
        }
        String baseURI = getResourceBaseURI();
        if (jdbcURL != null) {
            return MappingFactory.create(getMappingGenerator().mappingModel(baseURI), baseURI);
        }
        LOGGER.info("Reading mapping file from <{}>", mappingFile);
        try {
            return MappingFactory.load(mappingFile, null, baseURI);
        } catch (TurtleParseException e) {
            // We have wired RIOT into Jena in the static initializer above,
            // so this should never happen (it's for the old Jena Turtle/N3 parser)
            throw new D2RQException("Error parsing " + mappingFile + ": " + e.getMessage(), e,
                    D2RQException.MAPPING_TURTLE_SYNTAX);
        } catch (JenaException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RiotException) {
                throw new D2RQException("Error parsing " + mappingFile + ": " + cause.getMessage(), e,
                        D2RQException.MAPPING_TURTLE_SYNTAX);
            }
            throw e;
        } catch (AtlasException e) {
            String lang = FileUtils.guessLang(mappingFile, "unknown");
            // Detect the specific case of non-UTF-8 encoded input files
            // and do a custom error message
            Throwable cause = e.getCause();
            if (FileUtils.langTurtle.equals(lang) && cause instanceof MalformedInputException) {
                throw new D2RQException(String.format("Error parsing %s: Turtle files must be in UTF-8 encoding; " +
                                "bad encoding found at byte %d",
                        mappingFile, ((MalformedInputException) cause).getInputLength()), e,
                        D2RQException.MAPPING_TURTLE_SYNTAX);
            }
            // Generic error message for other parse errors
            throw new D2RQException("Error parsing " + mappingFile + ": " + e.getMessage(), e,
                    D2RQException.MAPPING_TURTLE_SYNTAX);
        }

    }
}
