package ru.avicomp.ontapi;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import org.apache.jena.graph.Graph;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.SetOntologyID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.map.*;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.utils.D2RQGraphs;
import ru.avicomp.ontapi.jena.vocabulary.XSD;
import ru.avicomp.ontapi.utils.SP;
import ru.avicomp.ontapi.utils.SPINMAPL;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The same as {@link D2RQSpinTest} but with use of ONT-MAP instead of direct SPIN.
 * <p>
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings("WeakerAccess")
@RunWith(Parameterized.class)
public class OntMapSimpleTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(OntMapSimpleTest.class);

    private static ConnectionData data = ConnectionData.POSTGRES;
    private final TestData test;

    public OntMapSimpleTest(TestData test) {
        this.test = test;
    }

    @Parameterized.Parameters(name = "{0}")
    public static TestData[] getData() {
        return TestData.values();
    }

    @Test
    public void testInference() {
        OWLMapManager manager = Managers.createOWLMapManager();
        OntGraphModel target = createTargetModel(manager);
        OntGraphModel source = createSourceModel(manager);
        D2RQTestHelper.print(source);
        MapModel spin = composeMapping(manager, source, target);
        D2RQTestHelper.print(spin.asGraphModel());

        LOGGER.debug("Run inference.");
        Graph data = D2RQGraphs.reassembly(source).getGraph();
        if (TestData.CACHE.equals(test)) {
            data = new CachingGraph(data);
        }
        manager.getInferenceEngine().run(spin, data, target.getBaseGraph());
        LOGGER.debug("Done.");

        target.listNamedIndividuals().forEach(x -> LOGGER.debug("{}", x));
        Assert.assertEquals("Incorrect number of result individuals.", 7, target.listNamedIndividuals().count());
        D2RQTestHelper.print(target);
        D2RQGraphs.close((UnionGraph) source.getGraph());
    }

    public static OntGraphModel createSourceModel(OntologyManager manager) {
        D2RQGraphDocumentSource source = D2RQSpinTest.createSource(data, "iswc");
        OntologyModel res;
        try {
            res = manager.loadOntologyFromOntologyDocument(source);
        } catch (OWLOntologyCreationException e) {
            throw new AssertionError(e);
        }
        res.applyChange(new SetOntologyID(res, IRI.create("http://source.avicomp.ru")));
        return res.asGraphModel();
    }

    public static OntGraphModel createTargetModel(OntologyManager manager) {
        LOGGER.debug("Create the target model.");
        String uri = "http://target.avicomp.ru";
        String ns = uri + "#";
        OntGraphModel res = manager.createGraphModel(uri).setNsPrefixes(OntModelFactory.STANDARD);
        OntClass clazz = res.createOntEntity(OntClass.class, ns + "ClassTarget");
        OntNDP prop = res.createOntEntity(OntNDP.class, ns + "targetProperty");
        prop.addRange(res.getOntEntity(OntDT.class, XSD.xstring));
        prop.addDomain(clazz);
        OntologyModel o = manager.getOntology(IRI.create(uri));
        Assert.assertNotNull("Can't find ontology " + uri, o);
        o.axioms().forEach(x -> LOGGER.debug("{}", x));
        return res;
    }

    public static MapModel composeMapping(MapManager manager, OntGraphModel source, OntGraphModel target) {
        LOGGER.debug("Compose the (spin) mapping.");
        OntClass sourceClass = source.listClasses().findFirst().orElseThrow(AssertionError::new);
        OntClass targetClass = target.listClasses().findFirst().orElseThrow(AssertionError::new);
        List<OntNDP> sourceProperties = source.listDataProperties().collect(Collectors.toList());
        OntNDP targetProperty = target.listDataProperties().findFirst().orElse(null);
        MapModel res = manager.createMapModel();

        MapFunction.Builder self = manager.getFunction(SPINMAPL.self).create();
        MapFunction.Builder concat = manager.getFunction(SPINMAPL.concatWithSeparator).create();

        res.createContext(sourceClass, targetClass, self.build())
                .addPropertyBridge(concat
                        .addProperty(SP.arg1, sourceProperties.get(0))
                        .addProperty(SP.arg2, sourceProperties.get(1))
                        .addLiteral(SPINMAPL.separator, ", "), targetProperty);
        return res;
    }

    enum TestData {
        CACHE,
        NO_CACHE,
    }
}
