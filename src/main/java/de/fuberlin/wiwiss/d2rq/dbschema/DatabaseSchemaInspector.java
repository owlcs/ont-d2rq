package de.fuberlin.wiwiss.d2rq.dbschema;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Join;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType;
import de.fuberlin.wiwiss.d2rq.sql.vendor.Vendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * Inspects a database to retrieve schema information.
 * <p>
 * TODO: All the dbType checks should be moved to the {@link Vendor} subclasses
 * TODO: This usually shouldn't be used directly, but through the ConnectedDB.
 * Except in the MappingGenerator. ConnectedDB is easier mockable for unit tests!
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class DatabaseSchemaInspector {
    private final static Logger LOGGER = LoggerFactory.getLogger(DatabaseSchemaInspector.class);

    private final ConnectedDB db;
    private final DatabaseMetaData schema;

    public static final int KEYS_IMPORTED = 0;
    public static final int KEYS_EXPORTED = 1;

    public DatabaseSchemaInspector(ConnectedDB db) {
        this.db = db;
        try {
            this.schema = db.connection().getMetaData();
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
    }

    /**
     * @param column {@link Attribute}
     * @return The column's datatype, or <code>null</code> if unknown
     */
    public DataType columnType(Attribute column) {
        try (ResultSet rs = this.schema.getColumns(null, column.schemaName(), column.tableName(), column.attributeName())) {
            if (!rs.next()) {
                throw new D2RQException("Column " + column + " not found in database", D2RQException.SQL_COLUMN_NOT_FOUND);
            }
            int type = rs.getInt("DATA_TYPE");
            String name = rs.getString("TYPE_NAME").toUpperCase();
            int size = rs.getInt("COLUMN_SIZE");
            DataType result = db.vendor().getDataType(type, name, size);
            if (result == null) {
                LOGGER.warn("Unknown datatype '" + (size == 0 ? name : (name + "(" + size + ")")) + "' (" + type + ")");
            }
            return result;
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
    }

    public boolean isNullable(Attribute column) {
        try (ResultSet rs = this.schema.getColumns(null, column.schemaName(), column.tableName(), column.attributeName())) {
            if (!rs.next()) {
                throw new D2RQException("Column " + column + " not found in database", D2RQException.SQL_COLUMN_NOT_FOUND);
            }
            return rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
    }

    public boolean isZerofillColumn(Attribute column) {
        if (db.vendor() != Vendor.MySQL) return false;
        try (Statement stmt = db.connection().createStatement()) {
            db.vendor().beforeQuery(db.connection());
            try (ResultSet rs = stmt.executeQuery(String.format("DESCRIBE %s", db.vendor().quoteRelationName(column.relationName())))) {
                db.vendor().afterQuery(db.connection());
                while (rs.next()) {
                    // MySQL names are case insensitive, so we normalize to lower case
                    if (!column.attributeName().equalsIgnoreCase(rs.getString("Field"))) {
                        continue;
                    }
                    return rs.getString("Type").toLowerCase().contains("zerofill");
                }
            }
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
        throw new D2RQException("Column not found in DESCRIBE result: " + column, D2RQException.SQL_COLUMN_NOT_FOUND);
    }

    /**
     * Lists available table names
     *
     * @param searchInSchema Schema to list tables from; <tt>null</tt> to list tables from all schemas
     * @return A list of {@link RelationName}s
     */
    public List<RelationName> listTableNames(String searchInSchema) {
        try (ResultSet rs = this.schema.getTables(null, searchInSchema, null, new String[]{"TABLE", "VIEW"})) {
            List<RelationName> result = new ArrayList<>();
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String table = rs.getString("TABLE_NAME");
                if (!this.db.vendor().isIgnoredTable(schema, table)) {
                    result.add(toRelationName(schema, table));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex);
        }
    }

    public List<Attribute> listColumns(RelationName tableName) {
        try (ResultSet rs = this.schema.getColumns(null, schemaName(tableName), tableName(tableName), null)) {
            List<Attribute> result = new ArrayList<>();
            while (rs.next()) {
                result.add(new Attribute(tableName, rs.getString("COLUMN_NAME")));
            }
            return result;
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
    }

    public List<Attribute> primaryKeyColumns(RelationName tableName) {
        try (ResultSet rs = this.schema.getPrimaryKeys(null, schemaName(tableName), tableName(tableName))) {
            List<Attribute> res = new ArrayList<>();
            while (rs.next()) {
                res.add(new Attribute(tableName, rs.getString("COLUMN_NAME")));
            }
            return res;
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
    }

    /**
     * Returns unique indexes defined on the table.
     *
     * @param tableName Name of a table
     * @return Map from index name to list of column names
     */
    public Map<String, List<String>> uniqueColumns(RelationName tableName) {
        /*
         * When requesting index info from an Oracle database, accept approximate
         * data, as requesting exact results will invoke an ANALYZE, for which the
         * querying user must have proper write permissions.
         * If he doesn't, an SQLException is thrown right here.
         * Note that the "approximate" parameter was not handled by the Oracle JDBC
         * driver before release 10.2.0.4, which may result in an exception here.
         * @see http://forums.oracle.com/forums/thread.jspa?threadID=210782
         * @see http://www.oracle.com/technology/software/tech/java/sqlj_jdbc/htdocs/readme_jdbc_10204.html
         */
        boolean approximate = db.vendor() == Vendor.Oracle;
        try (ResultSet rs = this.schema.getIndexInfo(null, schemaName(tableName), tableName(tableName), true, approximate)) {
            Map<String, List<String>> result = new HashMap<>();
            while (rs.next()) {
                String indexKey = rs.getString("INDEX_NAME");
                if (indexKey == null) { // is null when type = tableIndexStatistic, ignore
                    continue;
                }
                result.computeIfAbsent(indexKey, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
            }
            return result;
        } catch (SQLException ex) {
            throw new D2RQException("Database exception (unable to determine unique columns)", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
    }

    /**
     * Returns a list of imported or exported (foreign) keys for a table.
     *
     * @param tableName The table we are interested in
     * @param direction If set to {@link #KEYS_IMPORTED}, the table's foreign keys are returned.
     *                  If set to {@link #KEYS_EXPORTED}, the table's primary keys referenced from other tables are returned.
     * @return A list of {@link Join}s; the local columns are in attributes1()
     */
    public List<Join> foreignKeys(RelationName tableName, int direction) {
        Map<String, ForeignKey> fks = new HashMap<>();
        try (ResultSet rs = direction == KEYS_IMPORTED
                ? this.schema.getImportedKeys(null, schemaName(tableName), tableName(tableName))
                : this.schema.getExportedKeys(null, schemaName(tableName), tableName(tableName))) {
            while (rs.next()) {
                RelationName pkTable = toRelationName(rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME"));
                Attribute primaryColumn = new Attribute(pkTable, rs.getString("PKCOLUMN_NAME"));
                RelationName fkTable = toRelationName(rs.getString("FKTABLE_SCHEM"), rs.getString("FKTABLE_NAME"));
                Attribute foreignColumn = new Attribute(fkTable, rs.getString("FKCOLUMN_NAME"));
                String fkName = rs.getString("FK_NAME");
                if (!fks.containsKey(fkName)) {
                    fks.put(fkName, new ForeignKey());
                }
                int keySeq = rs.getInt("KEY_SEQ") - 1;
                fks.get(fkName).addColumns(keySeq, foreignColumn, primaryColumn);
            }
        } catch (SQLException ex) {
            throw new D2RQException("Database exception", ex, D2RQException.D2RQ_SQLEXCEPTION);
        }
        List<Join> results = new ArrayList<>();
        for (ForeignKey fk : fks.values()) {
            results.add(fk.toJoin());
        }
        return results;

    }

    private String schemaName(RelationName tableName) {
        if (this.db.vendor() == Vendor.PostgreSQL && tableName.schemaName() == null) {
            // The default schema is known as "public" in PostgreSQL
            return "public";
        }
        return tableName.schemaName();
    }

    private String tableName(RelationName tableName) {
        return tableName.tableName();
    }

    private RelationName toRelationName(String schema, String table) {
        if (schema == null) {
            // Table without schema
            return new RelationName(null, table, db.lowerCaseTableNames());
        } else if ((db.vendor() == Vendor.PostgreSQL || db.vendor() == Vendor.HSQLDB)
                && "public".equals(schema.toLowerCase())) {
            // Call the tables in PostgreSQL or HSQLDB default schema "FOO", not "PUBLIC.FOO"
            return new RelationName(null, table, db.lowerCaseTableNames());
        }
        return new RelationName(schema, table, db.lowerCaseTableNames());
    }

    /**
     * A foreign key. Supports adding (local column, other column) pairs. The pairs
     * can be added out of order and will be re-ordered internally. When all
     * columns are added, a {@link Join} object can be created.
     */
    private class ForeignKey {
        private TreeMap<Integer, Attribute> primaryColumns = new TreeMap<>();
        private TreeMap<Integer, Attribute> foreignColumns = new TreeMap<>();

        private void addColumns(int keySequence, Attribute foreign, Attribute primary) {
            primaryColumns.put(keySequence, primary);
            foreignColumns.put(keySequence, foreign);
        }

        private Join toJoin() {
            return new Join(new ArrayList<>(foreignColumns.values()), new ArrayList<>(primaryColumns.values()), Join.DIRECTION_RIGHT);
        }
    }

    /**
     * Looks up a RelationName with the schema in order to retrieve the correct capitalization
     *
     * @param relationName {@link RelationName}
     * @return The correctly captialized RelationName
     */
    public RelationName getCorrectCapitalization(RelationName relationName) {
        if (!relationName.caseUnspecified() || !db.lowerCaseTableNames())
            return relationName;

        for (RelationName r : listTableNames(null)) {
            if (r.equals(relationName))
                return r;
        }
        return null;
    }
}