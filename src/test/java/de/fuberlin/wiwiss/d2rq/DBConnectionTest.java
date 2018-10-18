package de.fuberlin.wiwiss.d2rq;

import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.MappingHelper;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.rdf.model.Model;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ISWCData;

import java.sql.*;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author jgarbers
 */
public class DBConnectionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBConnectionTest.class);

    private Model mapModel;

    private Collection<Database> databases;
    private Database firstDatabase;
    private ConnectedDB cdb;

    private String simplestQuery;
    private String mediumQuery;
    private String complexQuery;

    @Before
    public void setUp() {
        mapModel = ISWCData.MYSQL.loadMapping("http://test.").asModel();
        databases = MappingFactory.create(mapModel, null).listDatabases().collect(Collectors.toList());
        firstDatabase = databases.iterator().next();
        simplestQuery = "SELECT 1;";
        // mediumQuery = "SELECT PaperID from papers";
        mediumQuery = "SELECT DISTINCT papers.PaperID FROM rel_paper_topic, papers, topics WHERE papers.PaperID=rel_paper_topic.PaperID AND rel_paper_topic.TopicID=topics.TopicID AND topics.TopicID=3 AND rel_paper_topic.RelationType = 1 AND papers.Publish = 1;";
        complexQuery = "SELECT T0_Papers.PaperID, T0_Persons.URI, T1_Persons.Email, T1_Persons.URI FROM persons AS T1_Persons, papers AS T0_Papers, rel_person_paper AS T0_Rel_Person_Paper, persons AS T0_Persons WHERE T0_Persons.PerID=T0_Rel_Person_Paper.PersonID AND T0_Papers.PaperID=T0_Rel_Person_Paper.PaperID AND T0_Papers.Publish = 1 AND T0_Persons.URI=T1_Persons.URI AND (NOT (CONCAT('http://www.conference.org/conf02004/paper#Paper' , CAST(T0_Papers.PaperID AS char) , '') = T0_Persons.URI));";

    }

    @After
    public void tearDown() {
        mapModel.close();
        if (cdb != null) cdb.close();
    }

    @Test
    public void testConnections() throws SQLException {
        for (Database db : databases) {
            cdb = MappingHelper.getConnectedDB(db);
            Connection c = cdb.connection();
            String result = performQuery(c, simplestQuery); //
            Assert.assertEquals(result, "1");
        }
    }

    private static String performQuery(Connection c, String theQuery) throws SQLException {
        StringBuilder query_results = new StringBuilder();
        Statement s;
        ResultSet rs;
        s = c.createStatement();
        rs = s.executeQuery(theQuery);
        int col = (rs.getMetaData()).getColumnCount();
        while (rs.next()) {
            for (int pos = 1; pos <= col; pos++) {
                if (pos > 1)
                    query_results.append(" ");
                query_results.append(rs.getString(pos));
            }
        } // end while

        rs.close();
        s.close();
        return query_results.toString();
    }

    public Connection manuallyConfiguredConnection() {
        String driverClass;
        String url;
        String name;
        String pass;

        driverClass = "com.mysql.jdbc.Driver";
        url = "jdbc:mysql:///iswc";
        name = "root"; //  "@localhost";
        pass = ""; // "";

        Connection c = null;
        try {
            Class.forName(driverClass);
            c = DriverManager.getConnection(url, name, pass);
            return c;
        } //end try
        catch (Exception x) {
            LOGGER.error("manuallyConfiguredConnection", x);
        }
        return null;
    }

    // without declarations in
    public void xtestManuallyConfiguredConnection() throws SQLException {
        Connection c = manuallyConfiguredConnection();
        // String query = simplestQuery;
        // String query = "select PaperID from papers";
        String query = "SELECT papers.PaperID, papers.Year FROM papers WHERE papers.Year=2002 AND papers.PaperID = 2 AND papers.Publish = 1;";

        String query_results = performQuery(c, query);
        c.close();
        Assert.assertEquals(query_results, "2 2002");
    }

    @Test
    public void testDistinct() throws SQLException {
        // there seems to be a problem with MSAccess databases
        // when using the DISTINCT keyword, Strings are truncated to 256 chars
        cdb = MappingHelper.getConnectedDB(firstDatabase);
        Connection c = cdb.connection();
        //Connection c=manuallyConfiguredConnection();
        String nonDistinct = "SELECT T0_Papers.Abstract FROM papers AS T0_Papers WHERE T0_Papers.PaperID=1 AND T0_Papers.Publish = 1;";
        String distinct = "SELECT DISTINCT T0_Papers.Abstract FROM papers AS T0_Papers WHERE T0_Papers.PaperID=1 AND T0_Papers.Publish = 1;";
        String distinctResult = performQuery(c, distinct);
        String nonDistinctResult = performQuery(c, nonDistinct);
        c.close();
        Assert.assertEquals(distinctResult, nonDistinctResult);
    }

    // fails with wrong MSAccess Iswc DB (doc/manual/ISWC.mdb revision < 1.5)
    // succeeds with revision 1.5
    @Test
    public void testMedium() throws SQLException {
        cdb = MappingHelper.getConnectedDB(firstDatabase);
        Connection c = cdb.connection();
        //Connection c=manuallyConfiguredConnection(); // 2 is ok, 1 fails
        String query = mediumQuery;
        String query_results = performQuery(c, query);
        c.close();
        Assert.assertNotNull(query_results);
    }

    // fails with MSAccess
    @Test
    public void testLongComplexSQLQuery() {
        cdb = MappingHelper.getConnectedDB(firstDatabase);
        //Connection c=manuallyConfiguredConnection(); // 2 is ok, 1 fails
        String query = complexQuery;
        try (Connection c = cdb.connection()) {
            performQuery(c, query);
        } catch (SQLException e) {
            throw new AssertionError("DBConnectionTest.testLong() is known to fail with MSAccess", e);
        }
        // Assert.assertEquals(query_results,"2 2002");
    }


}