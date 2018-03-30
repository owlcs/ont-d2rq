package de.fuberlin.wiwiss.d2rq.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


/**
 * Reads SQL statements from a file or other source.
 * <p>
 * Statements must end with semicolon and must end at the end of a line. Lines starting with -- are considered comments and are ignored.
 */
public class SQLScriptLoader {
    private final static Logger LOGGER = LoggerFactory.getLogger(SQLScriptLoader.class);

    /**
     * Loads a SQL script from a file and executes it.
     * @param file {@link File}
     * @param conn {@link Connection}
     * @throws FileNotFoundException no file found
     * @throws SQLException sql trouble
     */
    public static void loadFile(File file, Connection conn)
            throws FileNotFoundException, SQLException {
        LOGGER.info("Reading SQL script from {}", file);
        new SQLScriptLoader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), conn).execute();
    }

    /**
     * Loads a SQL script from a URL and executes it.
     * @param url {@link URI}
     * @param conn {@link Connection}
     * @throws FileNotFoundException no file found
     * @throws SQLException sql trouble
     */
    public static void loadURI(URI url, Connection conn)
            throws IOException, SQLException {
        LOGGER.info("Reading SQL script from <{}>", url);
        new SQLScriptLoader(new InputStreamReader(url.toURL().openStream(), StandardCharsets.UTF_8), conn).execute();
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
}
