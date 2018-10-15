package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.map.MappingTransform;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by @ssz on 30.09.2018.
 */
public class DynamicSchemaTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSchemaTest.class);

    @Test
    public void testOntologyID() {
        String uri1 = "http://test.x";
        String uri2 = "http://test.y";
        String comment = "xxxx";
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        Graph g = mapping.getSchema();

        OntGraphModel schema = OntModelFactory.createModel(g);
        schema.setID(uri1).addComment("xxxx");
        Assert.assertTrue(schema.contains(null, RDFS.comment, comment));
        Assert.assertTrue(schema.contains(schema.getResource(uri1), RDFS.comment, comment));

        Assert.assertEquals(uri1, schema.getID().getURI());
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("Schema annotation: {}", s)).count());

        D2RQTestHelper.print(schema);
        Assert.assertTrue(schema.contains(null, RDFS.comment, comment));

        OntGraphModel mappingAsOWL = OntModelFactory.createModel(mapping.asModel().getGraph());
        Assert.assertEquals(1, mappingAsOWL.getID()
                .annotations().peek(s -> LOGGER.debug("Mapping annotation: {}", s)).count());
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("Schema annotation: {}", PrettyPrinter.toString(s))).count());
        Assert.assertEquals(uri1, schema.getID().getURI());
        Assert.assertEquals(uri1, mappingAsOWL.getID().getURI());

        Assert.assertTrue(schema.contains(null, RDFS.comment, comment));
        schema.setID(uri2);
        Assert.assertEquals(uri2, schema.getID().getURI());
        Assert.assertEquals(uri2, mappingAsOWL.getID().getURI());
        Assert.assertFalse(schema.contains(schema.getResource(uri1), RDFS.comment, comment));
        Assert.assertTrue(schema.contains(schema.getResource(uri2), RDFS.comment, comment));
    }

    @Test
    public void testConnectedPredefinedISWC() {
        int totalNumberOfStatements = 441;
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        mapping.getConfiguration().setServeVocabulary(false).setControlOWL(true);

        Assert.assertFalse(mapping.getConfiguration().getServeVocabulary());
        Assert.assertTrue(mapping.getConfiguration().getControlOWL());

        Model data = mapping.getDataModel();
        //data.write(System.err, "ttl");

        int compiledTriples = mapping.compiledPropertyBridges().size();

        OntGraphModel inMemory = OntModelFactory.createModel();
        inMemory.add(mapping.getVocabularyModel());
        Assert.assertEquals(8, inMemory.listClasses().peek(x -> LOGGER.debug("Schema: {}", x)).count());
        inMemory.add(data);
        Assert.assertEquals(13, inMemory.listClasses().peek(x -> LOGGER.debug("Schema+Data: {}", x)).count());
        D2RQTestHelper.print(inMemory);
        Assert.assertEquals(totalNumberOfStatements, inMemory.size());

        mapping.getConfiguration().setServeVocabulary(true);

        OntGraphModel dynamic = OntModelFactory.createModel(new Union(mapping.getSchema(), mapping.getData()));

        // add new class
        OntClass additional = dynamic.createOntEntity(OntClass.class, inMemory.expandPrefix("iswc:OneMoreClass"));
        additional.addAnnotation(inMemory.getRDFSLabel(), "OneMoreClass");

        Assert.assertEquals(14, dynamic.listClasses().peek(x -> LOGGER.debug("1) DYNAMIC CLASS: {}", x)).count());

        Assert.assertEquals(totalNumberOfStatements + 2, dynamic.statements().count());

        // remove new class
        mapping.asModel().removeAll(additional, null, null);
        Assert.assertEquals(13, dynamic.listClasses().peek(x -> LOGGER.debug("2) DYNAMIC CLASS: {}", x)).count());

        mapping.getConfiguration().setServeVocabulary(true);
        Assert.assertTrue(mapping.getConfiguration().getServeVocabulary());
        Assert.assertEquals(compiledTriples, mapping.compiledPropertyBridges().size());

        Assert.assertEquals(totalNumberOfStatements, mapping.getDataModel().listStatements().toList().size());
    }

    @Test
    public void testValidatePredefinedISWCSchema() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        OntGraphModel schema = OntModelFactory.createModel(mapping.getSchema());
        D2RQTestHelper.print(schema);
        commonValidatePredefinedSchema(schema);

        Assert.assertEquals(7, schema.listClasses().peek(x -> LOGGER.debug("CLASS: {}", x)).count());

        mapping.getConfiguration().setControlOWL(true);
        // require db connection:
        mapping.compiledPropertyBridges();

        D2RQTestHelper.print(schema);
        commonValidatePredefinedSchema(schema);

        Assert.assertEquals(8, schema.listClasses().peek(x -> LOGGER.debug("CLASS: {}", x)).count());
        schema.createOntEntity(OntClass.class, "OneMore");

        Assert.assertEquals(9, schema.listClasses().count());
        Assert.assertEquals(1, mapping.asModel().listStatements(null, RDF.type, OWL.Class).toSet().size());

        Assert.assertEquals(8, schema.listObjectProperties().peek(p -> LOGGER.debug("{}", p)).count());
        Assert.assertEquals(13, schema.listDataProperties().peek(p -> LOGGER.debug("{}", p)).count());
        Assert.assertEquals(4, schema.listAnnotationProperties().peek(p -> LOGGER.debug("{}", p)).count());

        schema.ontObjects(OntPE.class).peek(p -> LOGGER.debug("Test: {}", p))
                .forEach(p -> Assert.assertTrue(p.domain().count() >= 1));
        schema.ontObjects(OntDOP.class).forEach(p -> Assert.assertTrue(p.range().count() >= 1));
    }

    @Test
    public void testValidateDefaultISWCSchema() {
        // connection:
        Mapping mapping = ConnectionData.MYSQL.toDocumentSource("iswc").getMapping();

        MappingHelper.print(mapping);


        OntGraphModel oldWay = OntModelFactory.createModel(MappingTransform.getModelBuilder().build(mapping).getGraph());

        OntGraphModel schema = OntModelFactory.createModel(mapping.getSchema());

        LOGGER.debug("Old: {}, New: {}", oldWay.size(), schema.size());
        D2RQTestHelper.print(schema);
        Assert.assertEquals(oldWay.listClasses().count(), schema.listClasses().count());
        Assert.assertEquals(oldWay.listObjectProperties().count(), schema.listObjectProperties().count());
        Assert.assertEquals(oldWay.listDataProperties().count(), schema.listDataProperties().count());

    }

    private static void commonValidatePredefinedSchema(OntGraphModel m) {
        Resource xstring = XSD.xstring;
        Resource qYear = XSD.gYear;

        OntClass iswcInProceedingClass = findEntity(m, OntClass.class, "iswc:InProceedings");
        findEntity(m, OntClass.class, "foaf:Document");
        findEntity(m, OntClass.class, "iswc:Event");
        OntClass skosConceptClass = findEntity(m, OntClass.class, "skos:Concept");
        OntClass foafPersonClass = findEntity(m, OntClass.class, "foaf:Person");
        OntClass iswcConferenceClass = findEntity(m, OntClass.class, "iswc:Conference");
        OntClass iswcOrganizationClass = findEntity(m, OntClass.class, "iswc:Organization");

        OntNDP dcAbstract = findEntity(m, OntNDP.class, "dcterms:abstract");
        checkHasDomains(dcAbstract, iswcInProceedingClass);
        checkHasRanges(dcAbstract, xstring);

        OntNDP dcTitle = findEntity(m, OntNDP.class, "dc:title");
        checkHasRanges(dcTitle, xstring);
        checkHasDomains(dcTitle, iswcInProceedingClass);

        OntNOP dcCreator = findEntity(m, OntNOP.class, "dc:creator");
        checkHasDomains(dcCreator, iswcInProceedingClass);
        checkHasRanges(dcCreator, foafPersonClass);

        OntNDP dcDate = findEntity(m, OntNDP.class, "dc:date");
        checkHasDomains(dcDate, iswcConferenceClass, iswcInProceedingClass);
        checkHasRanges(dcDate, qYear, xstring);

        OntNOP skosSubject = findEntity(m, OntNOP.class, "skos:subject");
        checkHasDomains(skosSubject, iswcInProceedingClass);
        checkHasRanges(skosSubject, skosConceptClass);

        OntNOP skosBroader = findEntity(m, OntNOP.class, "skos:broader");
        checkHasRanges(skosBroader, skosConceptClass);
        checkHasDomains(skosBroader, skosConceptClass);

        OntNOP skosPrimarySubject = findEntity(m, OntNOP.class, "skos:primarySubject");
        checkHasDomains(skosPrimarySubject, iswcInProceedingClass);
        checkHasRanges(skosPrimarySubject, skosConceptClass);

        OntNDP skosPrefLabel = findEntity(m, OntNDP.class, "skos:prefLabel");
        checkHasDomains(skosPrefLabel, skosConceptClass);
        checkHasRanges(skosPrefLabel, xstring);

        OntNAP foafMbox = findEntity(m, OntNAP.class, "foaf:mbox");
        checkHasRanges(foafMbox);
        checkHasDomains(foafMbox, foafPersonClass);

        OntNDP foafName = findEntity(m, OntNDP.class, "foaf:name");
        checkHasDomains(foafName, foafPersonClass);
        checkHasRanges(foafName, xstring);

        OntNAP foafHomepage = findEntity(m, OntNAP.class, "foaf:homepage");
        checkHasDomains(foafHomepage, foafPersonClass, iswcOrganizationClass);
        checkHasRanges(foafHomepage);

        OntNAP foafDepiction = findEntity(m, OntNAP.class, "foaf:depiction");
        checkHasRanges(foafDepiction);
        checkHasDomains(foafDepiction, foafPersonClass);

        OntNOP iswcResearchInterests = findEntity(m, OntNOP.class, "iswc:research_interests");
        checkHasDomains(iswcResearchInterests, foafPersonClass);
        checkHasRanges(iswcResearchInterests, skosConceptClass);

        OntNOP iswcConference = findEntity(m, OntNOP.class, "iswc:conference");
        checkHasRanges(iswcConference, iswcConferenceClass);
        checkHasDomains(iswcConference, iswcInProceedingClass);

        OntNDP iswcLocation = findEntity(m, OntNDP.class, "iswc:location");
        checkHasDomains(iswcLocation, iswcConferenceClass);
        checkHasRanges(iswcLocation, xstring);

        Assert.assertFalse(m.contains(RDFS.label, RDF.type, (RDFNode) null));
    }

    private static <X extends OntEntity> X findEntity(OntGraphModel m, Class<X> type, String shortForm) {
        X res = m.getOntEntity(type, m.expandPrefix(shortForm));
        Assert.assertNotNull("Can't find " + type.getSimpleName() + " " + shortForm, res);
        return res;
    }

    private static void checkHasRanges(OntPE p, Resource... ranges) {
        checkHas(p, OntPE::range, ranges);
    }

    private static void checkHasDomains(OntPE p, Resource... domains) {
        checkHas(p, OntPE::domain, domains);
    }

    private static void checkHas(OntPE p, Function<OntPE, Stream<? extends Resource>> get, Resource... domains) {
        Assert.assertEquals(domains.length, get.apply(p).count());
        for (Resource c : domains) {
            get.apply(p).filter(c::equals).findFirst().orElseThrow(() -> new AssertionError("Property " + p));
        }
    }

}
