package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingHelper;
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
import ru.avicomp.d2rq.conf.ISWCData;
import ru.avicomp.d2rq.utils.OWLUtils;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.model.OntNOP;

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

    private static void validateMappedOWLDataForPredefinedMapping(OntGraphModel m) {
        Resource xstring = XSD.xstring;
        OntClass iswcFull_Professor = OWLUtils.findEntity(m, OntClass.class, "iswc:Full_Professor");
        OntClass iswcDepartment = OWLUtils.findEntity(m, OntClass.class, "iswc:Department");
        OntClass iswcInstitute = OWLUtils.findEntity(m, OntClass.class, "iswc:Institute");
        OntClass iswcUniversity = OWLUtils.findEntity(m, OntClass.class, "iswc:University");
        OntClass iswcResearcher = OWLUtils.findEntity(m, OntClass.class, "iswc:Researcher");
        OntClass postalAddresses = m.listClasses().filter(x -> "PostalAddresses".equalsIgnoreCase(x.getLocalName()))
                .findFirst().orElseThrow(AssertionError::new);
        OntClass iswcOrganizationClass = OWLUtils.findEntity(m, OntClass.class, "iswc:Organization");

        DynamicSchemaTest.checkIndividual(iswcResearcher, 5, false);
        DynamicSchemaTest.checkIndividual(iswcInstitute, 2, false);
        DynamicSchemaTest.checkIndividual(iswcUniversity, 3, false);
        DynamicSchemaTest.checkIndividual(iswcDepartment, 2, false);
        DynamicSchemaTest.checkIndividual(iswcFull_Professor, 2, false);
        DynamicSchemaTest.checkIndividual(postalAddresses, 9, true);

        OntNOP vcardADR = OWLUtils.findEntity(m, OntNOP.class, "vcard:ADR");
        OntNDP vcardPcode = OWLUtils.findEntity(m, OntNDP.class, "vcard:Pcode");
        OntNDP vcardCountry = OWLUtils.findEntity(m, OntNDP.class, "vcard:Country");
        OntNDP vcardLocality = OWLUtils.findEntity(m, OntNDP.class, "vcard:Locality");
        OntNDP vcardStreet = OWLUtils.findEntity(m, OntNDP.class, "vcard:Street");

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
    public void testValidateSchemaAndData() {
        int totalNumberOfStatements = 443;
        Mapping mapping = data.loadMapping();

        mapping.getConfiguration().setServeVocabulary(false).setControlOWL(true);

        Assert.assertFalse(mapping.getConfiguration().getServeVocabulary());
        Assert.assertTrue(mapping.getConfiguration().getControlOWL());

        Model data = mapping.getDataModel();

        int compiledTriples = MappingHelper.asConnectingMapping(mapping).compiledPropertyBridges().size();

        OntGraphModel inMemory = OntModelFactory.createModel();
        inMemory.add(mapping.getVocabularyModel());
        // + PostalAddresses:
        Assert.assertEquals(8, inMemory.listClasses().peek(x -> LOGGER.debug("Schema: {}", x)).count());
        inMemory.add(data);
        DynamicSchemaTest.validateInferredOWLForPredefinedMapping(inMemory);
        Assert.assertEquals(13, inMemory.listClasses().peek(x -> LOGGER.debug("Schema+Data: {}", x)).count());
        D2RQTestHelper.print(inMemory);
        DynamicSchemaTest.validateInferredOWLForPredefinedMapping(inMemory);
        validateMappedOWLDataForPredefinedMapping(inMemory);

        Assert.assertEquals(totalNumberOfStatements, inMemory.size());
        mapping.getConfiguration().setServeVocabulary(true);

        OntGraphModel dynamic = OntModelFactory.createModel(new Union(mapping.getSchema(), mapping.getData()));

        DynamicSchemaTest.validateInferredOWLForPredefinedMapping(dynamic);
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
        Assert.assertEquals(compiledTriples, MappingHelper.asConnectingMapping(mapping).compiledPropertyBridges().size());

        Assert.assertEquals(totalNumberOfStatements, mapping.getDataModel().listStatements().toList().size());

        mapping.close();
    }

}
