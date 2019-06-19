package de.fuberlin.wiwiss.d2rq.dbschema;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ru.avicomp.d2rq.conf.ConnectionData;

public class ISWCSchemaTest {
    private final static String driverClass = "com.mysql.jdbc.Driver";
    private final static String jdbcURL = ConnectionData.MYSQL.getJdbcConnectionString("iswc");

    private ConnectedDB db;

    @Before
    public void setUp() throws Exception {
        Class.forName(driverClass);
        db = new ConnectedDB(jdbcURL, null, null);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void testRecognizeNullableColumn() {
        Attribute personEmail = new Attribute(null, "persons", "Email");
        Assert.assertTrue(db.isNullable(personEmail));
    }

    @Test
    public void testRecognizeNonNullableColumn() {
        Attribute personID = new Attribute(null, "persons", "PerID");
        Assert.assertFalse(db.isNullable(personID));
    }
}
