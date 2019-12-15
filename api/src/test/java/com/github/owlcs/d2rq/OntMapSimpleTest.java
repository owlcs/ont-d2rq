package com.github.owlcs.d2rq;

import com.github.owlcs.d2rq.conf.ConnectionData;
import com.github.owlcs.d2rq.utils.OWLUtils;
import com.github.owlcs.map.*;
import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.OntologyManager;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.model.OntClass;
import com.github.owlcs.ontapi.jena.model.OntDataProperty;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.vocabulary.XSD;
import com.github.owlcs.ontapi.utils.SP;
import com.github.owlcs.ontapi.utils.SPINMAPL;
import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
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
        OntModel target = createTargetModel(manager);
        OntModel source = createSourceModel(manager, TestData.CACHE.equals(test));
        JenaModelUtils.print(source);
        MapModel spin = composeMapping(manager, source, target);
        JenaModelUtils.print(spin.asGraphModel());

        LOGGER.debug("Run inference.");
        Graph data = OWLUtils.getDataGraph(source);
        Assert.assertTrue((TestData.CACHE.equals(test) ? CachingGraph.class : GraphD2RQ.class).isInstance(data));
        manager.getInferenceEngine(spin).run(data, target.getBaseGraph());
        LOGGER.debug("Done.");

        long actual = target.individuals().peek(x -> LOGGER.debug("{}", x)).count();
        Assert.assertEquals("Incorrect number of result individuals.", 7, actual);
        JenaModelUtils.print(target);
        OWLUtils.closeConnections(source);
    }

    public static OntModel createSourceModel(OntologyManager manager, boolean withCache) {
        D2RQGraphDocumentSource source = D2RQSpinTest.createSource(data, "iswc");
        Ontology res;
        try {
            res = manager.loadOntologyFromOntologyDocument(source);
        } catch (OWLOntologyCreationException e) {
            throw new AssertionError(e);
        }
        res.applyChange(new SetOntologyID(res, IRI.create("http://source.owlcs.github.com")));
        source.getMapping().getConfiguration().setWithCache(withCache);
        Assert.assertEquals(withCache, source.getMapping().getConfiguration().getWithCache());
        return res.asGraphModel();
    }

    public static OntModel createTargetModel(OntologyManager manager) {
        LOGGER.debug("Create the target model.");
        String uri = "http://target.owlcs.github.com";
        String ns = uri + "#";
        OntModel res = manager.createGraphModel(uri).setNsPrefixes(OntModelFactory.STANDARD);
        OntClass clazz = res.createOntClass(ns + "ClassTarget");
        OntDataProperty prop = res.createOntEntity(OntDataProperty.class, ns + "targetProperty");
        prop.addRange(res.getDatatype(XSD.xstring));
        prop.addDomain(clazz);
        Ontology o = manager.getOntology(IRI.create(uri));
        Assert.assertNotNull("Can't find ontology " + uri, o);
        o.axioms().forEach(x -> LOGGER.debug("{}", x));
        return res;
    }

    public static MapModel composeMapping(MapManager manager, OntModel source, OntModel target) {
        LOGGER.debug("Compose the (spin) mapping, that concatenates two data property assertion values into a new one");
        OntClass sourceClass = source.classes().findFirst().orElseThrow(AssertionError::new);
        OntClass targetClass = target.classes().findFirst().orElseThrow(AssertionError::new);
        List<OntDataProperty> sourceProperties = source.dataProperties().collect(Collectors.toList());
        OntDataProperty targetProperty = target.dataProperties().findFirst().orElse(null);
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
        ;
    }
}
