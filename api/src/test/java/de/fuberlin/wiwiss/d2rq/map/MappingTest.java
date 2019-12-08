package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.impl.DatabaseImpl;
import de.fuberlin.wiwiss.d2rq.map.impl.MappingImpl;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.owlcs.d2rq.conf.ISWCData;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

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
        Assert.assertEquals(Collections.singletonList(db), m.databases().collect(Collectors.toList()));
        Assert.assertTrue(m.database("x").isPresent());
    }

    @Test
    public void testNoDatabaseCausesValidationError() {
        Mapping m = MappingFactory.create();
        Assert.assertEquals(0, m.databases().count());
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
        Assert.assertEquals(0, m.classMaps().count());
        Assert.assertEquals(0, m.databases().count());
        Assert.assertEquals(0, m.downloadMaps().count());
        Assert.assertEquals(0, m.additionalProperties().count());
        Assert.assertEquals(0, m.translationTables().count());
        Assert.assertEquals(0, m.propertyBridges().count());
    }

    @Test
    public void testReturnAddedClassMaps() {
        Mapping m = MappingFactory.create();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertEquals(1, m.classMaps().count());
        Assert.assertNotNull(m.addClassMap(c));
        ClassMap found = m.classMaps().filter(x -> classMap1.equals(x.asResource().getURI()))
                .findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(c, found);
        Assert.assertEquals(1, m.classMaps().count());
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
        try (Mapping m = ISWCData.MYSQL.loadMapping("http://x#")) {
            Assert.assertEquals(1, m.databases().count());
            Database db = m.databases().findFirst().orElseThrow(AssertionError::new);
            db.addConnectionProperty("a", "b");

            MappingHelper.asConnectingMapping(m).connect();
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
        try (Mapping m = ISWCData.MYSQL.loadMapping("http://x#")) {
            Assert.assertEquals(35, m.propertyBridges().count());
            Assert.assertEquals(42, MappingHelper.asConnectingMapping(m).compiledPropertyBridges().size());
            Assert.assertEquals(40, m.propertyBridges().count());

            PropertyBridge b = MappingUtils.findPropertyBridge(m, "organizations_Type_U");
            m.asModel().removeAll(b.asResource(), null, null);
            Assert.assertEquals(39, m.propertyBridges().count());
            Assert.assertEquals(41, MappingHelper.asConnectingMapping(m).compiledPropertyBridges().size());

            // return back:
            ClassMap c = MappingUtils.findClassMap(m, "Organizations");
            m.createPropertyBridge(m.asModel().expandPrefix("map:organizations_Type_U"))
                    .setBelongsToClassMap(c).addProperty(RDF.type)
                    .setURIPattern("http://annotation.semanticweb.org/iswc/iswc.daml#University")
                    .addCondition("organizations.Type = 'U'");

            MappingUtils.print(m);
            Assert.assertEquals(42, MappingHelper.asConnectingMapping(m).compiledPropertyBridges().size());
            Assert.assertEquals(40, m.propertyBridges().count());

        }
    }

    @Test
    public void testConfiguration() {
        Mapping m = MappingFactory.create();
        Configuration c = m.getConfiguration();
        Assert.assertNotNull(c);
        c.validate();
        Assert.assertTrue(c.getServeVocabulary());
        Assert.assertFalse(c.getUseAllOptimizations());
        Assert.assertFalse(c.getControlOWL());
        Assert.assertFalse(c.getWithCache());
        Assert.assertEquals(10_000, c.getCacheMaxSize());
        Assert.assertEquals(30_000_000, c.getCacheLengthLimit());

        c.setServeVocabulary(false)
                .setControlOWL(true)
                .setUseAllOptimizations(true)
                .setWithCache(true)
                .setCacheMaxSize(1)
                .setCacheLengthLimit(2).validate();
        Assert.assertFalse(c.getServeVocabulary());
        Assert.assertTrue(c.getUseAllOptimizations());
        Assert.assertTrue(c.getControlOWL());
        Assert.assertTrue(c.getWithCache());
        Assert.assertEquals(1, c.getCacheMaxSize());
        Assert.assertEquals(2, c.getCacheLengthLimit());
        MappingUtils.print(m);
    }

    @Test
    public void testLocking() {
        Mapping m = MappingFactory.create();
        Assert.assertFalse(m.isLocked());
        ClassMap cm = m.createClassMap("cm");
        m.lock();
        try {
            m.createClassMap("cm2");
            Assert.fail("Possible to add class-map");
        } catch (D2RQException d) {
            LOGGER.debug("Expected '{}'", d.getMessage());
        }
        Statement s = Iter.findFirst(m.asModel().listStatements(cm.asResource(), RDF.type, D2RQ.ClassMap))
                .orElseThrow(AssertionError::new);
        try {
            m.asModel().remove(s);
            Assert.fail("Possible to delete class-map");
        } catch (D2RQException d) {
            LOGGER.debug("Expected '{}'", d.getMessage());
        }
        Assert.assertTrue(m.isLocked());
        m.unlock();
        m.createClassMap("cm2");
        m.asModel().remove(s);
        Assert.assertFalse(m.isLocked());
    }

    @Test
    public void testReConnectionOnImpl() {
        MappingImpl mapping = (MappingImpl) ISWCData.MYSQL.loadMapping();
        DatabaseImpl d = Iter.findFirst(mapping.listDatabases()).orElseThrow(AssertionError::new);
        long size = mapping.getDataModel().size();
        Assert.assertTrue(mapping.isConnected());
        Assert.assertTrue(mapping.getConnectedDB(d).isConnected());
        Assert.assertSame(mapping.getConnectedDB(d), MappingHelper.getConnectedDB(d));
        mapping.close();
        Assert.assertFalse(mapping.isConnected());
        Assert.assertFalse(mapping.getConnectedDB(d).isConnected());
        Assert.assertEquals(size, mapping.getDataModel().size());
        Assert.assertTrue(mapping.isConnected());
        Assert.assertTrue(mapping.getConnectedDB(d).isConnected());
        mapping.close();
        Assert.assertFalse(mapping.isConnected());
        Assert.assertFalse(mapping.getConnectedDB(d).isConnected());
    }

    @Test
    public void testCachingD2RQGraph() {
        Mapping mapping = ISWCData.MYSQL.loadMapping();
        Graph g;
        Assert.assertFalse(mapping.getConfiguration().getWithCache());
        g = mapping.getData();
        Assert.assertTrue(g instanceof GraphD2RQ);
        Assert.assertSame(g, mapping.getData());

        mapping.getConfiguration().setWithCache(true);
        Assert.assertTrue(mapping.getConfiguration().getWithCache());
        g = mapping.getData();
        Assert.assertTrue(g instanceof CachingGraph);
        Assert.assertTrue(((CachingGraph) g).getBase() instanceof GraphD2RQ);
        Assert.assertSame(g, mapping.getData());

        mapping.getConfiguration().setWithCache(false);
        Assert.assertFalse(mapping.getConfiguration().getWithCache());
        Assert.assertTrue(mapping.getData() instanceof GraphD2RQ);
    }
}
