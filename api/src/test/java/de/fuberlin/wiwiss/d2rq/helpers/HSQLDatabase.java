package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.sql.SQLScriptLoader;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A simple wrapper around a HSQL in-memory database for testing purposes
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class HSQLDatabase {
    public final static String HSQL_DRIVER_CLASS = org.hsqldb.jdbcDriver.class.getName();

    public final static String HSQL_USER = "d2rq";
    public final static String HSQL_PASS = "";

    private final String jdbcURL;
    private final Connection conn;

    public HSQLDatabase(String databaseName) {
        jdbcURL = "jdbc:hsqldb:mem:" + databaseName;
        try {
            Class.forName(HSQL_DRIVER_CLASS);
            conn = DriverManager.getConnection(jdbcURL + ";create=true",
                    HSQL_USER, HSQL_PASS);
        } catch (ClassNotFoundException | SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public String getJdbcURL() {
        return jdbcURL;
    }

    public String getUser() {
        return HSQL_USER;
    }

    public String getPassword() {
        return HSQL_PASS;
    }

    public Connection getConnection() {
        return conn;
    }

    public void executeSQL(String sql) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Reads SQL statements from a file, using the
     * syntax defined in {@link SQLScriptLoader}.
     *
     * @param filename relative to current directory
     */
    public void executeScript(String filename) {
        try {
            SQLScriptLoader.loadFile(new File(filename), conn);
        } catch (IOException | SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public PreparedStatement prepareSQL(String sql) throws SQLException {
        return conn.prepareStatement(sql);
    }

    /**
     * Returns the value of the first column of the first row of
     * a <code>SELECT</code> SQL query result.
     *
     * @param sql A <code>SELECT</code> query
     * @return Value as a string, or null if there are zero
     * result rows or the value is <code>NULL</code>
     */
    public String selectString(String sql) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) return null;
            return rs.getString(1);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<String> select(String sql) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<String> res = new ArrayList<>();
            int count = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String row = IntStream.rangeClosed(1, count).mapToObj(i -> {
                    try {
                        return rs.getString(i);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.joining(","));
                res.add(row);
            }
            return res;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the value of the first column of the first row of
     * a <code>SELECT</code> SQL query result.
     *
     * @param sql A <code>SELECT</code> query
     * @return Value as an object, or null if there are zero
     * result rows or the value is <code>NULL</code>
     */
    public Object selectObject(String sql) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (!rs.next()) return null;
            Object result = rs.getObject(1);
            rs.close();
            stmt.close();
            return result;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the value of the first column of the first row of
     * a <code>SELECT</code> SQL query result.
     *
     * @param sql A <code>SELECT</code> query
     * @return Value as a byte array, or null if there are zero
     * result rows or the value is <code>NULL</code>
     */
    public byte[] selectBytes(String sql) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (!rs.next()) return null;
            byte[] result = rs.getBytes(1);
            rs.close();
            stmt.close();
            return result;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the class name of the SQL value in the first column of the first row of
     * a <code>SELECT</code> SQL query result.
     *
     * @param sql A <code>SELECT</code> query
     * @return The class name of the value, or null if there are zero
     * result rows or the value is <code>NULL</code>
     */
    public String selectClassName(String sql) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            if (!rs.next()) return null;
            String result = rs.getMetaData().getColumnClassName(1);
            rs.close();
            stmt.close();
            return result;
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Drops all tables.
     */
    public void clear() {
        executeSQL("DROP SCHEMA PUBLIC CASCADE");
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Closes the database.
     *
     * @param dropAll Drops all tables if true.
     */
    public void close(boolean dropAll) {
        if (dropAll) {
            clear();
        }
        close();
    }
}
