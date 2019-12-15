package com.github.owlcs.d2rq;

import com.github.owlcs.d2rq.conf.ISWCData;
import com.github.owlcs.d2rq.utils.OWLUtils;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.*;
import com.github.owlcs.ontapi.jena.vocabulary.OWL;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingHelper;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.XSD;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validate ISWC mappings data.
 * Created by @szz on 18.10.2018.
 */
@RunWith(Parameterized.class)
public class ISWCModelDataTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ISWCModelDataTest.class);
    private final ISWCData data;

    public ISWCModelDataTest(ISWCData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ISWCData[] getData() {
        return ISWCData.values();
    }

    private static void validateMappedOWLDataForPredefinedMapping(OntModel m) {
        Resource xstring = XSD.xstring;
        OntClass iswcFull_Professor = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Full_Professor");
        OntClass iswcDepartment = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Department");
        OntClass iswcInstitute = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Institute");
        OntClass iswcUniversity = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:University");
        OntClass iswcResearcher = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Researcher");
        OntClass postalAddresses = m.classes().filter(x -> "PostalAddresses".equalsIgnoreCase(x.getLocalName()))
                .findFirst().orElseThrow(AssertionError::new);
        OntClass iswcOrganizationClass = OWLUtils.findEntity(m, OntClass.Named.class, "iswc:Organization");

        DynamicSchemaTest.checkIndividual(iswcResearcher, 5, false);
        DynamicSchemaTest.checkIndividual(iswcInstitute, 2, false);
        DynamicSchemaTest.checkIndividual(iswcUniversity, 3, false);
        DynamicSchemaTest.checkIndividual(iswcDepartment, 2, false);
        DynamicSchemaTest.checkIndividual(iswcFull_Professor, 2, false);
        DynamicSchemaTest.checkIndividual(postalAddresses, 9, true);

        OntObjectProperty vcardADR = OWLUtils.findEntity(m, OntObjectProperty.Named.class, "vcard:ADR");
        OntDataProperty vcardPcode = OWLUtils.findEntity(m, OntDataProperty.class, "vcard:Pcode");
        OntDataProperty vcardCountry = OWLUtils.findEntity(m, OntDataProperty.class, "vcard:Country");
        OntDataProperty vcardLocality = OWLUtils.findEntity(m, OntDataProperty.class, "vcard:Locality");
        OntDataProperty vcardStreet = OWLUtils.findEntity(m, OntDataProperty.class, "vcard:Street");

        DynamicSchemaTest.checkHasDomains(vcardADR, iswcOrganizationClass);
        DynamicSchemaTest.checkHasRanges(vcardADR, postalAddresses);

        DynamicSchemaTest.checkHasRanges(vcardPcode, xstring);
        DynamicSchemaTest.checkHasRanges(vcardCountry, xstring);
        DynamicSchemaTest.checkHasRanges(vcardLocality, xstring);
        DynamicSchemaTest.checkHasRanges(vcardStreet, xstring);

        DynamicSchemaTest.checkHasDomains(vcardPcode, postalAddresses);
        DynamicSchemaTest.checkHasDomains(vcardCountry, postalAddresses);
        DynamicSchemaTest.checkHasDomains(vcardLocality, postalAddresses);
        DynamicSchemaTest.checkHasDomains(vcardStreet, postalAddresses);
    }

    @Test
    public void testValidateSchemaAndDataWithControlOWL() {
        int totalNumberOfStatements = 401;
        Mapping mapping = data.loadMapping();

        mapping.getConfiguration().setServeVocabulary(false).setControlOWL(true);

        Assert.assertFalse(mapping.getConfiguration().getServeVocabulary());
        Assert.assertTrue(mapping.getConfiguration().getControlOWL());

        Model data = mapping.getDataModel();

        int compiledTriples = MappingHelper.asConnectingMapping(mapping).compiledPropertyBridges().size();

        OntModel inMemory = OntModelFactory.createModel();
        inMemory.add(mapping.getVocabularyModel());
        // + PostalAddresses:
        Assert.assertEquals(8, inMemory.classes().peek(x -> LOGGER.debug("Schema: {}", x)).count());
        inMemory.add(data);
        // todo:
        inMemory.write(System.err, "ttl");
        System.out.println("----");
        mapping.asModel().write(System.err, "ttl");
        System.out.println("----");

        DynamicSchemaTest.validateInferredOWLForPredefinedMapping(inMemory);
        Assert.assertEquals(13, inMemory.classes().peek(x -> LOGGER.debug("Schema+Data: {}", x)).count());
        JenaModelUtils.print(inMemory);
        DynamicSchemaTest.validateInferredOWLForPredefinedMapping(inMemory);
        validateMappedOWLDataForPredefinedMapping(inMemory);

        Assert.assertEquals(totalNumberOfStatements, inMemory.size());
        mapping.getConfiguration().setServeVocabulary(true);

        OntModel dynamic = OntModelFactory.createModel(new Union(mapping.getSchema(), mapping.getData()));

        DynamicSchemaTest.validateInferredOWLForPredefinedMapping(dynamic);
        validateMappedOWLDataForPredefinedMapping(dynamic);

        // add new class
        OntClass additional = dynamic.createOntClass(inMemory.expandPrefix("iswc:OneMoreClass"));
        additional.addAnnotation(inMemory.getRDFSLabel(), "OneMoreClass");

        Assert.assertEquals(14, dynamic.classes().peek(x -> LOGGER.debug("1) DYNAMIC CLASS: {}", x)).count());

        Assert.assertEquals(totalNumberOfStatements + 2, dynamic.statements().count());

        // remove new class
        mapping.asModel().removeAll(additional, null, null);
        Assert.assertEquals(13, dynamic.classes().peek(x -> LOGGER.debug("2) DYNAMIC CLASS: {}", x)).count());

        mapping.getConfiguration().setServeVocabulary(true);
        Assert.assertTrue(mapping.getConfiguration().getServeVocabulary());
        Assert.assertEquals(compiledTriples, MappingHelper.asConnectingMapping(mapping).compiledPropertyBridges().size());

        Assert.assertEquals(totalNumberOfStatements, mapping.getDataModel().listStatements().toList().size());

        mapping.close();
    }

    @Test
    public void testValidateDataWithoutControlOWL() {
        try (Mapping mapping = data.loadMapping()) {
            OntModel m = OntModelFactory.createModel(mapping.getData());
            Assert.assertEquals(0, m.namedIndividuals().count());
            LOGGER.debug("Data:"); // starting ont-api:1.4.2 no duplicates in the result:
            List<OntIndividual> individuals = m.individuals()
                    .peek(x -> LOGGER.debug("INDIVIDUAL: {}", x)).collect(Collectors.toList());
            LOGGER.debug("Get: {}", individuals.size());
            Assert.assertEquals(42, individuals.size());
            String txt = JenaModelUtils.toTurtleString(m);
            LOGGER.debug("Model:\n{}", txt);
            Assert.assertFalse(txt.contains(m.shortForm(OWL.NamedIndividual.getURI())));
        }
    }

    @Test
    public void testValidateDataWithNamedIndividuals() {
        try (Mapping mapping = data.loadMapping()) {
            mapping.getConfiguration().setGenerateNamedIndividuals(true).setControlOWL(true);
            Assert.assertTrue(mapping.getConfiguration().getControlOWL());
            Assert.assertTrue(mapping.getConfiguration().getGenerateNamedIndividuals());


            OntModel res = OntModelFactory.createModel(mapping.getData());
            // TODO: there is a bug with intersections of
            //  d2rq:uriPattern=http://annotation.semanticweb.org/iswc/iswc.daml#@@persons.Type@@ and
            //  d2rq:uriColumn=topics.URI, which retrieves uris with the same namespace.
            //  It is not possible to find anything by the pattern 'iswc:e-Business ANY ANY',
            //  although this triple is present in the full set 'ANY ANY ANY'.
            //  So, a Graph becomes inconsistent.
            //  As a result - no named individuals are generated for the owl:sameAs rule.
            //  Right now don't know what to deal with it.
            //  See https://github.com/avicomp/ont-d2rq/issues/21

            Assert.assertEquals(42, res.namedIndividuals().peek(x -> LOGGER.debug("NAMED INDIVIDUAL: {}", x)).count());
            Assert.assertEquals(51, res.individuals()
                    .peek(x -> LOGGER.debug("INDIVIDUAL: {}", x))
                    .distinct().count());
        }
    }

}
