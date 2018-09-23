package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType.GenericType;
import de.fuberlin.wiwiss.d2rq.sql.vendor.Vendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * TODO Move all engine-specific code from here to {@link Vendor} and its implementations
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @author kurtjx (http://github.com/kurtjx)
 */
@SuppressWarnings("WeakerAccess")
public class ConnectedDB implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectedDB.class);

    public static final String KEEP_ALIVE_PROPERTY = "keepAlive"; // interval property, value in seconds
    public static final int DEFAULT_KEEP_ALIVE_INTERVAL = 60 * 60; // hourly
    public static final String KEEP_ALIVE_QUERY_PROPERTY = "keepAliveQuery"; // override default keep alive query
    public static final String DEFAULT_KEEP_ALIVE_QUERY = "SELECT 1"; // may not work for some DBMS

    private String jdbcURL;
    private String username;
    private String password;
    private final Map<Attribute, Boolean> cachedColumnNullability = new HashMap<>();
    private final Map<Attribute, DataType> cachedColumnTypes = new HashMap<>();
    private final Map<Attribute, GenericType> overriddenColumnTypes = new HashMap<>();
    private Connection connection;
    private DatabaseSchemaInspector schemaInspector;

    // Lazy initialization -- use vendor() for access!
    private Vendor vendor;

    private int limit;
    private int fetchSize;
    private int defaultFetchSize = Database.NO_FETCH_SIZE;
    private Map<Attribute, Boolean> zerofillCache = new HashMap<>();
    private Map<RelationName, Map<String, List<String>>> uniqueIndexCache = new HashMap<>();
    private final Properties connectionProperties;

    private class KeepAliveAgent extends Thread {
        private final int interval;
        private final String query;
        volatile boolean shutdown = false;

        /**
         * @param interval in seconds
         * @param query    the noop query to execute
         */
        public KeepAliveAgent(int interval, String query) {
            super("keepalive");
            this.interval = interval;
            this.query = query;
        }

        @Override
        public void run() {
            Connection c;
            Statement s = null;
            Vendor v;
            while (!shutdown) {
                try {
                    Thread.sleep(interval * 1000);
                } catch (InterruptedException e) {
                    if (shutdown) break;
                }

                try {
                    if (LOGGER.isDebugEnabled())
                        LOGGER.debug("Keep alive agent is executing noop query '{}'...", query);
                    c = connection();
                    v = vendor();
                    s = c.createStatement();

                    v.beforeQuery(c);
                    s.execute(query);
                    v.afterQuery(c);

                    v.beforeClose(c);
                    s.close();
                    v.afterClose(c);

                } catch (Throwable e) { // may throw D2RQException at runtime
                    LOGGER.error("Keep alive connection test failed: {} ", e.getMessage());

                    LOGGER.info("Connection will be reset since a failure is detected by keep alive agent.");
                    if (s != null) {
                        try {
                            s.close();
                        } catch (Exception ignore) {
                        }
                    }
                    resetConnection();
                } finally {
                    if (s != null) try {
                        s.close();
                    } catch (Exception ignore) {
                    }
                }
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Keep alive agent terminated.");
        }

        public void shutdown() {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("shutting down");
            shutdown = true;
            this.interrupt();
        }
    }

    private void resetConnection() {
        if (this.connection != null) {
            try {
                this.connection.close();
            } catch (SQLException sqlExc) {
                // ignore...
                LOGGER.error("Error while closing current connection: {}", sqlExc.getMessage(), sqlExc);
            } finally {
                this.connection = null;
            }
        }
    }

    private final KeepAliveAgent keepAliveAgent;

    public ConnectedDB(String jdbcURL, String username, String password) {
        this(jdbcURL, username, password, Collections.emptyMap(), Database.NO_LIMIT, Database.NO_FETCH_SIZE, null);
    }

    public ConnectedDB(String jdbcURL,
                       String username,
                       String password,
                       Map<String, GenericType> columnTypes,
                       int limit,
                       int fetchSize,
                       Properties connectionProperties) {
        // TODO replace column type arguments with a single column => type map
        this.jdbcURL = jdbcURL;
        this.username = username;
        this.password = password;
        this.limit = limit;
        this.fetchSize = fetchSize;
        this.connectionProperties = connectionProperties;

        for (String columnName : columnTypes.keySet()) {
            overriddenColumnTypes.put(SQL.parseAttribute(columnName), columnTypes.get(columnName));
        }

        // create keep alive agent if enabled
        if (connectionProperties != null && connectionProperties.containsKey(KEEP_ALIVE_PROPERTY)) {
            int interval = DEFAULT_KEEP_ALIVE_INTERVAL;
            String query = DEFAULT_KEEP_ALIVE_QUERY;
            try {
                interval = new Integer((String) connectionProperties.get(KEEP_ALIVE_PROPERTY));
                if (interval <= 0) interval = DEFAULT_KEEP_ALIVE_INTERVAL;
            } catch (NumberFormatException ignore) {
            } // use default
            if (connectionProperties.containsKey(KEEP_ALIVE_QUERY_PROPERTY))
                query = connectionProperties.getProperty(KEEP_ALIVE_QUERY_PROPERTY);

            this.keepAliveAgent = new KeepAliveAgent(interval, query);
            this.keepAliveAgent.start();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Keep alive agent is enabled (interval: {} seconds, noop query: '{}').", interval, query);
        } else {
            this.keepAliveAgent = null;
        }
    }

    public String getJdbcURL() {
        return jdbcURL;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Connection connection() {
        if (this.connection == null) {
            connect();
        }
        return this.connection;
    }

    public int limit() {
        return this.limit;
    }

    public void setDefaultFetchSize(int value) {
        defaultFetchSize = value;
    }

    public int fetchSize() {
        if (fetchSize == Database.NO_FETCH_SIZE) {
            // do not allow fetchSize=Integer.MIN_VALUE for mysql,
            // in this case rows will be returned one by one and we expect exception during iteration appeared:
            // Streaming result set com.mysql.jdbc.RowDataDynamic@... is still active.
            // No statements may be issued when any streaming result sets are open and in use on a given connection.
            // Ensure that you have called .close() on any active streaming result sets before attempting more queries.

            //if (vendorIs(Vendor.MySQL)) {
            //	return Integer.MIN_VALUE;
            //}
            return defaultFetchSize;
        }
        return fetchSize;
    }

    public boolean useServerFetch() {
        return fetchSize != Database.NO_FETCH_SIZE;
    }

    private void connect() {
        if (jdbcURL != null && !jdbcURL.toLowerCase().startsWith("jdbc:")) {
            throw new D2RQException("Not a JDBC URL: " + jdbcURL, D2RQException.D2RQ_DB_CONNECTION_FAILED);
        }
        try {
            LOGGER.info("Establishing JDBC connection to {}", jdbcURL);
            this.connection = DriverManager.getConnection(this.jdbcURL, getConnectionProperties());
        } catch (SQLException ex) {
            close();
            throw new D2RQException("Database connection to " + jdbcURL + " failed (user: " + username + "): " + ex.getMessage(),
                    ex,
                    D2RQException.D2RQ_DB_CONNECTION_FAILED);
        }
        // Database-dependent initialization
        try {
            vendor().initializeConnection(connection);
        } catch (SQLException ex) {
            close();
            throw new D2RQException(
                    "Database initialization failed: " + ex.getMessage(),
                    D2RQException.D2RQ_DB_CONNECTION_FAILED);
        }
    }

    private Properties getConnectionProperties() {
        Properties result = (connectionProperties == null) ? new Properties() : (Properties) connectionProperties.clone();
        if (username != null) {
            result.setProperty("user", username);
        }
        if (password != null) {
            result.setProperty("password", password);
        }

//		/*
//		 * Enable cursor support in MySQL
//		 * Note that the implementation is buggy in early MySQL 5.0 releases such
//		 * as 5.0.27, which may lead to incorrect results.
//		 * This is placed here as a later change requires a call to setClientInfo, which is only available from Java 6 on
//		 */
//		if (this.jdbcURL.contains(":mysql:")) {
//			result.setProperty("useCursorFetch", "true");
//			result.setProperty("useServerPrepStmts", "true"); /* prerequisite */
//		}

        return result;
    }

    public DatabaseSchemaInspector schemaInspector() {
        if (schemaInspector == null && jdbcURL != null) {
            schemaInspector = new DatabaseSchemaInspector(this);
        }
        return this.schemaInspector;
    }

    /**
     * Returns a column's datatype. Caches the types for performance.
     *
     * @param column Attribute
     * @return The column's datatype, or <code>null</code> if unknown
     */
    public DataType columnType(Attribute column) {
        if (!cachedColumnTypes.containsKey(column)) {
            if (overriddenColumnTypes.containsKey(column)) {
                cachedColumnTypes.put(column, overriddenColumnTypes.get(column).dataTypeFor(vendor()));
            } else if (schemaInspector() == null) {
                cachedColumnTypes.put(column, GenericType.CHARACTER.dataTypeFor(vendor()));
            } else {
                cachedColumnTypes.put(column, schemaInspector().columnType(column));
            }
        }
        return cachedColumnTypes.get(column);
    }

    public boolean isNullable(Attribute column) {
        if (!cachedColumnNullability.containsKey(column)) {
            cachedColumnNullability.put(column, schemaInspector() == null || schemaInspector().isNullable(column));
        }
        return cachedColumnNullability.get(column);
    }

    /**
     * @return A helper for generating SQL statements conforming to the syntax
     * of the database engine used in this connection
     */
    public Vendor vendor() {
        ensureVendorInitialized();
        return vendor;
    }

    protected String getDatabaseProductType() throws SQLException {
        return connection().getMetaData().getDatabaseProductName();
    }

    private void ensureVendorInitialized() {
        if (vendor != null) return;
        try {
            String productName = getDatabaseProductType();
            LOGGER.info("JDBC database product type: {}", productName);
            productName = productName.toLowerCase();
            if (productName.contains("mysql")) {
                vendor = Vendor.MySQL;
            } else if (productName.contains("postgresql")) {
                vendor = Vendor.PostgreSQL;
            } else if (productName.contains("interbase")) {
                vendor = Vendor.InterbaseOrFirebird;
            } else if (productName.contains("oracle")) {
                this.vendor = Vendor.Oracle;
            } else if (productName.contains("microsoft sql server")) {
                this.vendor = Vendor.SQLServer;
            } else if (productName.contains("access")) {
                this.vendor = Vendor.MSAccess;
            } else if (productName.contains("hsql")) {
                this.vendor = Vendor.HSQLDB;
            } else {
                this.vendor = Vendor.SQL92;
            }
            LOGGER.info("Using vendor class: {}", vendor.getClass().getName());
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex);
        }
    }

    /**
     * <p>Checks if two columns are formatted by the database in a compatible fashion.</p>
     * <p>Assuming <tt>v1</tt> is a value from column1, and <tt>v2</tt> a value
     * from column2, and <tt>v1 = v2</tt> evaluates to <tt>true</tt> within the
     * database, then we call the values have <em>compatible formatting</em> if
     * <tt>SELECT</tt>ing them results in character-for-character identical
     * strings. As an example, a <tt>TINYINT</tt> and a <tt>BIGINT</tt> are
     * compatible because equal values will be formatted in the same way
     * when <tt>SELECT</tt>ed, e.g. <tt>1 = 1</tt>. But if one of them is
     * <tt>ZEROFILL</tt>, then <tt>SELECT</tt>ing will result in a different
     * character string, e.g. <tt>1 = 0000000001</tt>. The two columns wouldn't
     * be compatible.</p>
     * <p>This is used by the engine when removing unnecessary joins. If
     * two columns have compatible formatting, then we can sometimes use
     * one in place of the other when they are known to have equal values.
     * But not if they are incompatible, because e.g. <tt>http://example.org/id/1</tt>
     * is different from <tt>http://example.org/id/0000000001</tt>.</p>
     *
     * @param column1 {@link Attribute}
     * @param column2 {@link Attribute}
     * @return <tt>true</tt> if both arguments have compatible formatting
     */
    public boolean areCompatibleFormats(Attribute column1, Attribute column2) {
        // TODO Right now we only catch the ZEROFILL case. There are many more!
        return !isZerofillColumn(column1) && !isZerofillColumn(column2);
    }

    private boolean isZerofillColumn(Attribute column) {
        if (!Vendor.MySQL.equals(vendor())) return false;
        if (!zerofillCache.containsKey(column)) {
            zerofillCache.put(column, schemaInspector().isZerofillColumn(column));
        }
        return zerofillCache.get(column);
    }

    public Map<String, List<String>> getUniqueKeyColumns(RelationName tableName) {
        if (!uniqueIndexCache.containsKey(tableName) && schemaInspector() != null)
            uniqueIndexCache.put(tableName, schemaInspector().uniqueColumns(tableName));
        return uniqueIndexCache.get(tableName);
    }

    /**
     * In some situations, MySQL stores table names using lowercase only, and then performs case-insensitive comparison.
     * We need to account for this when comparing table names reported by MySQL and those from the mapping.
     *
     * @return boolean
     * @see <a href="http://dev.mysql.com/doc/refman/5.0/en/identifier-case-sensitivity.html">MySQL Manual, Identifier Case Sensitivity</a>
     */
    public boolean lowerCaseTableNames() {
        if (!Vendor.MySQL.equals(vendor())) {
            return false;
        }
        try { // tested on mysql:mysql-connector-java:5.1.46
            return (Boolean) Class.forName("com.mysql.jdbc.MySQLConnection").getMethod("lowerCaseTableNames").invoke(connection());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            throw new IllegalStateException("Can't invoke com.mysql.jdbc.MySQLConnection#lowerCaseTableNames", e);
        }
    }

    /**
     * Closes the database connection and shuts down the keep alive agent.
     */
    @Override
    public void close() {
        if (keepAliveAgent != null)
            keepAliveAgent.shutdown();
        if (connection == null) return;
        try {
            LOGGER.info("Closing connection to {}", jdbcURL);
            this.connection.close();
        } catch (SQLException e) {
            // ignore...
            LOGGER.error("Error while closing current connection: '{}'", e.getMessage(), e);
        } finally {
            connection = null;
        }
    }

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) return true;
        if (!(otherObject instanceof ConnectedDB)) {
            return false;
        }
        ConnectedDB other = (ConnectedDB) otherObject;
        return this.jdbcURL.equals(other.jdbcURL);
    }

    @Override
    public int hashCode() {
        return this.jdbcURL.hashCode();
    }


    /**
     * Tries to guess the class name of a suitable JDBC driver from a JDBC URL.
     * This only works in the unlikely case that the driver has been registered earlier using Class.forName(classname).
     *
     * @param jdbcURL A JDBC URL
     * @return The corresponding JDBC driver class name, or <tt>null</tt> if not known
     */
    public static String guessJDBCDriverClass(String jdbcURL) {
        try {
            return DriverManager.getDriver(jdbcURL).getClass().getName();
        } catch (SQLException ex) {
            return null;
        }
    }

    /**
     * Registers a JDBC driver class.
     *
     * @param driverClassName Fully qualified class name of a JDBC driver
     * @throws D2RQException If the class could not be found
     */
    @Deprecated // since jdbc-4.0
    public static void registerJDBCDriver(String driverClassName) {
        if (driverClassName == null) return;
        try {
            Class.forName(driverClassName);
        } catch (ClassNotFoundException ex) {
            throw new D2RQException("Database driver class not found: " + driverClassName, D2RQException.DATABASE_JDBCDRIVER_CLASS_NOT_FOUND);
        }
    }
}