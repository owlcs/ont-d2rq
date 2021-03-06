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
import java.util.*;
import java.util.stream.Collectors;

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
    private Properties properties;
    private String sqlScript;
    private boolean generateDirectMapping;
    private String jdbcURL;
    private String mappingFile;
    private String baseURI;
    private String resourceStem = "";
    private Filter filter;
    private boolean fastMode;
    private boolean useOWLControl;
    private boolean withCache;
    private boolean withSchema = true;
    private boolean withAnonymousIndividuals;

    private ConnectedDB connectedDB;

    /**
     * Enables/disables generating anonymous individuals.
     * Makes sense only if {@link #generateDirectMapping} is {@code false} and {@link #mappingFile} is {@code null}.
     *
     * @param b boolean, {@code true} to enable anonymous individuals
     * @return this instance
     * @see MappingGenerator#setRequirePrimaryKey(boolean)
     */
    public SystemLoader withAnonymousIndividuals(boolean b) {
        this.withAnonymousIndividuals = b;
        return this;
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

    /**
     * Sets the base URI, which will be used to generate resource URIs.
     *
     * @param baseURI String, must be absolute, not {@code null}
     * @return this instance to allow cascading calls
     * @throws D2RQException        in case the URI is not absolute
     * @throws NullPointerException null input
     */
    public SystemLoader setSystemBaseURI(String baseURI) {
        if (!URI.create(baseURI).isAbsolute()) {
            throw new D2RQException("Base URI '" + baseURI + "' must be an absolute URI",
                    D2RQException.STARTUP_BASE_URI_NOT_ABSOLUTE);
        }
        this.baseURI = baseURI;
        return this;
    }

    /**
     * Sets the {@code withCache} parameter to the desired state.
     *
     * @param flag boolean
     * @return this instance to allow cascading calls
     * @see Configuration#getWithCache()
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#withCache
     */
    public SystemLoader setWithCache(boolean flag) {
        this.withCache = flag;
        return this;
    }

    /**
     * Sets the {@code useOWLControl} parameter to the desired state.
     *
     * @param flag boolean
     * @return this instance to allow cascading calls
     * @see Configuration#getControlOWL()
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#controlOWL
     */
    public SystemLoader setControlOWL(boolean flag) {
        this.useOWLControl = flag;
        return this;
    }

    /**
     * Sets the {@code fastMode} parameter to the desired state.
     *
     * @param flag boolean
     * @return this instance to allow cascading calls
     */
    public SystemLoader setFastMode(boolean flag) {
        this.fastMode = flag;
        return this;
    }

    /**
     * Sets the {@code withSchema} parameter to the desired state.
     *
     * @param flag boolean
     * @return this instance to allow cascading calls
     */
    public SystemLoader setServeVocabulary(boolean flag) {
        this.withSchema = flag;
        return this;
    }

    /**
     * Sets the {@code generateDirectMapping} parameter to the desired state.
     * If it is {@code true}, the {@link W3CMappingGenerator W3C Mapping Generator} is used,
     * otherwise {@link MappingGenerator Default D2RQ Mapping Generator} is used.
     *
     * @param flag boolean
     * @return this instance to allow cascading calls
     */
    public SystemLoader setGenerateW3CDirectMapping(boolean flag) {
        this.generateDirectMapping = flag;
        return this;
    }

    /**
     * Sets the resource file URL to the mapping.
     * Either JDBC URI or mapping URL must be specified.
     *
     * @param mappingURL String
     * @return this instance to allow cascading calls
     * @see #setJdbcURL(String)
     * @see #setMappingFileOrJdbcURL(String)
     */
    public SystemLoader setMappingURL(String mappingURL) {
        this.mappingFile = mappingURL;
        return this;
    }

    /**
     * Sets the JDBC connection string.
     * Either JDBC URI or mapping URL must be specified.
     *
     * @param jdbcURL String, not {@code null}
     * @return this instance to allow cascading calls
     * @see #setMappingURL(String)
     * @see #setMappingFileOrJdbcURL(String)
     */
    public SystemLoader setJdbcURL(String jdbcURL) {
        this.jdbcURL = Objects.requireNonNull(jdbcURL);
        return this;
    }

    /**
     * Sets required reference to resource (DB or mapping file).
     *
     * @param value String, not {@code null}
     * @return this instance to allow cascading calls
     * @see #setMappingURL(String)
     * @see #setJdbcURL(String)
     */
    public SystemLoader setMappingFileOrJdbcURL(String value) {
        if (Objects.requireNonNull(value).toLowerCase().startsWith("jdbc:")) {
            return setJdbcURL(value);
        }
        return setMappingURL(value);
    }

    /**
     * Sets the {@code resultSizeLimit} parameter to the desired value.
     * @param value int
     * @return this instance to allow cascading calls
     */
    public SystemLoader setResultSizeLimit(int value) {
        this.resultSizeLimit = value;
        return this;
    }

    /**
     * Sets the {@code fetchSize} parameter to the desired value.
     * @param value int
     * @return this instance to allow cascading calls
     */
    public SystemLoader setFetchSize(int value) {
        this.fetchSize = value;
        return this;
    }

    /**
     * Sets JDBC connection properties.
     * @param properties {@link Properties}
     * @return this instance to allow cascading calls
     */
    public SystemLoader setConnectionProperties(Properties properties) {
        this.properties = properties;
        return this;
    }

    /**
     * Sets a username if required by the database.
     *
     * @param username String
     * @return this instance to allow cascading calls
     */
    public SystemLoader setUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Sets a password if required by the database.
     *
     * @param password String
     * @return this instance to allow cascading calls
     */
    public SystemLoader setPassword(String password) {
        this.password = password;
        return this;
    }

    /**
     * Sets the {@link Filter}.
     *
     * @param filter not {@code null}
     * @return this instance to allow cascading calls
     */
    public SystemLoader setFilter(Filter filter) {
        this.filter = Objects.requireNonNull(filter);
        return this;
    }

    /**
     * Sets the JDBC driver class path.
     *
     * @param driver not {@code null}
     * @return this instance to allow cascading calls
     */
    public SystemLoader setJDBCDriverClass(String driver) {
        this.jdbcDriverClass = driver;
        return this;
    }

    /**
     * Sets the database initialization script.
     *
     * @param sqlFile a valid path to script file
     * @return this instance to allow cascading calls
     */
    public SystemLoader setStartupSQLScript(String sqlFile) {
        this.sqlScript = sqlFile;
        return this;
    }

    /**
     * @return Base URI where the server is assumed to run
     * @see #setSystemBaseURI(String)
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
                Collections.emptyMap(), resultSizeLimit, fetchSize, properties);
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
        res.setRequirePrimaryKey(!withAnonymousIndividuals);
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

    /**
     * Builds a {@link Mapping} taking into account various {@link SystemLoader}'s settings.
     * Note: if the builder contains both {@code mappingFile} and JDBC settings,
     * then the mapping will be loaded from the file and then, if possible,
     * adjusted according to the other JDBC settings.
     *
     * @return {@link Mapping}
     */
    public Mapping build() {
        Mapping res = fetchMapping();
        res.getConfiguration()
                .setWithCache(withCache)
                .setControlOWL(useOWLControl)
                .setUseAllOptimizations(fastMode)
                .setServeVocabulary(withSchema);
        // in case jdbc-parameters are also present, pass them into the mapping
        Optional<Database> ods = Optional.empty();
        if (jdbcURL != null) {
            ods = res.database(jdbcURL);
        } else {
            // then choose first if it is single
            Set<Database> dbs = res.databases().collect(Collectors.toSet());
            if (dbs.size() == 1) {
                ods = Optional.of(dbs.iterator().next());
            }
        }
        ods.ifPresent(d -> {
            if (properties != null) {
                d.addConnectionProperties(properties);
            }
            if (resultSizeLimit != Database.NO_LIMIT) {
                d.setResultSizeLimit(resultSizeLimit);
            }
            if (fetchSize != Database.NO_FETCH_SIZE) {
                d.setFetchSize(fetchSize);
            }
            if (username != null) {
                d.setUsername(username);
            }
            if (password != null) {
                d.setPassword(password);
            }
        });
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
        if (jdbcURL == null && mappingFile == null) {
            throw new D2RQException("no mapping file or JDBC URL specified");
        }
        String baseURI = getResourceBaseURI();
        if (jdbcURL != null && mappingFile == null) {
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
