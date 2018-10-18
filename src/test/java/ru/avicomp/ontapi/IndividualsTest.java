package ru.avicomp.ontapi;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.D2RQGraphs;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Test loading database data in the form of OWLNamedIndividuals.
 * <p>
 * Created by @szuev on 25.02.2017.
 */
@RunWith(Parameterized.class)
public class IndividualsTest {
    private static final Logger LOGGER = Logger.getLogger(IndividualsTest.class);
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
        LOGGER.info("Source: " + source);

        OntologyModel schema = m.loadOntologyFromOntologyDocument(source);
        schema.axioms().forEach(LOGGER::debug);

        int expectedNumberOfIndividuals = 56;

        LOGGER.info("Test schema+data ontology.");
        OntGraphModel data = D2RQGraphs.reassembly(schema.asGraphModel());
        testIndividuals(data, expectedNumberOfIndividuals);

        LOGGER.info("Test there is no individuals inside schema ontology.");
        testIndividuals(schema.asGraphModel(), 0);

        // pass all data from DB to memory
        OntologyModel inMemory = m.createOntology();
        inMemory.asGraphModel().add(data);
        // add owl:NamedIndividual declarations
        data.listNamedIndividuals().forEach(i -> inMemory.asGraphModel().createResource(i.getURI(), OWL.NamedIndividual));
        inMemory.asGraphModel().write(System.out, "ttl");

        List<OWLAxiom> axioms = inMemory.axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toList());
        axioms.forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect number of class-assertion axioms", expectedNumberOfIndividuals, axioms.size());

        D2RQGraphs.close(data);
    }

    private void testIndividuals(OntGraphModel model, int expected) {
        List<OntIndividual.Named> individuals = model.listNamedIndividuals().collect(Collectors.toList());
        LOGGER.debug("Number of individuals " + individuals.size());
        individuals.forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect number of (named)individuals", expected, individuals.size());
        Assert.assertEquals("Incorrect number of (all)individuals", expected, model.ontObjects(OntIndividual.class).count());
    }
}
