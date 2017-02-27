package ru.avicomp.ontapi;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import ru.avicomp.ontapi.jena.impl.configuration.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.configuration.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntIndividual;

/**
 * Test loading database data in the form of OWLNamedIndividuals.
 * Here we use the method {@link ru.avicomp.ontapi.jena.Hybrid#switchTo(Graph)},
 * but if we really need list of all individuals we better put them to memory without switching.
 * <p>
 * Created by @szuev on 25.02.2017.
 */
public class IndividualsTest {

    private static final Logger LOGGER = Logger.getLogger(IndividualsTest.class);

    @Test
    public void testList() throws Exception {
        OntologyManager m = OntManagerFactory.createONTManager();
        // overwrite global individual factories
        OntPersonality newGlobalPersonality = ONTAPITests.createD2RQPersonality();
        OntModelConfig.setPersonality(newGlobalPersonality);

        LOGGER.info("Load full db schema from " + ONTAPITests.JDBC_IRI);
        D2RQGraphDocumentSource source = new D2RQGraphDocumentSource(ONTAPITests.JDBC_IRI);
        LOGGER.info("Source: " + source);

        OntologyModel o = (OntologyModel) m.loadOntologyFromOntologyDocument(source);
        o.axioms().forEach(LOGGER::debug);

        int expectedNumberOfIndividuals = 56;

        LOGGER.info("Switch to data view.");
        ONTAPITests.switchTo(o, GraphD2RQ.class);
        testIndividuals(o, expectedNumberOfIndividuals);

        LOGGER.info("Switch back to schema view.");
        ONTAPITests.switchTo(o, GraphMem.class);
        testIndividuals(o, 0);

        LOGGER.info("Switch to data view again.");
        ONTAPITests.switchTo(o, GraphD2RQ.class);
        testIndividuals(o, expectedNumberOfIndividuals);

        List<OWLAxiom> axioms = o.axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toList());
        axioms.forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect number of class-assertion axioms", expectedNumberOfIndividuals, axioms.size());
    }

    private void testIndividuals(OntologyModel model, int expected) {
        List<OntIndividual.Named> individuals = model.asGraphModel().listNamedIndividuals().collect(Collectors.toList());
        LOGGER.debug("Number of individuals " + individuals.size());
        individuals.forEach(LOGGER::debug);
        Assert.assertEquals("Incorrect number of (named)individuals", expected, individuals.size());
        Assert.assertEquals("Incorrect number of (all)individuals", expected, model.asGraphModel().ontObjects(OntIndividual.class).count());
    }
}
