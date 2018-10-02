package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.stream.Collectors;

public class MappingTest {
    private final static String database1 = "http://test/db";
    private final static String database2 = "http://test/db2";
    private final static Resource classMap1 = ResourceFactory.createResource("http://test/classMap1");

    @Test
    public void testReturnAddedDatabase() {
        Mapping m = MappingFactory.createEmpty();
        Database db = m.createDatabase(database1).setJDBCDSN("x");
        Assert.assertEquals(Collections.singletonList(db), m.listDatabases().collect(Collectors.toList()));
        Assert.assertTrue(m.findDatabase("x").isPresent());
    }

    @Test
    public void testNoDatabaseCausesValidationError() {
        Mapping m = MappingFactory.createEmpty();
        Assert.assertEquals(0, m.listDatabases().count());
        try {
            m.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.MAPPING_NO_DATABASE, ex.errorCode());
        }
    }

    @Test
    public void testReturnResourceFromNewClassMap() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertEquals(classMap1, c.asResource());
    }

    @Test
    public void testNewClassMapHasNoDatabase() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertNull(c.getDatabase());
    }

    @Test
    public void testClassMapReturnsAssignedDatabase() {
        Mapping m = MappingFactory.createEmpty();
        Database db = m.createDatabase(database1);
        ClassMap c = m.createClassMap(classMap1);
        c.setDatabase(db);
        Assert.assertEquals(db, c.getDatabase());
    }

    @Test
    public void testMultipleDatabasesForClassMapCauseValidationError() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        Database db1 = m.createDatabase(database1).setJDBCDSN("jdbc://x");
        c.setDatabase(db1);
        m.validate();
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
        Mapping m = MappingFactory.createEmpty();
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
        Database d = MappingFactory.createEmpty().createDatabase("x")
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
    public void testNewMappingHasNoClassMaps() {
        Mapping m = MappingFactory.createEmpty();
        Assert.assertEquals(0, m.listClassMaps().count());
        Assert.assertNull(m.findClassMap(classMap1));
    }

    @Test
    public void testReturnAddedClassMaps() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        m.addClassMap(c);
        Assert.assertEquals(Collections.singleton(classMap1),
                m.listClassMaps().map(MapObject::asResource).collect(Collectors.toSet()));
        Assert.assertEquals(c, m.findClassMap(classMap1));
    }

    @Test
    public void testAddDatabaseConnectionProperties() {
        Database d = MappingFactory.createEmpty().createDatabase("db").putConnectionProperty("a", "b");
        Properties properties = d.getConnectionProperties();
        Assert.assertNotNull(properties);
        Assert.assertEquals(1, properties.size());
        Assert.assertEquals("b", properties.getProperty("a"));

        d.putConnectionProperty("a", "c").putConnectionProperty("A", "X");
        properties = d.getConnectionProperties();
        Assert.assertEquals(2, properties.size());
        Assert.assertEquals("c", properties.getProperty("a"));
        Assert.assertEquals("X", properties.getProperty("A"));

        Properties props = System.getProperties();
        Assert.assertEquals(props.size() + 2, d.addConnectionProperties(props).getConnectionProperties().size());
    }


    @Test
    public void testAddDatabaseColumns() {
        Database d = MappingFactory.createEmpty().createDatabase("db")
                .addColumn(Database.Column.NUMERIC, "Table.Col1")
                .addColumn(Database.Column.NUMERIC, "Table.Col2")
                .addColumn(Database.Column.NUMERIC, "Table.Col2")
                .addColumn(Database.Column.TEXT, "Table.Col2");
        Assert.assertEquals(2, d.columns(Database.Column.NUMERIC).count());
        Assert.assertEquals(1, d.columns(Database.Column.TEXT).count());

        Assert.assertEquals("Table.Col2", d.columns(Database.Column.TEXT).findFirst().orElseThrow(AssertionError::new));
    }
}
