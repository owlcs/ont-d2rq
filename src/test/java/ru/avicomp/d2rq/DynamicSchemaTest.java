package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.jena.DynamicTriples;
import de.fuberlin.wiwiss.d2rq.jena.MaskGraph;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.impl.schema.SchemaGenerator;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.function.BiPredicate;

/**
 * Created by @ssz on 30.09.2018.
 */
@Ignore // todo: not ready
public class DynamicSchemaTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSchemaTest.class);

    @Test
    public void testOntologyID() {
        String uri = "http://test.x";
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        Graph g = SchemaGenerator.createMagicGraph(mapping.asModel().getGraph());

        OntGraphModel schema = OntModelFactory.createModel(g);
        schema.setID(uri).addComment("xxxx");
        Assert.assertEquals(uri, schema.getID().getURI());
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("Schema annotation: {}", s)).count());

        MappingHelper.print(schema);

        OntGraphModel mappingAsOWL = OntModelFactory.createModel(mapping.asModel().getGraph());
        Assert.assertEquals(1, mappingAsOWL.getID()
                .annotations().peek(s -> LOGGER.debug("Mapping annotation: {}", s)).count());
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("Schema annotation: {}", PrettyPrinter.toString(s))).count());
        Assert.assertEquals(uri, schema.getID().getURI());
        Assert.assertEquals(uri, mappingAsOWL.getID().getURI());
    }

    @Test
    public void testOWLClasses() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        Graph g = SchemaGenerator.createMagicGraph(mapping.asModel().getGraph());

        OntGraphModel schema = OntModelFactory.createModel(g);
        Assert.assertEquals(5, schema.listClasses().peek(c -> LOGGER.debug("OWLClass: {}", c)).count());
        Assert.assertEquals(1, schema.statements(null, OWL.equivalentClass, null)
                .peek(s -> LOGGER.debug("Equivalent Classes: {}", PrettyPrinter.toString(s))).count());

        MappingHelper.print(mapping);
        mapping.close();
        Assert.assertFalse(mapping.asModel().containsResource(OWL.Class));
        Assert.assertFalse(mapping.asModel().containsResource(OWL.equivalentClass));
    }

    @Test
    public void testProperties() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");
        mapping.validate(false);
        //mapping.compiledPropertyBridges();
        MappingHelper.print(mapping);
        //mapping.getDataModel().write(System.err, "ttl");

        DynamicTriples schemaFiller = SchemaGenerator.buildVisiblePart();


        OntModelFactory.createModel(mapping.getSchema()).listObjectProperties()
                .forEach(s -> LOGGER.debug("expected object property: {}", s));
        OntModelFactory.createModel(mapping.getSchema()).listDataProperties()
                .forEach(s -> LOGGER.debug("expected data property: {}", s));
        OntModelFactory.createModel(mapping.getSchema()).statements(null, RDFS.domain, null)
                .forEach(s -> LOGGER.debug("expected domain: {}", PrettyPrinter.toString(s)));
        OntModelFactory.createModel(mapping.getSchema()).statements(null, RDFS.range, null)
                .forEach(s -> LOGGER.debug("expected range: {}", PrettyPrinter.toString(s)));

        Graph g = SchemaGenerator.createMagicGraph(mapping.asModel().getGraph());
        OntGraphModel schema = OntModelFactory.createModel(g);

        Assert.assertEquals(8, schema.listObjectProperties().peek(p -> LOGGER.debug("OBJECT Property: {}", p)).count());
        Assert.assertEquals(13, schema.listDataProperties().peek(p -> LOGGER.debug("DATA Property: {}", p)).count());


        LOGGER.debug("DOMAINS: {}", schema.statements(null, RDFS.domain, null).peek(s -> LOGGER.debug("Domain Assertion: {}", PrettyPrinter.toString(s))).count());
        LOGGER.debug("RANGES: {}", schema.statements(null, RDFS.range, null).peek(s -> LOGGER.debug("Range Assertion: {}", PrettyPrinter.toString(s))).count());

        /*mapping.close();

        OntGraphModel schema = OntModelFactory.createModel(mapping.getSchema());
        schema.listDataProperties().forEach(s -> LOGGER.debug("Data: {}", s)); // 13
          // 8*/
    }

    @Test
    public void testISWC() {

        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");
        mapping.getConfiguration().setServeVocabulary(false);
        ClassMap c = MappingHelper.findClassMap(mapping, "PostalAddresses");
        c.addClass(ResourceFactory.createResource("Unknown"));

        Model data = mapping.getDataModel();
        //data.write(System.err, "ttl");
        //System.out.println("-----------------");


        Graph g = SchemaGenerator.createMagicGraph(mapping.asModel().getGraph());
        OntGraphModel all = OntModelFactory.createModel(g);

        //System.out.println("-----------------");
        all.add(data);

        MappingHelper.print(all);
    }


    @Test
    public void testMaskGraph() {
        Model m = MappingFactory.load("/mapping-iswc.mysql.ttl").asModel();

        Graph graph = new MaskGraph(m.getGraph(),
                ((BiPredicate<Graph, Triple>) (g, t) -> g.contains(t.getSubject(), RDF.type.asNode(), D2RQ.PropertyBridge.asNode()))
                        .or((g, t) -> g.contains(t.getSubject(), RDF.type.asNode(), D2RQ.ClassMap.asNode())));

        Model x = ModelFactory.createModelForGraph(graph);
        Resource r = x.createResource("ex", OWL.Class).addProperty(RDFS.comment, "dffdfd");

        MappingHelper.print(x);
        Assert.assertFalse(x.containsResource(D2RQ.PropertyBridge));
        Assert.assertTrue(x.containsResource(OWL.Class));

        Assert.assertTrue(m.containsResource(OWL.Class));
        Assert.assertTrue(m.containsResource(D2RQ.PropertyBridge));

        x.removeAll(r, null, null);
        MappingHelper.print(x);

        Assert.assertFalse(x.containsResource(OWL.Class));
        Assert.assertFalse(m.containsResource(OWL.Class));
    }


}
