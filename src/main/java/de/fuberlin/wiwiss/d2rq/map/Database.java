package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Property;

import java.util.Properties;
import java.util.stream.Stream;

/**
 * Representation of a {@code d2rq:Database} from the mapping graph.
 *
 * @author Chris Bizer chris@bizer.de
 * @author Richard Cyganiak (richard@cyganiak.de)
 * <p>
 * Created by @ssz on 26.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#database-properties'>3.1 Properties of d2rq:Database</a>
 */
@SuppressWarnings("UnusedReturnValue")
public interface Database extends MapObject {
    // todo: use Integer and null to indicate no field assigned
    int NO_LIMIT = -1;
    int NO_FETCH_SIZE = -1;

    // todo: move to the Mapping utils
    ConnectedDB connectedDB();

    /**
     * Original comment:
     * This is a hack where we can pass a pre-existing ConnectedDB that
     * will be used by this Database, so we avoid that the Database opens another connection to the same DB.
     * todo: move to the Mapping utils
     *
     * @param db {@link ConnectedDB}, not {@code null}
     */
    void useConnectedDB(ConnectedDB db);

    /**
     * Adds the JDBC database URL as literal.
     * This is a string of the form {@code jdbc:subprotocol:subname}.
     * For a MySQL database, this is something like {@code jdbc:mysql://hostname:port/dbname}.
     *
     * @param uri String, not {@code null}
     * @return this instance to allow cascading calls
     */
    Database setJDBCDSN(String uri);

    /**
     * Gets the JDBC database URL,
     * that is a string literal from the statement with predicate {@code d2rq:jdbcDSN}
     * and {@link #asResource()} as subject.
     *
     * @return String or {@code null}
     */
    String getJDBCDSN();

    /**
     * Adds the JDBC driver class name for the database.
     * Used together with {@code d2rq:jdbcDSN}.
     * Example: {@code com.mysql.jdbc.Driver} for MySQL.
     * Notice that since JDBC4 this parameter is not required.
     *
     * @param className String, not {@code null}
     * @return this instance to allow cascading calls
     */
    Database setJDBCDriver(String className);

    /**
     * Gets the JDBC driver class path as string,
     * that is a lexical form of the literal from the statement with predicate {@code d2rq:jdbcDriver}
     * and {@link #asResource()} as subject.
     *
     * @return String or {@code null}
     */
    String getJDBCDriver();

    /**
     * Sets a username if required by the database.
     * If input is {@code null},
     * all statements with the {@code d2rq:username} predicate and this object as subject will be removed.
     *
     * @param username String or {@code null} to remove existing record
     * @return this instance to allow cascading calls
     */
    Database setUsername(String username);

    /**
     * Gets a username,
     * that is a string literal from the statement with predicate {@code d2rq:username}.
     *
     * @return String or {@code null}
     */
    String getUsername();

    /**
     * Sets a user password if required by the database.
     * Null input means
     * that all statements with the {@code d2rq:password} predicate and this object as subject will be removed.
     *
     * @param password String or {@code null} to remove existing record
     * @return this instance to allow cascading calls
     */
    Database setPassword(String password);

    /**
     * Gets a password,
     * that is a string literal from the statement with predicate {@code d2rq:password}.
     *
     * @return String or {@code null}
     */
    String getPassword();

    /**
     * Sets URL of a SQL script to be executed on startup.
     * Useful for initializing the connection and testing.
     * To load from the file system relative to the mapping file's location,
     * use this syntax: {@code _:this d2rq:startupSQLScript <file:script.sql>};
     *
     * @param script String, not {@code null}
     * @return this instance to allow cascading calls
     */
    Database setStartupSQLScript(String script);

    /**
     * Returns startup script uri,
     * that is an uri object from the statement with predicate {@code d2rq:startupSQLScript}.
     *
     * @return String or {@code null}
     */
    String getStartupSQLScript();

