package com.github.owlcs.d2rq.utils;

import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.jena.OntModelFactory;
import com.github.owlcs.ontapi.jena.UnionGraph;
import com.github.owlcs.ontapi.jena.impl.PersonalityModel;
import com.github.owlcs.ontapi.jena.impl.conf.OntModelConfig;
import com.github.owlcs.ontapi.jena.impl.conf.OntPersonality;
import com.github.owlcs.ontapi.jena.model.OntEntity;
import com.github.owlcs.ontapi.jena.model.OntIndividual;
import com.github.owlcs.ontapi.jena.model.OntModel;
import com.github.owlcs.ontapi.jena.utils.Graphs;
import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.RDFNode;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by @ssz on 20.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class OWLUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OWLUtils.class);

    public static <X extends OntEntity> X findEntity(OntModel m, Class<X> type, String shortForm) {
        X res = m.getOntEntity(type, m.expandPrefix(shortForm));
        Assert.assertNotNull("Can't find " + type.getSimpleName() + " " + shortForm, res);
        return res;
    }

    public static void validateOWLEntities(OntModel m,
                                           int classes,
                                           int objectProperties,
                                           int dataProperties,
                                           int annotationProperties,
                                           int namedIndividuals,
                                           int anonymousIndividuals) {
        // class assertions:
        Set<OntIndividual> individuals = m.individuals()
                .peek(i -> LOGGER.debug("Individual: {}", i))
                .collect(Collectors.toSet());
        Assert.assertEquals(namedIndividuals + anonymousIndividuals, individuals.size());

        Assert.assertEquals(namedIndividuals, individuals.stream().filter(RDFNode::isURIResource).count());
        Assert.assertEquals(anonymousIndividuals, individuals.stream().filter(RDFNode::isAnon).count());

        Assert.assertEquals(classes, m.classes().peek(x -> LOGGER.debug("Class: {}", x)).count());

        Assert.assertEquals(annotationProperties, m.annotationProperties()
                .peek(x -> LOGGER.debug("AnnotationProperty: {}", x)).count());
        Assert.assertEquals(objectProperties, m.objectProperties()
                .peek(x -> LOGGER.debug("ObjectProperty: {}", x)).count());
        Assert.assertEquals(dataProperties, m.dataProperties()
                .peek(x -> LOGGER.debug("DatatypeProperty: {}", x)).count());
    }

    public static OntModel toMemory(OntModel m) {
        Assert.assertTrue(D2RQGraphUtils.isMappingGraph(m.getBaseGraph()));
        UnionGraph res = build(getUnionGraph(m), D2RQGraphUtils::toMemory);
        return OntModelFactory.createModel(res, getPersonality(m));
    }

    public static OntModel toVirtual(OntModel m) {
        Assert.assertTrue(D2RQGraphUtils.isMappingGraph(m.getBaseGraph()));
        UnionGraph res = build(getUnionGraph(m), D2RQGraphUtils::toVirtual);
        return OntModelFactory.createModel(res, getPersonality(m));
    }

    public static Graph getDataGraph(OntModel m) {
        Assert.assertTrue(D2RQGraphUtils.isMappingGraph(m.getBaseGraph()));
        return D2RQGraphUtils.getDataGraph(m.getBaseGraph());
    }

    public static UnionGraph build(UnionGraph graph, Function<Graph, Graph> baseGraphMapper) {
        return build(graph, D2RQGraphUtils::createUnionGraph, baseGraphMapper);
    }

    /**
     * Assembles a new {@link UnionGraph Union Graph} from the given {@code hierarchyGraph}
     * preserving the hierarchy structure and using the {@code unionGraphFactory}
     * to produce new instances and {@code hybridGraphHandler} to convert {@link de.fuberlin.wiwiss.d2rq.jena.MappingGraph} into the desired from.
     *
     * @param hierarchyGraph     {@link UnionGraph}, the original graph
     * @param unionGraphFactory  {@code Function}, a factory to create fresh {@link UnionGraph} instance
     * @param mappingGraphHandler {@code Function}, a factory to handle {@link de.fuberlin.wiwiss.d2rq.jena.MappingGraph} if it is present in the structure
     * @return {@link UnionGraph}
     */
    public static UnionGraph build(UnionGraph hierarchyGraph,
                                   Function<Graph, UnionGraph> unionGraphFactory,
                                   Function<Graph, Graph> mappingGraphHandler) {
        Graph base = hierarchyGraph.getBaseGraph();
        if (D2RQGraphUtils.isMappingGraph(base)) {
            base = mappingGraphHandler.apply(base);
        }
        UnionGraph res = unionGraphFactory.apply(base);
        hierarchyGraph.getUnderlying()
                .graphs().map(x -> x instanceof UnionGraph ? build((UnionGraph) x, mappingGraphHandler) : x)
                .forEach(res::addGraph);
        return res;
    }

    /**
     * Retrieves an {@link UnionGraph} from the {@link OntModel}.
     *
     * @param m {@link OntModel}
     * @return {@link UnionGraph}
     * @throws ClassCastException unexpected {@link OntModel} implementation is specified
     */
    public static UnionGraph getUnionGraph(OntModel m) throws ClassCastException {
        return (UnionGraph) m.getGraph();
    }

    public static boolean isInMemory(Graph g) {
        return Graphs.baseGraphs(g).allMatch(GraphMem.class::isInstance);
    }

    public static OntPersonality getPersonality(OntModel m) {
        return m instanceof PersonalityModel ? ((PersonalityModel) m).getOntPersonality() :
                OntModelConfig.ONT_PERSONALITY_LAX;
    }

    public static void closeConnections(Ontology o) {
        closeConnections(o.asGraphModel());
    }

    public static void closeConnections(OntModel m) {
        D2RQGraphUtils.closeConnections(m.getGraph());
    }

}
