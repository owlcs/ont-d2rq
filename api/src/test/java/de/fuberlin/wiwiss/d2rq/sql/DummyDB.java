package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType.GenericType;
import de.fuberlin.wiwiss.d2rq.sql.vendor.Vendor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DummyDB extends ConnectedDB {
    private final Vendor vendor;
    private int limit = Database.NO_LIMIT;
    private Map<Attribute, Boolean> nullability = new HashMap<>();

    private DummyDB(String jdbcURL, Vendor vendor, Map<String, GenericType> types) {
        super(jdbcURL, null, null, types, Database.NO_LIMIT, Database.NO_FETCH_SIZE, null);
        this.vendor = vendor;
    }

    public static DummyDB create(Map<String, GenericType> types) {
        return create(generateJdbcURL(), Vendor.SQL92, types);
    }

    public static DummyDB create(Vendor vendor) {
        return create(generateJdbcURL(), vendor, Collections.emptyMap());
    }

    public static DummyDB create() {
        return create(generateJdbcURL());
    }

    public static DummyDB create(Database db) {
        return create(db.getJDBCDSN());
    }

    public static DummyDB create(String jdbcURL) {
        return create(jdbcURL, Vendor.SQL92, Collections.emptyMap());
    }

    private static DummyDB create(String url, Vendor vendor, Map<String, GenericType> types) {
        return new DummyDB(Objects.requireNonNull(url), Objects.requireNonNull(vendor), Objects.requireNonNull(types));
    }

    private static String generateJdbcURL() {
        return "jdbc:" + DummyDB.class.getSimpleName() + "-" + System.currentTimeMillis();
    }

    @Override
    protected void connect() {
        // nothing
    }

    @Override
    public DatabaseSchemaInspector schemaInspector() {
        return null;
    }

    public void setLimit(int newLimit) {
        limit = newLimit;
    }

    public void setNullable(Attribute column, boolean flag) {
        nullability.put(column, flag);
    }

    @Override
    public Vendor vendor() {
        return vendor;
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public boolean isNullable(Attribute column) {
        if (!nullability.containsKey(column)) return true;
        return nullability.get(column);
    }
}