    /**
     * Sets a result size limit.
     * That is an integer value that will be added as a LIMIT clause to all generated SQL queries.
     * This sets an upper bound for the number of results returned from large databases.
     * Note that this effectively “cripples” the server and can cause unpredictable results.
     * Also see {@link D2RQ#limit d2rq:limit} and {@link D2RQ#limitInverse d2rq:limitInverse},
     * which may be used to impose result limits on individual property bridges.
     *
     * @param limit nonnegative integer number
     * @return this instance to allow cascading calls
     */
    Database setResultSizeLimit(int limit);

    /**
     * Returns a result size limit,
     * that is a {@code xsd:integer} literal from the statement with the predicate {@code d2rq:resultSizeLimit}.
     *
     * @return positive int or {@link #NO_LIMIT}
     */
    int getResultSizeLimit();

    /**
     * Sets a fetching size.
     * That is an integer value that specifies the number of rows to retrieve with every database request.
     * This value is particularly important to control memory resources of both
     * the D2RQ and the database server when performing dumps.
     * The utility {@code dump-rdf} sets this value to {@code 500} by default.
     *
     * @param fetchSize nonnegative integer number
     * @return this instance to allow cascading calls
     */
    Database setFetchSize(int fetchSize);

    /**
     * Returns a fetch size,
     * that is a {@code xsd:integer} literal from the statement with predicate {@code d2rq:fetchSize}.
     *
     * @return positive int or {@link #NO_FETCH_SIZE}
     */
    int getFetchSize();

    /**
     * Adds a JDBC connection property pair as rdf triple with {@link #asResource() this resource} as subject.
     * The value goes as string literal and the key becomes a predicate with namespace {@link de.fuberlin.wiwiss.d2rq.vocab.JDBC#NS}.
     *
     * @param key   String, not {@code null}
     * @param value String, not {@code null}
     * @return this instance to allow cascading calls
     */
    Database putConnectionProperty(String key, String value);

    /**
     * Gets JDBC connection properties.
     *
     * @return {@link Properties}, possible empty with String keys and values
     */
    Properties getConnectionProperties();

    /**
     * Sets a column property as plain literal into the statement with the {@link Column#getPredicate()} predicate.
     *
     * @param value String, not {@code null}
     * @return this instance to allow cascading calls
     * @see Column description
     */
    Database addColumn(Column type, String value);

    /**
     * Lists all column literals parsed from the statements with the {@link Column#getPredicate()} predicate.
     *
     * @return Stream of Strings
     * @see Column description
     */
    Stream<String> columns(Column type);

    /**
     * Adds the specified properties to the existing one.
     *
     * @param properties {@link Properties}, not {@code null}
     * @return this instance to allow cascading calls
     * @see #getConnectionProperties()
     * @see #putConnectionProperty(String, String)
     */
    default Database addConnectionProperties(Properties properties) {
        properties.forEach((k, v) -> putConnectionProperty(String.valueOf(k), String.valueOf(v)));
        return this;
    }

    /**
     * These properties are used to declare the column type of database columns.
     * This affects the kind of SQL literal that D2RQ will use to query for values in this column.
     * The objects of the properties are column names in {@code Table_name.column_name} notation.
     * These properties do not need to be specified
     * unless the engine is for some reason unable to determine the correct column type by itself.
     * The {@code d2rq:timestampColumn} is for column types that combine a date and a time.
     * The {@code d2rq:binaryColumn} is for column types that contain binary data, such as {@code BLOB}s.
     */
    enum Column {
        TEXT(D2RQ.textColumn),
        BINARY(D2RQ.binaryColumn),
        NUMERIC(D2RQ.numericColumn),
        BOOLEAN(D2RQ.booleanColumn),
        DATE(D2RQ.dateColumn),
        TIME(D2RQ.timeColumn),
        TIMESTAMP(D2RQ.timestampColumn),
        INTERVAL(D2RQ.intervalColumn),
        BIT(D2RQ.bitColumn);
        private final Property predicate;

        Column(Property predicate) {
            this.predicate = predicate;
        }

        public Property getPredicate() {
            return predicate;
        }
    }
}
