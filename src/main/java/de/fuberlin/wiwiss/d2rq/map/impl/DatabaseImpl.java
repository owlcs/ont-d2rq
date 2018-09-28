package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType.GenericType;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.JDBC;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;


/**
 * Representation of a d2rq:Database from the mapping file.
 *
 * @author Chris Bizer chris@bizer.de
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class DatabaseImpl extends MapObjectImpl implements Database {

    public DatabaseImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected DatabaseImpl setRDFNode(Property property, RDFNode value) {
        checkNotConnected();
        return super.setRDFNode(property, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected DatabaseImpl addRDFNode(Property property, RDFNode value) {
        checkNotConnected();
        return super.addRDFNode(property, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected DatabaseImpl setNullable(Property property, String literal) {
        checkNotConnected();
        return super.setNullable(property, literal);
    }


    /**
     * {@inheritDoc}
     *
     * @param uri String, not {@code null}
     * @return this instance
     */
    @Override
    public Database setJDBCDSN(String uri) {
        return setLiteral(D2RQ.jdbcDSN, uri);
    }

    /**
     * Gets the JDBC database URL.
     *
     * @return String or {@code null}
     */
    @Override
    public String getJDBCDSN() {
        return findString(D2RQ.jdbcDSN).orElse(null);
    }

    /**
     * {@inheritDoc}
     *
     * @param className String, not {@code null}
     * @return this instance
     */
    @Override
    public Database setJDBCDriver(String className) {
        return setLiteral(D2RQ.jdbcDriver, className);
    }

    @Override
    public String getJDBCDriver() {
        return findString(D2RQ.jdbcDriver).orElse(null);
    }

    @Override
    public String getUsername() {
        return findString(D2RQ.username).orElse(null);
    }

    @Override
    public Database setUsername(String username) {
        return setNullable(D2RQ.username, username);
    }

    @Override
    public String getPassword() {
        return findString(D2RQ.password).orElse(null);
    }

    @Override
    public Database setPassword(String password) {
        return setNullable(D2RQ.password, password);
    }

    @Override
    public Stream<String> columns(Column type) {
        return Iter.asStream(listStrings(type.getPredicate()));
    }

    @Override
    public Database addColumn(Column type, String value) {
        return addLiteral(type.getPredicate(), value);
    }

    public Map<String, GenericType> getColumns() {
        Map<String, GenericType> res = new HashMap<>();
        for (Column type : Column.values()) {
            findString(type.getPredicate()).ifPresent(s -> res.put(s, getSQLType(type)));
        }
        return res;
    }

    public static GenericType getSQLType(Column column) {
        switch (column) {
            case TEXT:
                return GenericType.CHARACTER;
            case BIT:
                return GenericType.BIT;
            case BOOLEAN:
                return GenericType.BOOLEAN;
            case DATE:
                return GenericType.DATE;
            case TIME:
                return GenericType.TIME;
            case BINARY:
                return GenericType.BINARY;
            case INTERVAL:
                return GenericType.INTERVAL;
            case TIMESTAMP:
                return GenericType.TIMESTAMP;
            case NUMERIC:
                return GenericType.NUMERIC;
            default:
                throw new IllegalArgumentException("Can't map type " + column);
        }
    }

    @Override
    public int getResultSizeLimit() {
        return findFirst(D2RQ.resultSizeLimit, s -> s.getLiteral().getInt()).orElse(NO_LIMIT);
    }

    @Override
    public Database setResultSizeLimit(int limit) {
        return setLiteral(D2RQ.resultSizeLimit, limit);
    }

    @Override
    public int getFetchSize() {
        return findFirst(D2RQ.fetchSize, s -> s.getLiteral().getInt()).filter(s -> s >= 0).orElse(NO_FETCH_SIZE);
    }

    @Override
    public Database setFetchSize(int fetchSize) {
        return setLiteral(D2RQ.fetchSize, fetchSize);
    }

    @Override
    public String getStartupSQLScript() {
        return findURI(D2RQ.startupSQLScript).orElse(null);
    }

    @Override
    public Database setStartupSQLScript(String uri) {
        return setURI(D2RQ.startupSQLScript, uri);
    }

    @Override
    public Database setConnectionProperty(String key, String value) {
        return setLiteral(JDBC.property(key), value);
    }

    @Override
    public Properties getConnectionProperties() {
        Properties res = new Properties();
        resource.listProperties()
                .filterKeep(s -> s.getPredicate().getURI().startsWith(JDBC.NS))
                .forEachRemaining(s -> {
                    String key = s.getPredicate().getURI().replace(JDBC.NS, "");
                    String value = s.getLiteral().getString();
                    res.put(key, value);
                });
        return res;
    }

    public ConnectedDB toConnectionDB() {
        return new ConnectedDB(getJDBCDSN(), getUsername(), getPassword(),
                getColumns(), getResultSizeLimit(), getFetchSize(), getConnectionProperties());
    }

    @Override
    public ConnectedDB connectedDB() {
        return mapping.getConnectedDB(this);
    }

    @Override
    public void useConnectedDB(ConnectedDB cdb) {
        mapping.registerConnectedDB(this, Objects.requireNonNull(cdb));
    }

    @Override
    public String toString() {
        return "d2rq:Database " + super.toString();
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        // jdbc uri:
        v.forProperty(D2RQ.jdbcDSN)
                .requireExists(D2RQException.DATABASE_MISSING_DSN)
                .requireHasNoDuplicates(D2RQException.DATABASE_DUPLICATE_JDBCDSN)
                .requireIsStringLiteral(D2RQException.UNSPECIFIED);

        // driver:
        Validator.ForProperty d = v.forProperty(D2RQ.jdbcDriver);
        if (d.exists()) {
            d.requireHasNoDuplicates(D2RQException.DATABASE_DUPLICATE_JDBCDRIVER)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
            String driver = resource.getRequiredProperty(D2RQ.jdbcDriver).getString();
            try {
                Class.forName(driver);
            } catch (ClassNotFoundException e) {
                throw new D2RQException("Can't find driver class " + driver, e,
                        D2RQException.DATABASE_JDBCDRIVER_CLASS_NOT_FOUND);
            }
        }

        // loading script:
        Validator.ForProperty s = v.forProperty(D2RQ.startupSQLScript);
        if (s.exists()) {
            s.requireHasNoDuplicates(D2RQException.DATABASE_DUPLICATE_STARTUPSCRIPT)
                    .requireIsURI(D2RQException.UNSPECIFIED);
            String script = resource.getRequiredProperty(D2RQ.startupSQLScript).getResource().getURI();
            try {
                new URL(script);
            } catch (MalformedURLException m) {
                throw new D2RQException("Wrong script: " + script, m);
            }
        }
        // username and password
        Validator.ForProperty u = v.forProperty(D2RQ.username);
        Validator.ForProperty p = v.forProperty(D2RQ.password);
        if (u.exists()) {
            u.requireHasNoDuplicates(D2RQException.DATABASE_DUPLICATE_USERNAME)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
            if (p.exists()) {
                p.requireHasNoDuplicates(D2RQException.DATABASE_DUPLICATE_PASSWORD)
                        .requireIsStringLiteral(D2RQException.UNSPECIFIED);
            }
        } else if (p.exists()) {
            throw new D2RQException("Password without username");
        }
    }

    protected void checkNotConnected() { // todo: this logic will be moved to graph listener
        if (!mapping.hasConnection(this)) {
            return;
        }
        throw new D2RQException("Cannot modify Database as it is already connected",
                D2RQException.DATABASE_ALREADY_CONNECTED);
    }

    public static DatabaseImpl copy(MappingImpl mapping, Database other) {
        if (Objects.requireNonNull(other, "Null database").getMapping() == mapping) {
            return (DatabaseImpl) other;
        }
        DatabaseImpl res = mapping.createDatabase(other.asResource().getURI());
        res.setJDBCDSN(other.getJDBCDSN());
        set(res, DatabaseImpl::setJDBCDriver, other.getJDBCDriver());
        set(res, DatabaseImpl::setUsername, other.getUsername());
        set(res, DatabaseImpl::setPassword, other.getPassword());
        set(res, DatabaseImpl::setStartupSQLScript, other.getStartupSQLScript());
        set(res, DatabaseImpl::setResultSizeLimit, other.getResultSizeLimit());
        set(res, DatabaseImpl::setFetchSize, other.getFetchSize());
        other.getConnectionProperties().forEach((k, v) -> res.setConnectionProperty(String.valueOf(k), String.valueOf(v)));
        Arrays.stream(Column.values()).forEach(type -> other.columns(type).forEach(s -> res.addColumn(type, s)));
        return res;
    }

    private static <T> void set(DatabaseImpl d, BiConsumer<DatabaseImpl, T> setter, T value) {
        if (value == null) return;
        setter.accept(d, value);
    }
}