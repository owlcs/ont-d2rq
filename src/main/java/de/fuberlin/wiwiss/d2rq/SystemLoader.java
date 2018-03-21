package de.fuberlin.wiwiss.d2rq;

import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.MapParser;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.mapgen.Filter;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import de.fuberlin.wiwiss.d2rq.mapgen.W3CMappingGenerator;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;
import org.apache.jena.atlas.AtlasException;
import org.apache.jena.n3.turtle.TurtleParseException;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RiotException;
import org.apache.jena.shared.JenaException;
import org.apache.jena.system.JenaSystem;
import org.apache.jena.util.FileManager;
import org.apache.jena.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.MalformedInputException;
import java.sql.SQLException;

/**
 * Factory for MappingGenerators, ModelD2RQs and the like.
 * Many of these artifacts can be configured in multiple ways
 * (from the command line, from configuration files, etc.), and
 * creating one may require that others are previously created
 * and configured correctly. This class helps setting everything
 * up correctly.
 * <p>
 * TODO: {@link MapParser#absolutizeURI(String)} and {WebappInitListener#absolutize} need to be consolidated and/or folded into this class
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class SystemLoader {

    static {
        JenaSystem.init();    // Wire RIOT into Jena, etc.
    }

    private final static Logger LOGGER = LoggerFactory.getLogger(SystemLoader.class);

    public static final String DEFAULT_JDBC_URL = "jdbc:hsqldb:mem:temp";

    private String username = null;
    private String password = null;
    private String jdbcDriverClass = null;
    private String sqlScript = null;
    private boolean generateDirectMapping = false;
    private String jdbcURL = null;
    private String mappingFile = null;
    private String baseURI = null;
    private String resourceStem = "";
    private Filter filter = null;
    private boolean fastMode = false;
    private int resultSizeLimit = Database.NO_LIMIT;

    private ConnectedDB connectedDB = null;
    private Mapping mapping = null;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public void setJDBCDriverClass(String driver) {
        this.jdbcDriverClass = driver;
        ConnectedDB.registerJDBCDriver(driver);
    }

    public void setStartupSQLScript(String sqlFile) {
        this.sqlScript = sqlFile;
    }

    public void setGenerateW3CDirectMapping(boolean flag) {
        this.generateDirectMapping = flag;
    }

    public void setJdbcURL(String jdbcURL) {
        this.jdbcURL = jdbcURL;
    }

    public void setMappingFileOrJdbcURL(String value) {
        if (value.toLowerCase().startsWith("jdbc:")) {
            jdbcURL = value;
        } else {
            mappingFile = value;
        }
    }

    public void setSystemBaseURI(String baseURI) {
        if (!URI.create(baseURI).isAbsolute()) {
            throw new D2RQException("Base URI '" + baseURI + "' must be an absolute URI", D2RQException.STARTUP_BASE_URI_NOT_ABSOLUTE);
        }
        this.baseURI = baseURI;
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
     */
    public void setResourceStem(String value) {
        resourceStem = value;
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

    public void setFastMode(boolean flag) {
        this.fastMode = flag;
    }

    public void setMappingURL(String mappingURL) {
        this.mappingFile = mappingURL;
    }

    public void setResultSizeLimit(int value) {
        this.resultSizeLimit = value;
    }

    private ConnectedDB getConnectedDB() {
        return connectedDB == null ? connectedDB = createConnectedDB() : connectedDB;
    }

    private ConnectedDB createConnectedDB() {
        ConnectedDB connectedDB = new ConnectedDB(jdbcURL, username, password);
        if (sqlScript != null) {
            try {
                SQLScriptLoader.loadFile(new File(sqlScript), connectedDB.connection());
            } catch (IOException ex) {
                connectedDB.close();
                throw new D2RQException("Error accessing SQL startup script: " + sqlScript, D2RQException.STARTUP_SQL_SCRIPT_ACCESS);
            } catch (SQLException ex) {
                connectedDB.close();
                throw new D2RQException("Error importing " + sqlScript + " " + ex.getMessage(), D2RQException.STARTUP_SQL_SCRIPT_SYNTAX);
            }
        }
        return connectedDB;
    }

    /**
     * Returns a mapping generator. Needs to be explicitly closed
     * using {@link #closeMappingGenerator()}.
     */
    private MappingGenerator openMappingGenerator() {
        ConnectedDB connection = getConnectedDB();
        MappingGenerator generator = generateDirectMapping ? new W3CMappingGenerator(connection) : new MappingGenerator(connection);
        if (jdbcDriverClass != null) {
            generator.setJDBCDriverClass(jdbcDriverClass);
        }
        if (filter != null) {
            generator.setFilter(filter);
        }
        if (sqlScript != null) {
            // If there's a startup SQL script, copy its name into the generated mapping
            generator.setStartupSQLScript(new File(sqlScript).toURI());
        }
        return generator;
    }

    public void closeMappingGenerator() {
        if (connectedDB != null) {
            connectedDB.close();
        }
    }

    private Model fetchMappingModel() {
        Model mapModel;
        if (jdbcURL != null && mappingFile != null) {
            throw new D2RQException("conflicting mapping locations " + mappingFile + " and " + jdbcURL + "; specify at most one");
        }
        if (jdbcURL == null && mappingFile == null) {
            throw new D2RQException("no mapping file or JDBC URL specified");
        }
        if (jdbcURL != null) {
            mapModel = openMappingGenerator().mappingModel(getResourceBaseURI());
        } else {
            LOGGER.info("Reading mapping file from " + mappingFile);
            // Guess the language/type of mapping file based on file extension. If it is not among the known types then assume that the file has TURTLE syntax and force to use TURTLE parser
            String lang = FileUtils.guessLang(mappingFile, "unknown");
            try {
                if (lang.equals("unknown")) {
                    mapModel = FileManager.get().loadModel(mappingFile, getResourceBaseURI(), "TURTLE");
                } else {
                    // if the type is known then let Jena auto-detect it and load the appropriate parser
                    mapModel = FileManager.get().loadModel(mappingFile, getResourceBaseURI(), null);
                }
            } catch (TurtleParseException ex) {
                // We have wired RIOT into Jena in the static initializer above,
                // so this should never happen (it's for the old Jena Turtle/N3 parser)
                throw new D2RQException("Error parsing " + mappingFile + ": " + ex.getMessage(), ex, 77);
            } catch (JenaException ex) {
                if (ex.getCause() != null && ex.getCause() instanceof RiotException) {
                    throw new D2RQException("Error parsing " + mappingFile + ": " + ex.getCause().getMessage(), ex, 77);
                }
                throw ex;
            } catch (AtlasException ex) {
                // Detect the specific case of non-UTF-8 encoded input files
                // and do a custom error message
                if (FileUtils.langTurtle.equals(lang) && ex.getCause() != null && (ex.getCause() instanceof MalformedInputException)) {
                    throw new D2RQException(String.format("Error parsing %s: Turtle files must be in UTF-8 encoding; bad encoding found at byte %d",
                            mappingFile, ((MalformedInputException) ex.getCause()).getInputLength()), ex, 77);
                }
                // Generic error message for other parse errors
                throw new D2RQException("Error parsing " + mappingFile + ": " + ex.getMessage(), ex, 77);
            }
        }
        return mapModel;
    }

    public Mapping getMapping() {
        return mapping == null ? mapping = createMapping() : mapping;
    }

    private Mapping createMapping() {
        Mapping res = MappingFactory.create(fetchMappingModel(), getResourceBaseURI());
        res.configuration().setUseAllOptimizations(fastMode);
        if (connectedDB != null) {
            // Hack! We don't want the Database to open another ConnectedDB,
            // so we check if it's connected to the same DB, and in that case
            // make it use the existing ConnectedDB that we already have opened.
            // Otherwise we get problems where D2RQ is trying to import a SQL
            // script twice on startup.
            for (Database db : res.databases()) {
                if (db.getJDBCDSN().equals(connectedDB.getJdbcURL())) {
                    if (resultSizeLimit != Database.NO_LIMIT) {
                        db.setResultSizeLimit(resultSizeLimit);
                    }
                    db.useConnectedDB(connectedDB);
                }
            }
        }
        return res;
    }
}
