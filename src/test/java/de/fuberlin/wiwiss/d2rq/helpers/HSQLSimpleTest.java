package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.mapgen.MappingGenerator;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A set of simple tests that apply various parts of D2RQ to
 * an HSQL database, exercising our test helpers like
 * {@link HSQLDatabase} and {@link MappingHelper}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class HSQLSimpleTest {
    private final static String EX = "http://example.org/";
    private final static Logger LOGGER = LoggerFactory.getLogger(HSQLSimpleTest.class);

    static {
        ConnectedDB.registerJDBCDriver("org.hsqldb.jdbcDriver");
    }

    private HSQLDatabase db;

    @Before
    public void setUp() {
        db = new HSQLDatabase("test");
        List<String> tables = db.select("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.SYSTEM_TABLES where TABLE_TYPE='TABLE'");
        tables.forEach(t -> { // from some other test...
            LOGGER.warn("Unexpected TABLE '{}' before test, drop it", t);
            db.executeSQL("DROP TABLE " + t);
        });
        db.executeSQL("CREATE TABLE TEST (ID INT PRIMARY KEY, VALUE VARCHAR(50) NULL)");
    }

    @After
    public void tearDown() {
        db.close(true);
    }

    @Test
    public void testFindTableWithSchemaInspector() {
        DatabaseSchemaInspector schema = new DatabaseSchemaInspector(
                new ConnectedDB(db.getJdbcURL(), db.getUser(), db.getPassword()));
        Assert.assertEquals(new ArrayList<RelationName>() {{
            add(new RelationName(null, "TEST"));
        }}, schema.listTableNames(null));
    }

    @Test
    public void testGenerateDefaultMappingModel() {
        Model model = generateDefaultMappingModel();
        Assert.assertFalse(model.isEmpty());
    }

    @Test
    public void testGenerateSomeClassMapsInDefaultMapping() { // ??
        Mapping mapping = generateDefaultMapping();
        Collection<Resource> res = mapping.classMapResources();
        LOGGER.debug("Class-maps: {}", res);
        Assert.assertEquals("Unexpected class-maps" + res, 1, res.size());
    }

    @Test
    public void testDefaultMappingWithHelloWorld() {
        db.executeSQL("INSERT INTO TEST VALUES (1, 'Hello World!')");
        GraphD2RQ g = generateDefaultGraphD2RQ();
        Assert.assertTrue(g.contains(Node.ANY, Node.ANY, NodeFactory.createLiteral("Hello World!")));
    }

    @Test
    public void testGenerateEmptyGraphFromSimpleD2RQMapping() {
        Mapping m = MappingHelper.readFromTestFile("helpers/simple.ttl");
        m.configuration().setServeVocabulary(false);
        GraphD2RQ g = m.getDataModel().getGraph();
        Assert.assertTrue(g.isEmpty());
    }

    @Test
    public void testGenerateTripleFromSimpleD2RQMapping() {
        Mapping m = MappingHelper.readFromTestFile("helpers/simple.ttl");
        m.configuration().setServeVocabulary(false);
        db.executeSQL("INSERT INTO TEST VALUES (1, 'Hello World!')");
        GraphD2RQ g = m.getDataModel().getGraph();
        Assert.assertTrue(g.contains(
                NodeFactory.createURI(EX + "test/1"), RDF.Nodes.type, NodeFactory.createURI(EX + "Test")));
        Assert.assertEquals(1, g.size());
    }

    private Model generateDefaultMappingModel() {
        ConnectedDB cdb = new ConnectedDB(db.getJdbcURL(), db.getUser(), null);
        MappingGenerator generator = new MappingGenerator(cdb);
        return generator.mappingModel(EX);
    }

    private Mapping generateDefaultMapping() {
        return MappingFactory.create(generateDefaultMappingModel(), EX);
    }

    private GraphD2RQ generateDefaultGraphD2RQ() {
        return generateDefaultMapping().getDataModel().getGraph();
    }
}
