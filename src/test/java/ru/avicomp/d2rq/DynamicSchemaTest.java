package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.compose.Union;
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
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @ssz on 30.09.2018.
 */
public class DynamicSchemaTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicSchemaTest.class);

    @Test
    public void testInsertOntologyID() {
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
    public void testPrefixes() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");
        PrefixMapping pm = mapping.getSchema().getPrefixMapping();
        pm.getNsPrefixMap().forEach((p, u) -> LOGGER.debug("Schema {} => {}", p, u));
        mapping.asModel().getNsPrefixMap().forEach((p, u) -> LOGGER.debug("Mapping {} => {}", p, u));
        Assert.assertNull(pm.getNsPrefixURI(MappingFactory.MAP_PREFIX));
        Assert.assertNull(pm.getNsPrefixURI(MappingFactory.D2RQ_PREFIX));
        Assert.assertNull(pm.getNsPrefixURI(MappingFactory.JDBC_PREFIX));
        Assert.assertEquals(11, mapping.getVocabularyModel().numPrefixes());
        Assert.assertEquals(14, mapping.asModel().numPrefixes());

        String p = "test";
        String u = "http://test.com#";
        mapping.getVocabularyModel().setNsPrefix(p, u);
        Assert.assertEquals(u, mapping.asModel().getNsPrefixURI(p));
        Assert.assertEquals(p, mapping.getVocabularyModel().getNsURIPrefix(u));
        Assert.assertEquals(12, mapping.getVocabularyModel().numPrefixes());
        Assert.assertEquals(15, mapping.asModel().numPrefixes());

        mapping.asModel().removeNsPrefix(p);
        //Assert.assertEquals(11, pm.numPrefixes());
        Assert.assertEquals(14, mapping.asModel().numPrefixes());
        Assert.assertEquals(11, mapping.getSchema().getPrefixMapping().numPrefixes());
    }

    @Test
    public void testConnectedPredefinedISWCSchemaAndData() {
        int totalNumberOfStatements = 443;
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        mapping.getConfiguration().setServeVocabulary(false).setControlOWL(true);

        Assert.assertFalse(mapping.getConfiguration().getServeVocabulary());
        Assert.assertTrue(mapping.getConfiguration().getControlOWL());

        Model data = mapping.getDataModel();

        int compiledTriples = mapping.compiledPropertyBridges().size();

        OntGraphModel inMemory = OntModelFactory.createModel();
        inMemory.add(mapping.getVocabularyModel());
        // + PostalAddresses:
        Assert.assertEquals(8, inMemory.listClasses().peek(x -> LOGGER.debug("Schema: {}", x)).count());
        inMemory.add(data);
        validateInferredOWLForPredefinedMapping(inMemory);
        Assert.assertEquals(13, inMemory.listClasses().peek(x -> LOGGER.debug("Schema+Data: {}", x)).count());
        D2RQTestHelper.print(inMemory);
        validateInferredOWLForPredefinedMapping(inMemory);
        validateMappedOWLDataForPredefinedMapping(inMemory);


        Assert.assertEquals(totalNumberOfStatements, inMemory.size());
        mapping.getConfiguration().setServeVocabulary(true);

        OntGraphModel dynamic = OntModelFactory.createModel(new Union(mapping.getSchema(), mapping.getData()));

        validateInferredOWLForPredefinedMapping(dynamic);
        validateMappedOWLDataForPredefinedMapping(dynamic);

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

        mapping.close();
    }

    @Test
    public void testValidatePredefinedISWCSchema() {
        Mapping mapping = MappingFactory.load("/mapping-iswc.mysql.ttl");

        OntGraphModel schema = OntModelFactory.createModel(mapping.getSchema());
        D2RQTestHelper.print(schema);
        validateInferredOWLForPredefinedMapping(schema);

        Assert.assertEquals(7, schema.listClasses().peek(x -> LOGGER.debug("CLASS: {}", x)).count());

        mapping.getConfiguration().setControlOWL(true);
        // require db connection:
        mapping.compiledPropertyBridges();

        D2RQTestHelper.print(schema);
        validateInferredOWLForPredefinedMapping(schema);
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

        mapping.close();
    }

    @Test
    public void testValidateDefaultISWCSchema() {
        // connection:
        try (Mapping mapping = ConnectionData.MYSQL.toDocumentSource("iswc").getMapping()) {

            MappingHelper.print(mapping);

            OntGraphModel schema = OntModelFactory.createModel(mapping.getSchema());

            D2RQTestHelper.print(schema);
            Assert.assertEquals(6, schema.listClasses().peek(x -> LOGGER.debug("Class {}", x)).count());
            Assert.assertEquals(8, schema.listObjectProperties().peek(x -> LOGGER.debug("Object property {}", x)).count());
            Assert.assertEquals(33, schema.listDataProperties().peek(x -> LOGGER.debug("Datatype property {}", x)).count());
            Assert.assertEquals(47, schema.ontEntities().peek(e -> {
                LOGGER.debug("Entity: {}", e);
                Assert.assertTrue(e.annotations().count() > 0);
            }).count());

            Assert.assertEquals(179, schema.statements().count());
        }
    }

    private static void validateInferredOWLForPredefinedMapping(OntGraphModel m) {
        Resource xstring = XSD.xstring;
        Resource qYear = XSD.gYear;

        OntClass iswcInProceedingClass = findEntity(m, OntClass.class, "iswc:InProceedings");
        OntClass iswcEventClass = findEntity(m, OntClass.class, "iswc:Event");
        OntClass iswcConferenceClass = findEntity(m, OntClass.class, "iswc:Conference");
        OntClass iswcOrganizationClass = findEntity(m, OntClass.class, "iswc:Organization");
        OntClass foafDocumentClass = findEntity(m, OntClass.class, "foaf:Document");
        OntClass foafPersonClass = findEntity(m, OntClass.class, "foaf:Person");
        OntClass skosConceptClass = findEntity(m, OntClass.class, "skos:Concept");

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

        // skos:primarySubject owl:equivalentProperty skos:subject
        Assert.assertEquals(1, m.statements(null, OWL.equivalentProperty, null).count());
        Assert.assertTrue(skosPrimarySubject.equivalentProperty().collect(Collectors.toSet()).contains(skosSubject));

        // iswc:InProceedings owl:equivalentClass foaf:Document
        Assert.assertEquals(1, m.statements(null, OWL.equivalentClass, null).count());
        Assert.assertTrue(iswcInProceedingClass.equivalentClass().collect(Collectors.toSet()).contains(foafDocumentClass));

        // iswc:Conference rdfs:subClassOf iswc:Event
        Assert.assertEquals(1, m.statements(null, RDFS.subClassOf, null).count());
        Assert.assertTrue(iswcConferenceClass.subClassOf().collect(Collectors.toSet()).contains(iswcEventClass));

        Assert.assertFalse(m.contains(RDFS.label, RDF.type, (RDFNode) null));
    }

    private static void validateMappedOWLDataForPredefinedMapping(OntGraphModel m) {
        Resource xstring = XSD.xstring;
        OntClass iswcFull_Professor = findEntity(m, OntClass.class, "iswc:Full_Professor");
        OntClass iswcDepartment = findEntity(m, OntClass.class, "iswc:Department");
        OntClass iswcInstitute = findEntity(m, OntClass.class, "iswc:Institute");
        OntClass iswcUniversity = findEntity(m, OntClass.class, "iswc:University");
        OntClass iswcResearcher = findEntity(m, OntClass.class, "iswc:Researcher");
        OntClass postalAddresses = m.listClasses().filter(x -> "PostalAddresses".equals(x.getLocalName()))
                .findFirst().orElseThrow(AssertionError::new);
        OntClass iswcOrganizationClass = findEntity(m, OntClass.class, "iswc:Organization");

        checkIndividual(iswcResearcher, 5, false);
        checkIndividual(iswcInstitute, 2, false);
        checkIndividual(iswcUniversity, 3, false);
        checkIndividual(iswcDepartment, 2, false);
        checkIndividual(iswcFull_Professor, 2, false);
        checkIndividual(postalAddresses, 9, true);

        OntNOP vcardADR = findEntity(m, OntNOP.class, "vcard:ADR");
        OntNDP vcardPcode = findEntity(m, OntNDP.class, "vcard:Pcode");
        OntNDP vcardCountry = findEntity(m, OntNDP.class, "vcard:Country");
        OntNDP vcardLocality = findEntity(m, OntNDP.class, "vcard:Locality");
        OntNDP vcardStreet = findEntity(m, OntNDP.class, "vcard:Street");

        checkHasDomains(vcardADR, iswcOrganizationClass);
        checkHasRanges(vcardADR, postalAddresses);

        checkHasRanges(vcardPcode, xstring);
        checkHasRanges(vcardCountry, xstring);
        checkHasRanges(vcardLocality, xstring);
        checkHasRanges(vcardStreet, xstring);

        checkHasDomains(vcardPcode, postalAddresses);
        checkHasDomains(vcardCountry, postalAddresses);
        checkHasDomains(vcardLocality, postalAddresses);
        checkHasDomains(vcardStreet, postalAddresses);
        // todo:
    }

    private static void checkIndividual(OntClass owner, int count, boolean anon) {
        PrefixMapping pm = owner.getModel();
        Assert.assertEquals(count, owner.individuals().peek(i -> {
            LOGGER.debug("{} individual: {}", pm.shortForm(owner.getURI()), i);
            Assert.assertEquals(anon, i.isAnon());
            i.classes().filter(owner::equals).findFirst().orElseThrow(AssertionError::new);
        }).count());
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
