package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Properties;
import java.util.stream.Collectors;

@SuppressWarnings("FieldCanBeLocal")
public class MappingTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(MappingTest.class);

    private static String database1 = "http://test/db";
    private static String database2 = "http://test/db2";
    private static String classMap1 = "http://test/classMap1";

    @Test
    public void testReturnAddedDatabase() {
        Mapping m = MappingFactory.create();
        Database db = m.createDatabase(database1).setJDBCDSN("x");
        Assert.assertEquals(Collections.singletonList(db), m.listDatabases().collect(Collectors.toList()));
        Assert.assertTrue(m.findDatabase("x").isPresent());
    }

    @Test
    public void testNoDatabaseCausesValidationError() {
        Mapping m = MappingFactory.create();
        Assert.assertEquals(0, m.listDatabases().count());
        try {
            m.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_NO_DATABASE, ex.errorCode());
        }
    }

    @Test
    public void testReturnResourceFromNewClassMap() {
        Mapping m = MappingFactory.create();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertEquals(classMap1, c.asResource().getURI());
    }

    @Test
    public void testNewClassMapHasNoDatabase() {
        Mapping m = MappingFactory.create();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertNull(c.getDatabase());
    }

    @Test
    public void testClassMapReturnsAssignedDatabase() {
        Mapping m = MappingFactory.create();
        Database db = m.createDatabase(database1);
        ClassMap c = m.createClassMap(classMap1);
        c.setDatabase(db);
        Assert.assertEquals(db, c.getDatabase());
    }

    @Test
    public void testMultipleDatabasesForClassMapCauseValidationError() {
        Mapping m = MappingFactory.create();
        Database db1 = m.createDatabase(database1).setJDBCDSN("jdbc://x");
        m.validate();
        ClassMap c = m.createClassMap(classMap1).setDatabase(db1).setURIColumn("TestDB.TestCol1")
                .addPropertyBridge(m.createPropertyBridge(null)
                        .setURIColumn("TestDB.TestCol2").addProperty(RDFS.comment));
        m.validate(false);
        Database db2 = m.createDatabase(database2);
        c.asResource().addProperty(D2RQ.dataStorage, db2.asResource());
        try {
            c.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals("Message: " + ex.getMessage(), D2RQException.CLASSMAP_DUPLICATE_DATABASE, ex.errorCode());
        }
    }

    @Test
    public void testClassMapWithoutDatabaseCausesValidationError() {
        Mapping m = MappingFactory.create();
        ClassMap c = m.createClassMap(classMap1);
        m.createDatabase(database1).setJDBCDSN("jdbc:mysql:///db");
        try {
            c.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals("Message: " + ex.getMessage(), D2RQException.CLASSMAP_NO_DATABASE, ex.errorCode());
        }
        try {
            m.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals("Message: " + ex.getMessage(), D2RQException.CLASSMAP_NO_DATABASE, ex.errorCode());
        }
    }

    @Test
    public void testDatabaseWithWrongDriverCausesValidationError() {
        String driver = "nonexistent";
        String uri = "jdbc:mysql:///db";
        Database d = MappingFactory.create().createDatabase("x")
                .setJDBCDSN(uri).setJDBCDriver(driver);
        Assert.assertEquals(driver, d.getJDBCDriver());
        Assert.assertEquals(uri, d.getJDBCDSN());
        try {
            d.validate();
        } catch (D2RQException ex) {
            Assert.assertTrue(ex.getMessage().contains(driver));
            Assert.assertEquals("Message: " + ex.getMessage(),
                    D2RQException.DATABASE_JDBCDRIVER_CLASS_NOT_FOUND, ex.errorCode());
        }
        driver = MappingTest.class.getName();
        d.setJDBCDriver(driver);
        Assert.assertEquals(driver, d.getJDBCDriver());
        Assert.assertEquals(uri, d.getJDBCDSN());
        try {
            d.validate();
        } catch (D2RQException ex) {
            Assert.assertTrue(ex.getMessage().contains(driver));
            Assert.assertEquals("Message: " + ex.getMessage(),
                    D2RQException.DATABASE_JDBCDRIVER_CLASS_NOT_FOUND, ex.errorCode());
        }

    }

    @Test
    public void testEmptyMapping() {
        Mapping m = MappingFactory.create();
        Assert.assertEquals(0, m.listClassMaps().count());
        Assert.assertEquals(0, m.listDatabases().count());
        Assert.assertEquals(0, m.listDownloadMaps().count());
        Assert.assertEquals(0, m.listAdditionalProperties().count());
        Assert.assertEquals(0, m.listTranslationTables().count());
        Assert.assertEquals(0, m.listPropertyBridges().count());
    }

    @Test
    public void testReturnAddedClassMaps() {
        Mapping m = MappingFactory.create();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertEquals(1, m.listClassMaps().count());
        Assert.assertNotNull(m.addClassMap(c));
        ClassMap found = m.listClassMaps().filter(x -> classMap1.equals(x.asResource().getURI()))
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(c, found);
        Assert.assertEquals(1, m.listClassMaps().count());
    }

    @Test
    public void testAddDatabaseConnectionProperties() {
        Database d = MappingFactory.create().createDatabase("db").addConnectionProperty("a", "b");
        Properties properties = d.getConnectionProperties();
        Assert.assertNotNull(properties);
        Assert.assertEquals(1, properties.size());
        Assert.assertEquals("b", properties.getProperty("a"));

        d.addConnectionProperty("a", "c").addConnectionProperty("A", "X");
        properties = d.getConnectionProperties();
        Assert.assertEquals(2, properties.size());
        Assert.assertEquals("c", properties.getProperty("a"));
        Assert.assertEquals("X", properties.getProperty("A"));

        Properties props = System.getProperties();
        Assert.assertEquals(props.size() + 2, d.addConnectionProperties(props).getConnectionProperties().size());
    }


    @Test
    public void testAddDatabaseColumns() {
        Database d = MappingFactory.create().createDatabase("db")
                .addColumn(Database.Column.NUMERIC, "Table.Col1")
                .addColumn(Database.Column.NUMERIC, "Table.Col2")
                .addColumn(Database.Column.NUMERIC, "Table.Col2")
                .addColumn(Database.Column.TEXT, "Table.Col2");
        Assert.assertEquals(2, d.columns(Database.Column.NUMERIC).count());
        Assert.assertEquals(1, d.columns(Database.Column.TEXT).count());

        Assert.assertEquals("Table.Col2", d.columns(Database.Column.TEXT).findFirst().orElseThrow(AssertionError::new));
    }

    @Test
    public void testConnectionWhileModification() {
        String file = MappingTest.class.getResource("/mapping-iswc.mysql.ttl").toString();
        try (Mapping m = MappingFactory.load(file, "ttl", "http://x#")) {
            Assert.assertEquals(1, m.listDatabases().count());
            Database db = m.listDatabases().findFirst().orElseThrow(AssertionError::new);
            db.addConnectionProperty("a", "b");

            Mappings.asConnectingMapping(m).connect();
            try {
                db.addConnectionProperty("c", "d");
            } catch (D2RQException e) {
                LOGGER.debug("Exception: {}", e.getMessage());
                Assert.assertEquals(D2RQException.DATABASE_ALREADY_CONNECTED, e.errorCode());
            }
        }
    }

    @Test
    public void testModifyCompiledPropertyBridges() {
        String file = MappingTest.class.getResource("/mapping-iswc.mysql.ttl").toString();
        try (Mapping m = MappingFactory.load(file, "ttl", "http://x#")) {
            Assert.assertEquals(35, m.listPropertyBridges().count());
            Assert.assertEquals(42, Mappings.asConnectingMapping(m).compiledPropertyBridges().size());
            Assert.assertEquals(40, m.listPropertyBridges().count());

            PropertyBridge b = MappingHelper.findPropertyBridge(m, "organizations_Type_U");
            m.asModel().removeAll(b.asResource(), null, null);
            Assert.assertEquals(39, m.listPropertyBridges().count());
            Assert.assertEquals(41, Mappings.asConnectingMapping(m).compiledPropertyBridges().size());

            // return back:
            ClassMap c = MappingHelper.findClassMap(m, "Organizations");
            m.createPropertyBridge(m.asModel().expandPrefix("map:organizations_Type_U"))
                    .setBelongsToClassMap(c).addProperty(RDF.type)
                    .setURIPattern("http://annotation.semanticweb.org/iswc/iswc.daml#University")
                    .addCondition("organizations.Type = 'U'");

            MappingHelper.print(m);
            Assert.assertEquals(42, Mappings.asConnectingMapping(m).compiledPropertyBridges().size());
            Assert.assertEquals(40, m.listPropertyBridges().count());

        }
    }
}
