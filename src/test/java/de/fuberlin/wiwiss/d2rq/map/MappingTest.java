package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.stream.Collectors;

public class MappingTest {
    private final static Resource database1 = ResourceFactory.createResource("http://test/db");
    private final static Resource database2 = ResourceFactory.createResource("http://test/db2");
    private final static Resource classMap1 = ResourceFactory.createResource("http://test/classMap1");

    @Test
    public void testNoDatabasesInitially() {
        Mapping m = MappingFactory.createEmpty();
        Assert.assertEquals(0, m.listDatabases().count());
        Assert.assertNull(m.findDatabase(database1));
    }

    @Test
    public void testReturnAddedDatabase() {
        Mapping m = MappingFactory.createEmpty();
        Database db = m.createDatabase(database1);
        m.addDatabase(db);
        Assert.assertEquals(Collections.singletonList(db), m.listDatabases().collect(Collectors.toList()));
        Assert.assertEquals(db, m.findDatabase(database1));
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
}
