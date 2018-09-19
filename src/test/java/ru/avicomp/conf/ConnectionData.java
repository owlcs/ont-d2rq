package ru.avicomp.conf;

import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;
import org.apache.jena.rdf.model.ResourceFactory;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.D2RQGraphDocumentSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

/**
 * Created by @szuev on 29.03.2018.
 */
public enum ConnectionData {
    /**
     * to set up use <a href='file:doc/example/iswc-mysql.sql'>iswc-mysql.sql</a>
     */
    MYSQL,
    /**
     * to set up use <a href='file:doc/example/iswc-postgres.sql'>iswc-postgres.sql</a>
     */
    POSTGRES {
        /**
         * For postgres 9.6
         * @param statement {@link Statement}, not {@code null}
         * @param databaseName DB name, not {@code null}
         * @throws SQLException SQL error happens
         * @see <a href='https://dba.stackexchange.com/questions/11893/force-drop-db-while-others-may-be-connected'>ps force drop</a>
         */
        @Override
        protected void beforeDrop(Statement statement, String databaseName) throws SQLException {
            statement.executeUpdate(String.format("ALTER DATABASE %s CONNECTION LIMIT 0;", databaseName));
            statement.executeQuery(String.format("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '%s'", databaseName));
        }

        @Override
        protected String fixIRI(String iri) {
            return iri.toLowerCase();
        }
    },
    ;

    public static final IRI DEFAULT_BASE_IRI = IRI.create("http://d2rq.avc.ru/test/");

    private static final Properties PROPERTIES = load("/db.properties");
    private IRI base;

    public IRI getJdbcBaseIRI() {
        if (base != null) return base;
        String str = PROPERTIES.getProperty(prefix() + "uri");
        if (!str.endsWith("/")) str += "/";
        return base = IRI.create(str);
    }

    public IRI getJdbcIRI(String databaseName) {
        return IRI.create(getJdbcBaseIRI() + Objects.requireNonNull(databaseName));
    }

    public String getUser() {
        return PROPERTIES.getProperty(prefix() + "user");
    }

    public String getPwd() {
        return PROPERTIES.getProperty(prefix() + "password");
    }

    private String prefix() {
        return String.format("%s.", name().toLowerCase());
    }

    public D2RQGraphDocumentSource toDocumentSource(String dbName) {
        return toDocumentSource(DEFAULT_BASE_IRI, dbName);
    }

    public D2RQGraphDocumentSource toDocumentSource(IRI base, String dbName) {
        return D2RQGraphDocumentSource.create(base, getJdbcIRI(dbName), getUser(), getPwd());
    }

    /**
     * Returns the given uri updated to the database requirements.
     *
     * @param uri String, not {@code null}
     * @return {@link IRI}
     */
    public IRI toIRI(String uri) {
        return IRI.create(fixIRI(uri));
    }

    /**
     * Returns the given uri updated to the database requirements.
     *
     * @param iri String, not {@code null}
     * @return String, not {@code null}
     */
    protected String fixIRI(String iri) {
        return iri;
    }

    public ConnectedDB toConnectedDB() {
        return new ConnectedDB(getJdbcBaseIRI().getIRIString(), getUser(), getPwd());
    }

    public ConnectedDB toConnectedDB(String dbName) {
        return new ConnectedDB(getJdbcIRI(dbName).getIRIString(), getUser(), getPwd());
    }

    /**
     * Loads properties, first from System, then from the given file.
     *
     * @param fileResource path
     * @return {@link Properties}
     */
    public static Properties load(String fileResource) {
        Properties fromFile = new Properties();
        try (InputStream in = ConnectionData.class.getResourceAsStream(fileResource)) {
            fromFile.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Properties res = new Properties(fromFile);
        System.getProperties().forEach((key, val) -> {
            if (!(key instanceof String)) return;
            String str = (String) key;
            if (Arrays.stream(values()).map(ConnectionData::prefix).anyMatch(str::startsWith)) {
                res.put(key, val);
            }
        });
        return res;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionData.class);

    /**
     * Creates database from the given resource script.
     *
     * @param scriptResource path to script resource, not {@code null}
     * @param databaseName   DB name, not {@code null}
     * @throws Exception Can't read, load or process SQL script
     */
    public void createDatabase(String scriptResource, String databaseName) throws Exception {
        Objects.requireNonNull(databaseName, "Null database name");
        Path script = Paths.get(ConnectionData.class.getResource(Objects.requireNonNull(scriptResource, "Null script")).toURI());
        try (ConnectedDB db = toConnectedDB();
             Connection conn = db.connection()) {
            conn.setAutoCommit(true);
            try (Statement s = conn.createStatement()) {
                LOGGER.info("Create database '{}'", databaseName);
                s.executeUpdate(String.format("CREATE DATABASE %s", databaseName));
            }
        }
        try (ConnectedDB db = toConnectedDB(databaseName);
             Connection conn = db.connection();
             Reader reader = Files.newBufferedReader(script, StandardCharsets.UTF_8);
             SQLScriptLoader loader = new SQLScriptLoader(reader, conn)) {
            conn.setAutoCommit(false);
            LOGGER.info("Execute script: {}", script);
            loader.execute();
            conn.commit();
        }
        LOGGER.info("The database '{}' has been created.", databaseName);
    }

    /**
     * Deletes the given database.
     *
     * @param databaseName DB name, not {@code null}
     * @throws SQLException SQL error happens
     */
    public void dropDatabase(String databaseName) throws SQLException {
        Objects.requireNonNull(databaseName, "Null database name");
        try (ConnectedDB db = toConnectedDB();
             Connection conn = db.connection()) {
            conn.setAutoCommit(true);
            try (Statement s = conn.createStatement()) {
                LOGGER.info("Drop database <{}>", databaseName);
                beforeDrop(s, databaseName);
                s.executeUpdate(String.format("DROP DATABASE %s;", databaseName));
            }
        }
        LOGGER.info("The database '{}' has been deleted.", databaseName);
    }

    /**
     * Performs final actions before DB dropping.
     *
     * @param statement    {@link Statement}, not {@code null}
     * @param databaseName DB name, not {@code null}
     * @throws SQLException SQL error happens
     */
    protected void beforeDrop(Statement statement, String databaseName) throws SQLException {
    }


    /**
     * Creates a fresh database {@link de.fuberlin.wiwiss.d2rq.map.MapObject}.
     *
     * @param uri  String, not {@code null}
     * @param name String, not {@code null}
     * @return {@link Database}
     */
    public Database createDatabaseMapObject(String uri, String name) {
        Database res = new Database(ResourceFactory.createResource(Objects.requireNonNull(uri, "Null uri")));
        res.setJDBCDSN(getJdbcIRI(Objects.requireNonNull(name, "Null name")).getIRIString());
        res.setJDBCDriver(getDeclaringClass().getName());
        res.setUsername(getUser());
        res.setPassword(getPwd());
        return res;
    }
}
