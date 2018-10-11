package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.jena.MaskGraph;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.impl.schema.SchemaGenerator;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntDOP;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public void testConnectedISWC() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");
        mapping.getConfiguration().setServeVocabulary(false);
        populateOWL2(mapping);

        Model data = mapping.getDataModel();

        Graph schema = SchemaGenerator.createMagicGraph(mapping.asModel().getGraph());
        OntGraphModel all = OntModelFactory.createModel(schema);
        Assert.assertEquals(9, all.listClasses().count());

        all.add(data);

        MappingHelper.print(all);
        Assert.assertEquals(9, all.listClasses().count());

    }

    @Test
    public void testValidateISWC() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");
        populateOWL2(mapping);

        OntGraphModel schema = OntModelFactory.createModel(SchemaGenerator.createMagicGraph(mapping.asModel().getGraph()));
        MappingHelper.print(schema);

        Assert.assertEquals(9, schema.listClasses().count());
        schema.createOntEntity(OntClass.class, "OneMore");

        Assert.assertEquals(10, schema.listClasses().peek(c -> LOGGER.debug("{}", c)).count());
        Assert.assertEquals(1, mapping.asModel().listStatements(null, RDF.type, OWL.Class).toSet().size());

        Assert.assertEquals(8, schema.listObjectProperties().peek(p -> LOGGER.debug("{}", p)).count());
        Assert.assertEquals(13, schema.listDataProperties().peek(p -> LOGGER.debug("{}", p)).count());
        Assert.assertEquals(4, schema.listAnnotationProperties().peek(p -> LOGGER.debug("{}", p)).count());

        schema.ontObjects(OntPE.class).peek(p -> LOGGER.debug("Test: {}", p))
                .forEach(p -> Assert.assertTrue(p.domain().count() >= 1));
        schema.ontObjects(OntDOP.class).forEach(p -> Assert.assertTrue(p.range().count() >= 1));
    }

    @Test
    public void testDefaultISWC() {
        Mapping mapping = ConnectionData.MYSQL.toDocumentSource("iswc").getMapping();

        MappingHelper.print(mapping);

        OntGraphModel oldWay = OntModelFactory.createModel(mapping.getSchema());

        OntGraphModel schema = OntModelFactory.createModel(SchemaGenerator.createMagicGraph(mapping.asModel().getGraph()));
        MappingHelper.print(schema);
        Assert.assertEquals(oldWay.listClasses().count(), schema.listClasses().count());
        Assert.assertEquals(oldWay.listObjectProperties().count(), schema.listObjectProperties().count());
        Assert.assertEquals(oldWay.listDataProperties().count(), schema.listDataProperties().count());
    }

    public static void populateOWL2(Mapping mapping) { // todo: will be moved to the mapping (or mapping-generator ?)
        // owl:NamedIndividual declaration, class type for anonymous individuals;
        mapping.listClassMaps().forEach(x -> {
            if (x.listClasses().count() == 0) {
                Resource clazz = x.asResource();
                if (clazz.isAnon()) {
                    clazz = ResourceFactory.createResource("Unknown-" + clazz.getId());
                }
                x.addClass(clazz);
            }
            if (x.getBNodeIdColumns() == null)
                x.addClass(OWL.NamedIndividual);
        });

        // owl:sameAs, owl:differentFrom NamedIndividuals:
        Set<Property> symmetricIndividualPredicates = Stream.of(OWL.sameAs, OWL.differentFrom).collect(Collectors.toSet());
        mapping.listPropertyBridges()
                .filter(p -> p.listProperties().anyMatch(symmetricIndividualPredicates::contains))
                .forEach(p -> {
                    String u = p.getURIColumn();
                    if (u == null) return;
                    ClassMap c = p.getBelongsToClassMap();
                    if (c == null) return;
                    Database d = c.getDatabase();
                    mapping.createClassMap(null).setDatabase(d).addClass(OWL.NamedIndividual).setURIColumn(u);
                });
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
