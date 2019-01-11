package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.helpers.MappingTestHelper;
import de.fuberlin.wiwiss.d2rq.map.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.Models;

import java.util.function.Consumer;

/**
 * To test mapping creation from scratch.
 * Created by @ssz on 02.01.2019.
 */
@RunWith(Parameterized.class)
public class AssembleMappingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssembleMappingTest.class);

    private final ConnectionData data;

    public AssembleMappingTest(ConnectionData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ConnectionData[] getData() {
        return ConnectionData.values();
    }

    @Test
    public void testCreateSingleClassMapping() {
        String uri = "http://map";
        String ns = uri + "#";
        Mapping mapping = MappingFactory.create();
        OntModelFactory.createModel(mapping.asModel().getGraph()).setID(uri);
        Database db = mapping.createDatabase(null)
                .setJDBCDSN(data.getJdbcIRI("iswc").getIRIString())
                .setUsername(data.getUser()).setPassword(data.getPwd())
                .addConnectionProperties(data.getConnectionProperties());

        mapping.createClassMap(null).setDatabase(db)
                .setURIPattern(data.fixIRI("conferences/@@conferences.ConfID@@"))
                .addClass(ns + "XXX");

        mapping.validate();
        MappingTestHelper.print(mapping);

        OntGraphModel m = OntModelFactory.createModel(mapping.getData());
        D2RQTestHelper.print(m);
        Assert.assertEquals(5, m.size());
    }

    @Test
    public void testDynamicDataProperties() {
        String uri = "http://map.ex";
        String ns = uri + "#";
        String prefix = "map";
        Mapping mapping = MappingFactory.create();
        OntModelFactory.createModel(mapping.asModel().getGraph()).setID(uri);
        mapping.getConfiguration().setControlOWL(true);

        mapping.getSchema().getPrefixMapping().setNsPrefix(prefix, ns);
        Assert.assertEquals(ns, mapping.getSchema().getPrefixMapping().getNsPrefixURI(prefix));
        Assert.assertEquals(ns, mapping.asModel().getNsPrefixURI(prefix));

        Database db = mapping.createDatabase(null)
                .setJDBCDSN(data.getJdbcIRI("iswc").getIRIString())
                .setUsername(data.getUser()).setPassword(data.getPwd())
                .addConnectionProperties(data.getConnectionProperties());

        ClassMap c = mapping.createClassMap(null).setDatabase(db)
                .addClass(ns + "Organization")
                .setURIPattern(data.fixIRI(ns + "Organization@@organizations.OrgID@@"));

        // dynamic property:
        mapping.createPropertyBridge(null).setBelongsToClassMap(c)
                .addDynamicProperty(data.fixIRI("@@organizations.URI@@")).setColumn(data.fixIRI("organizations.Name"));
        Assert.assertEquals(1, mapping.listPropertyBridges().flatMap(PropertyBridge::listDynamicProperties).count());

        mapping.validate();

        OntGraphModel full = OntModelFactory.createModel(mapping.getData());
        D2RQTestHelper.print(full);

        MappingTestHelper.print(mapping);

        Model schema = mapping.getVocabularyModel();
        D2RQTestHelper.print(schema);
        Assert.assertEquals(3, schema.size());

        Assert.assertEquals(9, full.listDataProperties().peek(x -> LOGGER.debug("DataProperty:{}", x)).count());
        Assert.assertEquals(9, full.classAssertions().peek(x -> LOGGER.debug("Individual:{}", x))
                .peek(i -> Assert.assertEquals(1, i.positiveAssertions()
                        .peek(x -> LOGGER.debug("Assertion:{}", Models.toString(x)))
                        .peek((Consumer<Statement>) s -> Assert.assertTrue(s.getObject().isLiteral())).count()))
                .count());
    }

    @Test
    public void testDynamicObjectProperties() {
        String uri = "http://annotation.semanticweb.org/iswc/iswc.daml";
        String ns = uri + "#";
        Mapping mapping = MappingFactory.create();
        OntModelFactory.createModel(mapping.getSchema()).setID(uri).getModel().setNsPrefix("m", ns);
        mapping.getConfiguration().setControlOWL(true);

        Database db = mapping.createDatabase(null)
                .setJDBCDSN(data.getJdbcIRI("iswc").getIRIString())
                .setUsername(data.getUser()).setPassword(data.getPwd())
                .addConnectionProperties(data.getConnectionProperties());

        ClassMap x = mapping.createClassMap("X").setDatabase(db)
                .setConstantValue()
                .addClass(ns + "XX");
        ClassMap y = mapping.createClassMap("Y").setDatabase(db)
                //.setURIPattern(ns + "y")
                .setBNodeIdColumns(data.fixIRI("topics.URI"))
                //.setConstantValue()
                .addClass(ns + "YY");

        mapping.createPropertyBridge(null).setBelongsToClassMap(x).setRefersToClassMap(y)
                .addDynamicProperty(data.fixIRI("@@topics.URI@@"));
        Assert.assertEquals(1, mapping.listPropertyBridges().flatMap(PropertyBridge::listDynamicProperties).count());

        mapping.validate();

        OntGraphModel full = OntModelFactory.createModel(mapping.getData());
        D2RQTestHelper.print(full);

        Assert.assertEquals(2, full.listClasses().count());
        Assert.assertEquals(0, full.listNamedIndividuals().count());
        Assert.assertEquals(0, full.listDataProperties().count());
        Assert.assertEquals(16, full.classAssertions().count());
        Assert.assertEquals(15, full.listObjectProperties()
                .peek(p -> LOGGER.debug("OP:::{}", p))
                .peek(p -> Assert.assertEquals(1, full.statements(null, p, null)
                        .peek(s -> LOGGER.debug("Assertion:::{}", s))
                        .peek(s -> Assert.assertTrue(s.getSubject().canAs(OntIndividual.class)))
                        .peek(s -> Assert.assertTrue(s.getObject().canAs(OntIndividual.class)))
                        .count()))
                .count());
    }
}
