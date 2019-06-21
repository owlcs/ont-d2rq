package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.map.*;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.After;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public abstract class DatatypeTestBase {
    static final Logger LOGGER = LoggerFactory.getLogger(DatatypeTestBase.class);

    private final static String EX = "http://example.com/";
    private final static String dbURI = EX + "db";
    private final static String classMapURI = EX + "classmap";
    private final static String propertyBridgeURI = EX + "propertybridge";
    private final static String valueProperty = EX + "value";

    private String jdbcURL;
    private String driver;
    private String user;
    private String password;
    private String schema;
    private String script;

    private String datatype;
    private Graph graph;
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
                    stmt.execute(String.format("DROP TABLE %s", table));
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
        mapping.getConfiguration().setUseAllOptimizations(true).setServeVocabulary(false);
        mapping.validate();
        graph = mapping.getData();
        Database db = mapping.databases().findFirst().orElseThrow(AssertionError::new);
        inspector = MappingHelper.getConnectedDB(db).schemaInspector();
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
        Mapping mapping = MappingFactory.create();
        Database database = mapping.createDatabase(dbURI)
                .setJDBCDSN(jdbcURL)
                .setJDBCDriver(driver)
                .setUsername(user)
                .setPassword(password)
                .setStartupSQLScript(script.toString());
        ClassMap classMap = mapping.addDatabase(database).createClassMap(classMapURI)
                .setDatabase(database)
                .setURIPattern("row/@@T_" + datatype + ".ID@@");
        mapping.createPropertyBridge(propertyBridgeURI)
                .setBelongsToClassMap(classMap)
                .addProperty(valueProperty)
                .setColumn("T_" + datatype + ".VALUE");
        return mapping;
    }
}
