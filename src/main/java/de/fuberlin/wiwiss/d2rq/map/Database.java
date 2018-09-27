package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.rdf.model.Resource;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface Database extends MapObject {
    int NO_LIMIT = -1;
    int NO_FETCH_SIZE = -1;

    // todo: either move to Mapping or delete from interface
    ConnectedDB connectedDB();

    // todo: either move to Mapping or delete from interface
    void useConnectedDB(ConnectedDB db);

    void setJDBCDSN(String jdbcDSN);

    String getJDBCDSN();

    void setJDBCDriver(String jdbcDriver);

    void setUsername(String username);

    void setPassword(String password);

    void setStartupSQLScript(Resource script);

    void setResultSizeLimit(int limit);

}
