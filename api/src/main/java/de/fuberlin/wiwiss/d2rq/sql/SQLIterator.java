package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import org.apache.jena.query.QueryCancelledException;
import org.apache.jena.util.iterator.ClosableIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Executes an SQL query and delivers result rows as an iterator over {@link ResultRow}s.
 * The query is executed lazily. This class logs all executed SQL queries.
 *
 * @author Chris Bizer chris@bizer.de
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class SQLIterator implements ClosableIterator<ResultRow> {
    private final static Logger LOGGER = LoggerFactory.getLogger(SQLIterator.class);

    protected final String sql;
    protected final List<ProjectionSpec> columns;
    protected final ConnectedDB database;
    protected volatile Statement statement;
    protected ResultSet resultSet;
    protected ResultRow prefetchedRow;
    //private int numCols;
    protected boolean queryExecuted;
    protected boolean explicitlyClosed;
    protected volatile boolean cancelled;

    public SQLIterator(String sql, List<ProjectionSpec> columns, ConnectedDB db) {
        this.sql = sql;
        this.columns = columns;
        this.database = db;
    }

    @Override
    public boolean hasNext() {
        if (cancelled) {
            throw new QueryCancelledException();
        }
        if (explicitlyClosed) {
            return false;
        }
        if (prefetchedRow == null) {
            ensureQueryExecuted();
            tryFetchNextRow();
        }
        return prefetchedRow != null;
    }

    /**
     * @return The next query ResultRow.
     */
    @Override
    public ResultRow next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        ResultRow result = this.prefetchedRow;
        this.prefetchedRow = null;
        return result;
    }

    private synchronized void tryFetchNextRow() {
        if (this.resultSet == null) {
            this.prefetchedRow = null;
            return;
        }
        try {
            if (!this.resultSet.next()) {
                this.resultSet.close();
                this.resultSet = null;
                this.prefetchedRow = null;
                return;
            }
            //BeanCounter.totalNumberOfReturnedRows++;
            //BeanCounter.totalNumberOfReturnedFields += this.numCols;
            prefetchedRow = ResultRowMap.fromResultSet(resultSet, columns, database);
        } catch (SQLException ex) {
            throw new D2RQException(ex);
        }
    }

    /**
     * Make sure the SQL result set is closed and freed. Will auto-close when the record-set is exhausted.
     */
    @Override
    public void close() {
        if (explicitlyClosed) return;
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Closing SQLIterator");
        try {
            /* JDBC 4+ requires manual closing of result sets and statements */
            if (this.resultSet != null) {
                this.resultSet.close();
            }
            if (this.database != null) {
                this.database.vendor().beforeClose(this.database.connection());
            }
            if (this.statement != null) {
                this.statement.close();
            }
            if (this.database != null) {
                this.database.vendor().afterClose(this.database.connection());
            }
        } catch (SQLException ex) {
            throw new D2RQException(ex.getMessage() + "; query was: " + this.sql, ex);
        } finally {
            explicitlyClosed = true;
        }
    }

    public synchronized void cancel() {
        cancelled = true;
        if (statement != null) {
            try {
                database.vendor().beforeCancel(database.connection());
                statement.cancel();
                database.vendor().afterCancel(database.connection());
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public void remove() {
        throw new RuntimeException("Operation not supported");
    }

    private void ensureQueryExecuted() {
        if (this.queryExecuted) {
            return;
        }
        this.queryExecuted = true;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(sql);
        }
        //BeanCounter.totalNumberOfExecutedSQLQueries++;
        try {
            Connection con = this.database.connection();
            this.statement = con.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            if (database.useServerFetch()) {
                try {
                    this.statement.setFetchSize(database.fetchSize());
                } catch (SQLException ignored) {
                    /* Some drivers don't support fetch sizes, e.g. JDBC-ODBC */
                }
            }
            database.vendor().beforeQuery(database.connection());
            this.resultSet = this.statement.executeQuery(this.sql);
            database.vendor().afterQuery(database.connection());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("SQL result set created");
            //this.numCols = this.resultSet.getMetaData().getColumnCount();
        } catch (SQLException ex) {
            if (cancelled) {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("SQL query execution cancelled", ex);
                throw new QueryCancelledException();
            }
            throw new D2RQException(ex.getMessage() + ": " + this.sql, ex);
        }
    }
}
