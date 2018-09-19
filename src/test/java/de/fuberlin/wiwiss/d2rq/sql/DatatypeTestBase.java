package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.*;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.After;
import org.junit.Assert;

import java.net.URL;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public abstract class DatatypeTestBase {
    private final static String EX = "http://example.com/";
    private final static Resource dbURI = ResourceFactory.createResource(EX + "db");
    private final static Resource classMapURI = ResourceFactory.createResource(EX + "classmap");
    private final static Resource propertyBridgeURI = ResourceFactory.createResource(EX + "propertybridge");
    private final static Resource valueProperty = ResourceFactory.createProperty(EX + "value");

    private String jdbcURL;
    private String driver;
    private String user;
    private String password;
    private String schema;
    private String script;

    private String datatype;
    private GraphD2RQ graph;
    private DatabaseSchemaInspector inspector;

    @After
    public void tearDown() {
        if (graph != null) graph.close();
    }

    @SuppressWarnings("SameParameterValue")
    protected void initDB(String jdbcURL, String driver, String user, String password, String script, String schema) {
        this.jdbcURL = jdbcURL;
        this.driver = driver;
        this.user = user;
        this.password = password;
        this.script = script;
        this.schema = null;
        dropAllTables();
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    private void dropAllTables() {
        ConnectedDB.registerJDBCDriver(driver);
        try (ConnectedDB db = new ConnectedDB(jdbcURL, user, password)) {
            Statement stmt = db.connection().createStatement();
            try {
                for (String table : allTables()) {
                    stmt.execute("DROP TABLE " + table);
                }
            } finally {
                db.vendor().beforeClose(db.connection());
                stmt.close();
                db.vendor().afterClose(db.connection());
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected void createMapping(String datatype) {
        this.datatype = datatype;
        Mapping mapping = generateMapping();
        mapping.configuration().setServeVocabulary(false);
        mapping.configuration().setUseAllOptimizations(true);
        mapping.connect();
        graph = mapping.getDataGraph();
        inspector = mapping.databases().iterator().next().connectedDB().schemaInspector();
    }

    protected void assertMappedType(String rdfType) {
        Assert.assertEquals(rdfType, inspector.columnType(
                SQL.parseAttribute("T_" + datatype + ".VALUE")).rdfType());
    }

    protected void assertValues(String[] expectedValues) {
        assertValues(expectedValues, true);
    }

    protected void assertValues(String[] expectedValues, boolean searchValues) {
        ExtendedIterator<Triple> it = graph.find(Node.ANY, Node.ANY, Node.ANY);
        List<String> listedValues = new ArrayList<>();
        while (it.hasNext()) {
            listedValues.add(it.next().getObject().getLiteralLexicalForm());
        }
        Assert.assertEquals(Arrays.asList(expectedValues), listedValues);
        if (!searchValues) return;
        for (String value : expectedValues) {
            Assert.assertTrue("Expected literal not in graph: '" + value + "'",
                    graph.contains(Node.ANY, Node.ANY, NodeFactory.createLiteral(value)));
        }
    }

    protected void assertValuesNotFindable(String[] expectedValues) {
        for (String value : expectedValues) {
            Assert.assertFalse("Unexpected literal found in graph: '" + value + "'",
                    graph.contains(Node.ANY, Node.ANY, NodeFactory.createLiteral(value)));
        }
    }

    private Set<String> allTables() {
        ConnectedDB.registerJDBCDriver(driver);
        try (ConnectedDB db = new ConnectedDB(jdbcURL, user, password)) {
            Set<String> result = new HashSet<>();
            inspector = db.schemaInspector();
            for (RelationName name : inspector.listTableNames(schema)) {
                result.add(name.toString());
            }
            return result;
        }
    }
    private Mapping generateMapping() {
        URL script = DatatypeTestBase.class.getResource(this.script);
        Mapping mapping = MappingFactory.createEmpty();
        Database database = new Database(dbURI);
        database.setJDBCDSN(jdbcURL);
        database.setJDBCDriver(driver);
        database.setUsername(user);
        database.setPassword(password);
        database.setStartupSQLScript(ResourceFactory.createResource(script.toString()));
        mapping.addDatabase(database);
        ClassMap classMap = new ClassMap(classMapURI);
        classMap.setDatabase(database);
        classMap.setURIPattern("row/@@T_" + datatype + ".ID@@");
        mapping.addClassMap(classMap);
        PropertyBridge propertyBridge = new PropertyBridge(propertyBridgeURI);
        propertyBridge.setBelongsToClassMap(classMap);
        propertyBridge.addProperty(valueProperty);
        propertyBridge.setColumn("T_" + datatype + ".VALUE");
        classMap.addPropertyBridge(propertyBridge);
        return mapping;
    }
}
