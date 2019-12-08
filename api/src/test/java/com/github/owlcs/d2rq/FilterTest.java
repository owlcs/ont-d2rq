package com.github.owlcs.d2rq;

import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import org.apache.jena.rdf.model.Resource;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.owlcs.d2rq.conf.ConnectionData;
import com.github.owlcs.d2rq.utils.OWLUtils;
import ru.avicomp.ontapi.OntManagers;
import ru.avicomp.ontapi.OntologyID;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntOPE;
import ru.avicomp.ontapi.jena.model.OntPE;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Test loading filtered db scheme.
 * <p>
 * Created by @szuev on 25.02.2017.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(Parameterized.class)
public class FilterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterTest.class);
    private ConnectionData data;

    private static Map<ConnectionData, Map<OntologyModel, Integer>> testResult = new HashMap<>();

    public FilterTest(ConnectionData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ConnectionData[] getData() {
        return ConnectionData.values();
    }

    @Test
    public void test01FilterSchema() throws Exception {
        LOGGER.info("Load full db schema from {}", data);
        D2RQGraphDocumentSource source1 = data.toDocumentSource("iswc");
        OntologyManager manager = OntManagers.createONT();
        OntologyModel full = manager.loadOntologyFromOntologyDocument(source1);
        full.axioms().map(Object::toString).forEach(LOGGER::debug);
        source1.close();

        // creates a filter:
        LOGGER.info("Load the restricted model from db (property constraints)");
        OWLDataProperty dp = full.dataPropertiesInSignature().sorted().findFirst()
                .orElseThrow(() -> new AssertionError("Can't find any data property."));
        OWLObjectProperty op = full.objectPropertiesInSignature().sorted().findFirst()
                .orElseThrow(() -> new AssertionError("Can't find any object property."));
        MappingFilter filter1 = MappingFilter.create(dp, op);
        LOGGER.debug("Constraint properties: {}", filter1.properties().collect(Collectors.toList()));

        // creates a source with filter by properties:
        D2RQGraphDocumentSource source2 = source1.filter(filter1);
        // load a model:
        OntologyModel filteredByProperties = manager.loadOntologyFromOntologyDocument(source2);
        // set iri"
        OWLOntologyID id2 = OntologyID.create("http://d2rq.example.com", "http://d2rq.example.com/version/1.0");
        filteredByProperties.applyChange(new SetOntologyID(filteredByProperties, id2));
        JenaModelUtils.print(filteredByProperties.asGraphModel());
        // validate:
        Assert.assertEquals("Expected two ontologies", 2, manager.ontologies().count());
        Assert.assertTrue("Can't find " + id2, manager.contains(id2));
        Assert.assertNotEquals(full.asGraphModel().getBaseGraph(), filteredByProperties.asGraphModel().getBaseGraph());
        Assert.assertEquals("Expected two classes", 2, filteredByProperties.asGraphModel().classes().count());
        Assert.assertEquals("Expected one data property", 1, filteredByProperties.asGraphModel().dataProperties().count());
        Assert.assertEquals("Expected one object property", 1, filteredByProperties.asGraphModel().objectProperties().count());
        source2.close();
        testResult.computeIfAbsent(data, d -> new HashMap<>()).put(filteredByProperties, 11);

        // creates a filter:
        LOGGER.info("Load the restricted model from db (class constraints)");
        OntOPE p = full.asGraphModel().objectProperties().min(Comparator.comparing(Resource::getURI))
                .orElseThrow(() -> new AssertionError("Can't find any ont object property."));
        IRI class1 = p.ranges().map(Resource::getURI).map(IRI::create).sorted().findFirst()
                .orElseThrow(() -> new AssertionError("Can't find range for " + p));
        IRI class2 = p.domains().map(Resource::getURI).map(IRI::create).sorted().findFirst()
                .orElseThrow(() -> new AssertionError("Can't find domain for " + p));
        MappingFilter filter2 = MappingFilter.create().includeClass(class1).includeClass(class2);
        LOGGER.debug("Constraint classes: " + filter2.classes().collect(Collectors.toList()));

        D2RQGraphDocumentSource source3 = source1.filter(filter2);
        OntologyModel filteredByClasses = manager.loadOntologyFromOntologyDocument(source3);
        OWLOntologyID id3 = OntologyID.create("http://d2rq.example.com", "http://d2rq.example.com/version/2.0");
        filteredByClasses.applyChange(new SetOntologyID(filteredByClasses, id3));
        JenaModelUtils.print(filteredByClasses.asGraphModel());
        // validate:
        Assert.assertEquals("Expected three ontologies", 3, manager.ontologies().count());
        Assert.assertTrue("Can't find " + id3, manager.contains(id3));
        Assert.assertEquals("Expected two classes", 1, filteredByClasses.asGraphModel().classes().count());
        List<OntPE> props = filteredByClasses.asGraphModel().ontObjects(OntPE.class).collect(Collectors.toList());
        props.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertFalse("No properties:", props.isEmpty());
        source3.close();
        testResult.computeIfAbsent(data, d -> new HashMap<>()).put(filteredByClasses, 9);
    }

    @Test
    public void test02FilterData() {
        Map<OntologyModel, Integer> res = testResult.get(data);
        Assume.assumeNotNull(res);
        res.forEach((schema, expectedCount) -> {
            LOGGER.info("Test data for ontology {}", schema.getOntologyID());
            OntGraphModel withData = OWLUtils.toMemory(schema.asGraphModel());
            Assert.assertEquals("Ontology IDs don't match", schema.asGraphModel().getID(), withData.getID());

            Set<OntIndividual> individuals = withData.ontObjects(OntIndividual.class).collect(Collectors.toSet());
            individuals.forEach(x -> LOGGER.debug("{}", x));
            Assert.assertEquals("Wrong individuals count", expectedCount.intValue(), individuals.size());
            OWLUtils.closeConnections(schema);
        });

    }


}
