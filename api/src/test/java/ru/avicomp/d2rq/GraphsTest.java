package ru.avicomp.d2rq;

import de.fuberlin.wiwiss.d2rq.jena.VirtualGraph;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.compose.Union;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.d2rq.conf.ISWCData;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntDT;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Created by @ssz on 15.10.2018.
 */
public class GraphsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphsTest.class);

    @Test
    public void testMaskGraph() {
        Model m = ISWCData.MYSQL.loadMapping().asModel();

        Graph graph = VirtualGraph.createMaskGraph(m.getGraph(),
                ((BiPredicate<Graph, Triple>) (g, t) -> g.contains(t.getSubject(), RDF.type.asNode(), D2RQ.PropertyBridge.asNode()))
                        .or((g, t) -> g.contains(t.getSubject(), RDF.type.asNode(), D2RQ.ClassMap.asNode())));

        Model x = ModelFactory.createModelForGraph(graph);
        Resource r = m.createResource("ex", OWL.Class).addProperty(RDFS.comment, "xxxx");

        JenaModelUtils.print(x);
        Assert.assertFalse(x.containsResource(D2RQ.PropertyBridge));
        Assert.assertTrue(x.containsResource(OWL.Class));

        Assert.assertTrue(m.containsResource(OWL.Class));
        Assert.assertTrue(m.containsResource(D2RQ.PropertyBridge));

        m.removeAll(r, null, null);
        JenaModelUtils.print(x);

        Assert.assertFalse(x.containsResource(OWL.Class));
        Assert.assertFalse(m.containsResource(OWL.Class));
    }


    @Test
    public void testUnionModelFindAll() {
        try (Mapping mapping = ISWCData.MYSQL.loadMapping()) {
            mapping.getConfiguration().setServeVocabulary(false);
            Graph left = mapping.getSchema();
            // connection:
            Graph right = mapping.getDataModel().getGraph();

            Model u = ModelFactory.createModelForGraph(new Union(left, right));

            // findAll: test no java.util.ConcurrentModificationException:
            String ttl = JenaModelUtils.toTurtleString(u);

            LOGGER.debug("\n{}", ttl);

            Assert.assertTrue(ttl.startsWith("@prefix"));
            Assert.assertTrue(ttl.split("\n").length > 100);

            Assert.assertFalse(u.listStatements().toList().isEmpty());
        }
    }

    @Test
    public void testOntGraphModelModifyAndFindAll() {
        try (Mapping mapping = ISWCData.MYSQL.loadMapping()) {
            mapping.getConfiguration().setControlOWL(true);
            Graph left = mapping.getSchema();
            // connection:
            Graph right = mapping.getDataModel().getGraph();

            OntGraphModel u = OntModelFactory.createModel(new Union(left, right));

            u.createOntEntity(OntDT.class, u.expandPrefix("iswc:string"));

            List<OntClass> classes = u.classes().collect(Collectors.toList());

            classes.forEach(x -> LOGGER.debug("{}", x));

            Assert.assertFalse(classes.isEmpty());
        }
    }
}
