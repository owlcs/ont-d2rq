package ru.avicomp.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.mem.GraphMem;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.OntologyModel;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.OntModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntEntity;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.utils.D2RQGraphUtils;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.util.function.Function;

/**
 * Created by @ssz on 20.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class OWLUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(OWLUtils.class);

    public static <X extends OntEntity> X findEntity(OntGraphModel m, Class<X> type, String shortForm) {
        X res = m.getOntEntity(type, m.expandPrefix(shortForm));
        Assert.assertNotNull("Can't find " + type.getSimpleName() + " " + shortForm, res);
        return res;
    }

    public static void validateOWLEntities(OntGraphModel m,
                                           int classes,
                                           int objectProperties,
                                           int dataProperties,
                                           int annotationProperties,
                                           int namedIndividuals,
                                           int anonymousIndividuals) {
        Assert.assertEquals(namedIndividuals, m.listNamedIndividuals()
                .peek(i -> LOGGER.debug("Named: {}", i)).count());
        Assert.assertEquals(anonymousIndividuals, m.ontObjects(OntIndividual.Anonymous.class)
                .peek(i -> LOGGER.debug("Anonymous: {}", i)).count());

        Assert.assertEquals(namedIndividuals + anonymousIndividuals, m.ontObjects(OntIndividual.class)
                .peek(i -> LOGGER.debug("Individual: {}", i)).count());

        Assert.assertEquals(classes, m.listClasses().peek(x -> LOGGER.debug("Class: {}", x)).count());

        Assert.assertEquals(annotationProperties, m.listAnnotationProperties()
                .peek(x -> LOGGER.debug("AnnotationProperty: {}", x)).count());
        Assert.assertEquals(objectProperties, m.listObjectProperties()
                .peek(x -> LOGGER.debug("ObjectProperty: {}", x)).count());
        Assert.assertEquals(dataProperties, m.listDataProperties()
                .peek(x -> LOGGER.debug("DatatypeProperty: {}", x)).count());
    }

    public static OntGraphModel toMemory(OntGraphModel m) {
        Assert.assertTrue(D2RQGraphUtils.isMappingGraph(m.getBaseGraph()));
        UnionGraph res = build(getUnionGraph(m), D2RQGraphUtils::toMemory);
        return OntModelFactory.createModel(res, getPersonality(m));
    }

    public static OntGraphModel toVirtual(OntGraphModel m) {
        Assert.assertTrue(D2RQGraphUtils.isMappingGraph(m.getBaseGraph()));
        UnionGraph res = build(getUnionGraph(m), D2RQGraphUtils::toVirtual);
        return OntModelFactory.createModel(res, getPersonality(m));
    }

    public static Graph getDataGraph(OntGraphModel m) {
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
     * Retrieves an {@link UnionGraph} from the {@link OntGraphModel}.
     *
     * @param m {@link OntGraphModel}
     * @return {@link UnionGraph}
     * @throws ClassCastException unexpected {@link OntGraphModel} implementation is specified
     */
    public static UnionGraph getUnionGraph(OntGraphModel m) throws ClassCastException {
        return (UnionGraph) m.getGraph();
    }

    public static boolean isInMemory(Graph g) {
        return Graphs.flat(g).allMatch(GraphMem.class::isInstance);
    }

    public static OntPersonality getPersonality(OntGraphModel m) {
        return m instanceof OntGraphModelImpl ? ((OntGraphModelImpl) m).getPersonality() :
                OntModelConfig.ONT_PERSONALITY_LAX;
    }

    public static void closeConnections(OntologyModel o) {
        closeConnections(o.asGraphModel());
    }

    public static void closeConnections(OntGraphModel m) {
        D2RQGraphUtils.closeConnections(m.getGraph());
    }

}
