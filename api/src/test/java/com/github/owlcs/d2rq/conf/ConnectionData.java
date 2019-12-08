package com.github.owlcs.d2rq.conf;

import com.github.owlcs.d2rq.D2RQGraphDocumentSource;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.impl.MapObjectImpl;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by @szuev on 29.03.2018.
 *
 * @see <a href='file:resources/db.properties'>db.properties</a>
 */
public enum ConnectionData {
    /**
     * to set up DB use <a href='file:doc/example/iswc-mysql.sql'>iswc-mysql.sql</a>
     */
    MYSQL {
        @Override
        public String getDefaultDriver() {
            return "com.mysql.jdbc.Driver";
        }
    },
    /**
     * to set up DB use <a href='file:doc/example/iswc-postgres.sql'>iswc-postgres.sql</a>
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
        public String getDefaultDriver() {
            return "org.postgresql.Driver";
        }

        @Override
        public String fixIRI(String iri) {
            return iri.toLowerCase();
        }

    },
    ;

    public static final IRI DEFAULT_BASE_IRI = IRI.create("http://d2rq.avc.ru/test/");

    private static final Properties PROPERTIES = loadProperties("/db.properties");
    private String base;
    private Properties connectionProperties;

    public String getBase() {
        if (base != null) return base;
        String str = PROPERTIES.getProperty(prefix() + "uri");
        if (!str.endsWith("/")) str += "/";
        return base = str;
    }

    public String getJdbcURI(String databaseName) {
        return getBase() + Objects.requireNonNull(databaseName);
    }

    /**
     * Gets full JDBC connection string with login credentials.
     *
     * @param databaseName the name for database
     * @return String
     */
    public String getJdbcConnectionString(String databaseName) {
        String res = getJdbcURI(databaseName);
        String u = getUser();
        String p = getPwd();
        String delimiter = "?";
        if (u != null && !u.isEmpty()) {
            res = res + delimiter + "user=" + u;
            delimiter = "&";
        }
        if (p != null && !p.isEmpty()) {
            res = res + delimiter + "password=" + p;
        }
        return res;
    }

    public String getUser() {
        return PROPERTIES.getProperty(prefix() + "user");
    }

    public String getPwd() {
        return PROPERTIES.getProperty(prefix() + "password");
    }

    public Properties getConnectionProperties() {
        return connectionProperties == null ? connectionProperties = parseConnectionProperties() : connectionProperties;
    }

    private Properties parseConnectionProperties() {
        String prefix = prefix() + "properties.";
        Properties res = new Properties();
        PROPERTIES.stringPropertyNames().stream()
                .filter(k -> k.startsWith(prefix))
                .forEach(key -> res.put(key.replace(prefix, ""), PROPERTIES.getProperty(key)));
        return res;
    }

    public String getDriver() {
        String k = prefix() + "driver";
        return PROPERTIES.containsKey(k) ? PROPERTIES.getProperty(k) : getDefaultDriver();
    }

    private String prefix() {
        return String.format("%s.", name().toLowerCase());
    }

    abstract String getDefaultDriver();

    public D2RQGraphDocumentSource toDocumentSource(String dbName) {
        return toDocumentSource(DEFAULT_BASE_IRI, dbName);
    }

    public D2RQGraphDocumentSource toDocumentSource(IRI base, String dbName) {
        return D2RQGraphDocumentSource.create(base, IRI.create(getJdbcURI(dbName)),
                getUser(), getPwd(), getConnectionProperties());
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
    public String fixIRI(String iri) {
        return iri;
    }

    public ConnectedDB toConnectedDB() {
        return createConnectedDB(getBase());
    }

    public ConnectedDB toConnectedDB(String dbName) {
        return createConnectedDB(getJdbcURI(dbName));
    }

    private ConnectedDB createConnectedDB(String uri) {
        return new ConnectedDB(uri, getUser(), getPwd(),
                Collections.emptyMap(),
                Database.NO_LIMIT,
                Database.NO_FETCH_SIZE, getConnectionProperties());
    }

    /**
     * Loads properties, first from System, then from the given file.
     *
     * @param fileResource path
     * @return {@link Properties}
     */
    public static Properties loadProperties(String fileResource) {
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
        createDatabase(Paths.get(ConnectionData.class
                .getResource(Objects.requireNonNull(scriptResource, "Null script")).toURI()), databaseName);
    }

    public void createDatabase(Path script, String databaseName) throws Exception {
        Objects.requireNonNull(databaseName, "Null database name");
        if (!Files.exists(script)) throw new IllegalArgumentException("Can't find script " + script);
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
     * Creates a fresh database {@link MapObjectImpl}.
     *
     * @param mapping {@link Mapping}, not {@code null}
     * @param uri     String, resource uri, not {@code null}
     * @param name    String, database name, not {@code null}
     * @return {@link Database}
     */
    public Database createDatabaseMapObject(Mapping mapping, String uri, String name) {
        return mapping.createDatabase(Objects.requireNonNull(uri, "Null uri"))
                .setJDBCDSN(getJdbcURI(Objects.requireNonNull(name, "Null name")))
                .setJDBCDriver(getDriver())
                .setUsername(getUser())
                .setPassword(getPwd())
                .addConnectionProperties(getConnectionProperties());
    }

    public Mapping loadMapping(String resource, String format, String baseURI) {
        Mapping res = MappingFactory.load(resource, format, baseURI);
        insert(res);
        return res;
    }

    public void insert(Mapping mapping) {
        insert(mapping, true);
    }

    /**
     * Inserts into the given mapping this JDBC data settings.
     *
     * @param mapping {@link Mapping}
     * @param force   if {@code true}, then replace previous {@code d2rq:Database} with the new one,
     *                otherwise only fixes connection credentials
     * @throws IllegalArgumentException if the mapping cannot be updated due to some incompatibility
     */
    public void insert(Mapping mapping, boolean force) {
        String base = getBase();
        Set<Database> dbs;
        if (force) {
            dbs = mapping.databases().collect(Collectors.toSet());
            if (dbs.size() != 1) {
                throw new IllegalArgumentException("Too many d2rq:Database. Can update only single one.");
            }
            Database db = dbs.iterator().next();
            String prev = db.getJDBCDSN();
            if (!prev.contains(base)) {
                String uri = prev.replaceFirst("^.+/([^/]+)$", base + "$1");
                LOGGER.debug("Replace <{}> with <{}>", prev, uri);
                db.setJDBCDSN(uri);
            }
        }
        dbs = mapping.databases()
                .filter(s -> s.getJDBCDSN().startsWith(base)).collect(Collectors.toSet());
        if (dbs.isEmpty()) {
            throw new IllegalArgumentException("Can't find any db for the uri " + base);
        }
        dbs.forEach(d -> d.setUsername(getUser()).setPassword(getPwd()));
    }

}
