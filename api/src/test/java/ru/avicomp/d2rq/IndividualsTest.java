package ru.avicomp.d2rq;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ConnectionData;
import ru.avicomp.d2rq.utils.OWLUtils;
import ru.avicomp.ontapi.OntManagers;
import ru.avicomp.ontapi.OntologyManager;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Test loading database data in the form of OWLNamedIndividuals.
 * <p>
 * Created by @szuev on 25.02.2017.
 */
@RunWith(Parameterized.class)
public class IndividualsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndividualsTest.class);
    private ConnectionData data;

    public IndividualsTest(ConnectionData data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static ConnectionData[] getData() {
        return ConnectionData.values();
    }

    @Test
    public void testList() throws Exception {
        OntologyManager m = OntManagers.createONT();

        LOGGER.info("Load full db schema from " + data);
        D2RQGraphDocumentSource source = data.toDocumentSource("iswc");
        LOGGER.info("Source: {}", source);

        OntologyModel schema = m.loadOntologyFromOntologyDocument(source);
        schema.axioms().forEach(x -> LOGGER.debug("{}", x));

        int expectedNumberOfIndividuals = 56;

        LOGGER.info("Test schema+data ontology.");
        OntGraphModel virtual = OWLUtils.toVirtual(schema.asGraphModel());
        testIndividuals(virtual, expectedNumberOfIndividuals);

        LOGGER.info("Test there is no individuals inside schema ontology.");
        testIndividuals(schema.asGraphModel(), 0);

        // pass all data from DB to memory
        OntologyModel inMemory = m.createOntology();
        inMemory.asGraphModel().add(virtual);

        List<OWLAxiom> axioms = inMemory.axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toList());
        axioms.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect number of class-assertion axioms", expectedNumberOfIndividuals, axioms.size());

        OWLUtils.closeConnections(schema);
    }

    private void testIndividuals(OntGraphModel model, int expected) {
        List<OntIndividual.Named> individuals = model.listNamedIndividuals().collect(Collectors.toList());
        LOGGER.debug("Number of individuals " + individuals.size());
        individuals.forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect number of (named)individuals", expected, individuals.size());
        Assert.assertEquals("Incorrect number of (all)individuals", expected, model.ontObjects(OntIndividual.class).count());
    }
}
