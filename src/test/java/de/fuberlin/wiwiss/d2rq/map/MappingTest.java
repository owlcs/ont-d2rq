package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class MappingTest {
    private final static Resource database1 = ResourceFactory.createResource("http://test/db");
    private final static Resource database2 = ResourceFactory.createResource("http://test/db2");
    private final static Resource classMap1 = ResourceFactory.createResource("http://test/classMap1");

    @Test
    public void testNoDatabasesInitially() {
        Mapping m = MappingFactory.createEmpty();
        Assert.assertTrue(m.databases().isEmpty());
        Assert.assertNull(m.database(database1));
    }

    @Test
    public void testReturnAddedDatabase() {
        Mapping m = MappingFactory.createEmpty();
        Database db = m.createDatabase(database1);
        m.addDatabase(db);
        Assert.assertEquals(Collections.singletonList(db), new ArrayList<>(m.databases()));
        Assert.assertEquals(db, m.database(database1));
    }

    @Test
    public void testNoDatabaseCausesValidationError() {
        Mapping m = MappingFactory.createEmpty();
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
        Assert.assertEquals(classMap1, c.resource());
    }

    @Test
    public void testNewClassMapHasNoDatabase() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        Assert.assertNull(c.database());
    }

    @Test
    public void testClassMapReturnsAssignedDatabase() {
        Mapping m = MappingFactory.createEmpty();
        Database db = m.createDatabase(database1);
        ClassMap c = m.createClassMap(classMap1);
        c.setDatabase(db);
        Assert.assertEquals(db, c.database());
    }

    @Test
    public void testMultipleDatabasesForClassMapCauseValidationError() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        try {
            Database db1 = m.createDatabase(database1);
            c.setDatabase(db1);
            Database db2 = m.createDatabase(database2);
            c.setDatabase(db2);
            m.addClassMap(c);
            m.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.CLASSMAP_DUPLICATE_DATABASE, ex.errorCode());
        }
    }

    @Test
    public void testClassMapWithoutDatabaseCausesValidationError() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        try {
            Database db1 = m.createDatabase(database1);
            db1.setJDBCDSN("jdbc:mysql:///db");
            db1.setJDBCDriver("org.example.Driver");
            m.addDatabase(db1);
            m.addClassMap(c);
            m.validate();
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.CLASSMAP_NO_DATABASE, ex.errorCode());
        }
    }

    @Test
    public void testNewMappingHasNoClassMaps() {
        Mapping m = MappingFactory.createEmpty();
        Assert.assertTrue(m.classMapResources().isEmpty());
        Assert.assertNull(m.classMap(classMap1));
    }

    @Test
    public void testReturnAddedClassMaps() {
        Mapping m = MappingFactory.createEmpty();
        ClassMap c = m.createClassMap(classMap1);
        m.addClassMap(c);
        Assert.assertEquals(Collections.singleton(classMap1),
                new HashSet<>(m.classMapResources()));
        Assert.assertEquals(c, m.classMap(classMap1));
    }
}
