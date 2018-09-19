package de.fuberlin.wiwiss.d2rq.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * Reads SQL statements from a file or other source.
 * <p>
 * Statements must end with semicolon and must end at the end of a line. Lines starting with -- are considered comments and are ignored.
 */
@SuppressWarnings("WeakerAccess")
public class SQLScriptLoader implements AutoCloseable {
    private final static Logger LOGGER = LoggerFactory.getLogger(SQLScriptLoader.class);

    /**
     * Loads a SQL script from a file and executes it.
     *
     * @param file {@link File}
     * @param conn {@link Connection}
     * @throws IOException  I/O trouble
     * @throws SQLException SQL trouble
     */
    public static void loadFile(File file, Connection conn) throws IOException, SQLException {
        loadFile(file.toPath(), conn);
    }

    /**
     * Loads a SQL script from a file and executes it.
     *
     * @param file {@link Path}
     * @param conn {@link Connection}
     * @throws IOException  I/O trouble
     * @throws SQLException SQL trouble
     */
    public static void loadFile(Path file, Connection conn) throws IOException, SQLException {
        LOGGER.info("Reading SQL script from {}", file);
        load(() -> Files.newBufferedReader(file, StandardCharsets.UTF_8), conn);
    }

    /**
     * Loads a SQL script from a URL and executes it.
     *
     * @param url  {@link URI}
     * @param conn {@link Connection}
     * @throws IOException  I/O trouble
     * @throws SQLException SQL trouble
     */
    public static void loadURI(URI url, Connection conn) throws IOException, SQLException {
        LOGGER.info("Reading SQL script from <{}>", url);
        load(() -> new InputStreamReader(url.toURL().openStream(), StandardCharsets.UTF_8), conn);
    }

    /**
     * Loads a SQL script from any reader and executes it.
     *
     * @param opener {@link IOStreamSupplier} for {@link Reader}s
     * @param conn   {@link Connection}
     * @throws IOException  I/O trouble
     * @throws SQLException SQL trouble
     */
    public static void load(IOStreamSupplier<Reader> opener, Connection conn) throws IOException, SQLException {
        try (Reader reader = opener.open()) {
            new SQLScriptLoader(reader, conn).execute();
        }
    }

    private final BufferedReader in;
    private final Connection conn;

    public SQLScriptLoader(Reader in, Connection conn) {
        this.in = new BufferedReader(in);
        this.conn = conn;
    }

    public void execute() throws SQLException {
        int lineNumber = 1;
        int statements = 0;

        try (Statement stmt = conn.createStatement()) {
            String line;
            StringBuilder sql = new StringBuilder();
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("--")) {
                    // comment, ignore this line
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("Comment: '{}'", line);
                    }
                } else {
                    if (line.endsWith(";")) {
                        sql.append(line, 0, line.length() - 1);
                        String s = sql.toString().trim();
                        if (!s.isEmpty()) {
                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("Execute: '{}'", s);
                            }
                            stmt.execute(s);
                            statements++;
                        }
                        sql = new StringBuilder();
                    } else {
                        sql.append(line);
                        sql.append('\n');
                    }
                }
                lineNumber++;
            }
            String s = sql.toString().trim();
            if (!s.isEmpty()) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Execute: '{}'", s);
                }
                stmt.execute(s);
            }
            if (LOGGER.isDebugEnabled())
                LOGGER.info("Done, {} lines, {} statements", (lineNumber - 1), statements);
        } catch (SQLException ex) {
            throw new SQLException("in line " + lineNumber + ": " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() throws Exception {
        Exception res = new Exception("Can't close " + SQLScriptLoader.class.getName());
        try {
            in.close();
        } catch (IOException io) {
            res.addSuppressed(io);
        }
        try {
            conn.close();
        } catch (SQLException sql) {
            res.addSuppressed(sql);
        }
        if (res.getSuppressed().length != 0) {
            throw res;
        }
    }

    /**
     * A factory-opener to open anything, which can be closed.
     *
     * @param <S> any {@link Closeable}
     */
    @FunctionalInterface
    public interface IOStreamSupplier<S extends Closeable> {
        S open() throws IOException;
    }
}
