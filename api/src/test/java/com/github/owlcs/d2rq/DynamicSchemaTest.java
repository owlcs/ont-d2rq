package com.github.owlcs.d2rq;

import com.github.owlcs.d2rq.conf.ConnectionData;
import com.github.owlcs.d2rq.conf.ISWCData;
import com.github.owlcs.d2rq.utils.OWLUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.*;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import com.github.owlcs.ontapi.jena.vocabulary.RDF;
import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.utils.MappingUtils;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @ssz on 30.09.2018.
 */
public class DynamicSchemaTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSchemaTest.class);

    @Test
    public void testCreateSchema() {
        String uri = "http://empty";
        String ns = uri + "#";

        Mapping mapping = MappingFactory.create();
        OntModel m = OntModelFactory.createModel(mapping.getSchema());
        m.setNsPrefix("test", ns);
        Assert.assertEquals("test", m.getNsURIPrefix(ns));
        Assert.assertEquals("test", mapping.asModel().getNsURIPrefix(ns));

        Assert.assertTrue(m.isEmpty());
        m.setID(uri);
        Assert.assertFalse(m.isEmpty());
        Assert.assertEquals(uri, m.getID().getURI());
        m.createOntClass(ns + "Clazz");
        Assert.assertEquals(1, m.classes().count());
        Assert.assertEquals(2, m.size());
        Assert.assertFalse(m.isEmpty());
        try {
            mapping.validate();
            Assert.fail("No validation error");
        } catch (D2RQException e) {
            LOGGER.debug("Expected: '{}'", e.getMessage());
        }
        try {
            mapping.getDataModel().listStatements().forEachRemaining(x -> LOGGER.error("Iter:::{}", x));
            Assert.fail("No validation error while iterating over data");
        } catch (D2RQException e) {
            LOGGER.debug("Expected: '{}'", e.getMessage());
        }
        JenaModelUtils.print(m);
    }

    @Test
    public void testInsertOntologyID() {
        String uri1 = "http://test.x";
        String uri2 = "http://test.y";
        String ver = "http://test.y/ver1";
        String comment = "xxxx";
        Mapping mapping = ISWCData.MYSQL.loadMapping();

        Graph g = mapping.getSchema();

        OntModel schema = OntModelFactory.createModel(g);
        schema.setID(uri1).addComment("xxxx");
        Assert.assertTrue(schema.contains(null, RDFS.comment, comment));
        Assert.assertTrue(schema.contains(schema.getResource(uri1), RDFS.comment, comment));

        Assert.assertEquals(uri1, schema.getID().getURI());
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("1) Schema annotation: {}", s)).count());

        JenaModelUtils.print(schema);
        Assert.assertTrue(schema.contains(null, RDFS.comment, comment));

        OntModel mappingAsOWL = OntModelFactory.createModel(mapping.asModel().getGraph());
        Assert.assertEquals(1, mappingAsOWL.getID()
                .annotations().peek(s -> LOGGER.debug("Mapping annotation: {}", s)).count());
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("2) Schema annotation: {}", PrettyPrinter.toString(s))).count());
        Assert.assertEquals(uri1, schema.getID().getURI());
        Assert.assertEquals(uri1, mappingAsOWL.getID().getURI());

        // change iri, set version iri
        Assert.assertTrue(schema.contains(null, RDFS.comment, comment));
        schema.setID(uri2).setVersionIRI(ver);
        Assert.assertEquals(2, schema.getID().annotations()
                .peek(s -> LOGGER.debug("3) Schema annotation: {}", s)).count());
        Assert.assertEquals(uri2, schema.getID().getURI());
        Assert.assertEquals(uri2, mappingAsOWL.getID().getURI());
        Assert.assertEquals(ver, schema.getID().getVersionIRI());
        Assert.assertEquals(ver, mappingAsOWL.getID().getVersionIRI());
        Assert.assertFalse(schema.contains(schema.getResource(uri1), RDFS.comment, comment));
        Assert.assertTrue(schema.contains(schema.getResource(uri2), RDFS.comment, comment));

        // reload mapping
        String res = JenaModelUtils.toTurtleString(mapping.asModel());
        Model m = JenaModelUtils.loadFromString(res);
        Assert.assertEquals(uri2, OntModelFactory.createModel(m.getGraph()).getID().getURI());
        OntModel schema2 = OntModelFactory.createModel(MappingFactory.wrap(m).getSchema());
        Assert.assertEquals(2, schema2.getID().annotations()
                .peek(s -> LOGGER.debug("4) Schema annotation: {}", s)).count());
        Assert.assertEquals(uri2, schema2.getID().getURI());
        Assert.assertEquals(ver, schema2.getID().getVersionIRI());
        OntStatement commentStatement = schema2.getID().annotations()
                .filter(s -> RDFS.comment.equals(s.getPredicate())).findFirst().orElseThrow(AssertionError::new);
        Assert.assertEquals(comment, commentStatement.getString());
    }

    @Test
    public void testPrefixes() {
        Mapping mapping = ISWCData.MYSQL.loadMapping();
        PrefixMapping pm = mapping.getSchema().getPrefixMapping();
        pm.getNsPrefixMap().forEach((p, u) -> LOGGER.debug("Schema {} => {}", p, u));
        mapping.asModel().getNsPrefixMap().forEach((p, u) -> LOGGER.debug("Mapping {} => {}", p, u));
        //Assert.assertNull(pm.getNsPrefixURI(MappingFactory.MAP_PREFIX));
        Assert.assertNull(pm.getNsPrefixURI(MappingFactory.D2RQ_PREFIX));
        Assert.assertNull(pm.getNsPrefixURI(MappingFactory.JDBC_PREFIX));
        Assert.assertEquals(12, mapping.getVocabularyModel().numPrefixes());
        Assert.assertEquals(14, mapping.asModel().numPrefixes());

        String p = "test";
        String u = "http://test.com#";
        mapping.getVocabularyModel().setNsPrefix(p, u);
        Assert.assertEquals(u, mapping.asModel().getNsPrefixURI(p));
        Assert.assertEquals(p, mapping.getVocabularyModel().getNsURIPrefix(u));
        Assert.assertEquals(13, mapping.getVocabularyModel().numPrefixes());
        Assert.assertEquals(15, mapping.asModel().numPrefixes());

        mapping.asModel().removeNsPrefix(p);
        //Assert.assertEquals(11, pm.numPrefixes());
        Assert.assertEquals(14, mapping.asModel().numPrefixes());
        Assert.assertEquals(12, mapping.getSchema().getPrefixMapping().numPrefixes());
    }

    @Test
    public void testValidatePredefinedISWCSchema() {
        Mapping mapping = ISWCData.MYSQL.loadMapping();

        OntModel schema = OntModelFactory.createModel(mapping.getSchema());
        JenaModelUtils.print(schema);
        Assert.assertFalse(mapping.getConfiguration().getControlOWL());
        validateInferredOWLForPredefinedMapping(schema);

        Assert.assertEquals(7, schema.classes().peek(x -> LOGGER.debug("1) CLASS: {}", x)).count());

        mapping.getConfiguration().setControlOWL(true);
        Assert.assertTrue(mapping.getConfiguration().getControlOWL());

        JenaModelUtils.print(schema);
        validateInferredOWLForPredefinedMapping(schema);
        Assert.assertEquals(8, schema.classes().peek(x -> LOGGER.debug("2) CLASS: {}", x)).count());
        schema.createOntClass("OneMore");

        Assert.assertEquals(9, schema.classes().count());
        Assert.assertEquals(1, mapping.asModel().listStatements(null, RDF.type, OWL.Class).toSet().size());

        Assert.assertEquals(8, schema.objectProperties().peek(p -> LOGGER.debug("{}", p)).count());
        Assert.assertEquals(13, schema.dataProperties().peek(p -> LOGGER.debug("{}", p)).count());
        Assert.assertEquals(4, schema.annotationProperties().peek(p -> LOGGER.debug("{}", p)).count());

        schema.ontObjects(OntProperty.class).peek(p -> LOGGER.debug("Test: {}", p))
                .forEach(p -> Assert.assertTrue(p.domains().count() >= 1));
        schema.ontObjects(OntRealProperty.class).forEach(p -> Assert.assertTrue(p.ranges().count() >= 1));

        mapping.close();
    }

    @Test
    public void testValidateDefaultISWCSchema() {
        // connection:
        try (Mapping mapping = ConnectionData.MYSQL.toDocumentSource("iswc").getMapping()) {

            MappingUtils.print(mapping);

            OntModel schema = OntModelFactory.createModel(mapping.getSchema());

            JenaModelUtils.print(schema);
            Assert.assertEquals(6, schema.classes().peek(x -> LOGGER.debug("Class {}", x)).count());
            Assert.assertEquals(8, schema.objectProperties().peek(x -> LOGGER.debug("Object property {}", x)).count());
            Assert.assertEquals(33, schema.dataProperties().peek(x -> LOGGER.debug("Datatype property {}", x)).count());
            Assert.assertEquals(47, schema.ontEntities().peek(e -> {
                LOGGER.debug("Entity: {}", e);
                Assert.assertTrue(e.annotations().count() > 0);
            }).count());

            Assert.assertEquals(179, schema.statements().count());
        }
    }

    static void validateInferredOWLForPredefinedMapping(OntModel m) {
        Resource xstring = XSD.xstring;
        Resource qYear = XSD.gYear;

        OntClass iswcInProceedingClass = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:InProceedings");
        OntClass iswcEventClass = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Event");
        OntClass iswcConferenceClass = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Conference");
        OntClass iswcOrganizationClass = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Organization");
        OntClass foafDocumentClass = OWLUtils.findEntity(m, OntClass.Named.class, "foaf:Document");
        OntClass foafPersonClass = OWLUtils.findEntity(m, OntClass.Named.class, "foaf:Person");
        OntClass skosConceptClass = OWLUtils.findEntity(m, OntClass.Named.class, "skos:Concept");

        OntDataProperty dcAbstract = OWLUtils.findEntity(m, OntDataProperty.class, "dcterms:abstract");
        checkHasDomains(dcAbstract, iswcInProceedingClass);
        checkHasRanges(dcAbstract, xstring);

        OntDataProperty dcTitle = OWLUtils.findEntity(m, OntDataProperty.class, "dc:title");
        checkHasRanges(dcTitle, xstring);
        checkHasDomains(dcTitle, iswcInProceedingClass);

        OntObjectProperty dcCreator = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "dc:creator");
        checkHasDomains(dcCreator, iswcInProceedingClass);
        checkHasRanges(dcCreator, foafPersonClass);

        OntDataProperty dcDate = OWLUtils.findEntity(m, OntDataProperty.class, "dc:date");
        checkHasDomains(dcDate, iswcConferenceClass, iswcInProceedingClass);
        checkHasRanges(dcDate, qYear, xstring);

        OntObjectProperty skosSubject = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "skos:subject");
        checkHasDomains(skosSubject, iswcInProceedingClass);
        checkHasRanges(skosSubject, skosConceptClass);

        OntObjectProperty skosBroader = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "skos:broader");
        checkHasRanges(skosBroader, skosConceptClass);
        checkHasDomains(skosBroader, skosConceptClass);

        OntObjectProperty skosPrimarySubject = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "skos:primarySubject");
        checkHasDomains(skosPrimarySubject, iswcInProceedingClass);
        checkHasRanges(skosPrimarySubject, skosConceptClass);

        OntDataProperty skosPrefLabel = OWLUtils.findEntity(m, OntDataProperty.class, "skos:prefLabel");
        checkHasDomains(skosPrefLabel, skosConceptClass);
        checkHasRanges(skosPrefLabel, xstring);

        OntAnnotationProperty foafMbox = OWLUtils.findEntity(m, OntAnnotationProperty.class, "foaf:mbox");
        checkHasRanges(foafMbox);
        checkHasDomains(foafMbox, foafPersonClass);

        OntDataProperty foafName = OWLUtils.findEntity(m, OntDataProperty.class, "foaf:name");
        checkHasDomains(foafName, foafPersonClass);
        checkHasRanges(foafName, xstring);

        OntAnnotationProperty foafHomepage = OWLUtils.findEntity(m, OntAnnotationProperty.class, "foaf:homepage");
        checkHasDomains(foafHomepage, foafPersonClass, iswcOrganizationClass);
        checkHasRanges(foafHomepage);

        OntAnnotationProperty foafDepiction = OWLUtils.findEntity(m, OntAnnotationProperty.class, "foaf:depiction");
        checkHasRanges(foafDepiction);
        checkHasDomains(foafDepiction, foafPersonClass);

        OntObjectProperty iswcResearchInterests = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "iswc:research_interests");
        checkHasDomains(iswcResearchInterests, foafPersonClass);
        checkHasRanges(iswcResearchInterests, skosConceptClass);

        OntObjectProperty iswcConference = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "iswc:conference");
        checkHasRanges(iswcConference, iswcConferenceClass);
        checkHasDomains(iswcConference, iswcInProceedingClass);

        OntDataProperty iswcLocation = OWLUtils.findEntity(m, OntDataProperty.class, "iswc:location");
        checkHasDomains(iswcLocation, iswcConferenceClass);
        checkHasRanges(iswcLocation, xstring);

        // skos:primarySubject owl:equivalentProperty skos:subject
        Assert.assertEquals(1, m.statements(null, OWL.equivalentProperty, null).count());
        Assert.assertTrue(skosPrimarySubject.equivalentProperties().collect(Collectors.toSet()).contains(skosSubject));

        // iswc:InProceedings owl:equivalentClass foaf:Document
        Assert.assertEquals(1, m.statements(null, OWL.equivalentClass, null).count());
        Assert.assertTrue(iswcInProceedingClass.equivalentClasses().collect(Collectors.toSet()).contains(foafDocumentClass));

        // iswc:Conference rdfs:subClassOf iswc:Event
        Assert.assertEquals(1, m.statements(null, RDFS.subClassOf, null).count());
        Assert.assertTrue(iswcConferenceClass.superClasses().collect(Collectors.toSet()).contains(iswcEventClass));

        Assert.assertFalse(m.contains(RDFS.label, RDF.type, (RDFNode) null));
    }

    static void checkIndividual(OntClass owner, int count, boolean anon) {
        PrefixMapping pm = owner.getModel();
        Assert.assertEquals(count, owner.individuals().peek(i -> {
            LOGGER.debug("{} individual: {}", pm.shortForm(owner.getURI()), i);
            Assert.assertEquals(anon, i.isAnon());
            i.classes().filter(owner::equals).findFirst().orElseThrow(AssertionError::new);
        }).count());
    }

    static void checkHasRanges(OntProperty p, Resource... ranges) {
        checkHas(p, OntProperty::ranges, ranges);
    }

    static void checkHasDomains(OntProperty p, Resource... domains) {
        checkHas(p, OntProperty::domains, domains);
    }

    private static void checkHas(OntProperty p, Function<OntProperty, Stream<? extends Resource>> get, Resource... domains) {
        Assert.assertEquals(domains.length, get.apply(p).count());
        for (Resource c : domains) {
            get.apply(p).filter(c::equals).findFirst().orElseThrow(() -> new AssertionError("Property " + p));
        }
    }

}
