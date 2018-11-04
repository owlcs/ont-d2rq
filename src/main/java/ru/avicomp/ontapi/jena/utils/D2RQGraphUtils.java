package ru.avicomp.ontapi.jena.utils;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.jena.MappingGraph;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.OntGraphUtils;
import ru.avicomp.ontapi.jena.HybridGraph;
import ru.avicomp.ontapi.jena.UnionGraph;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilities to work with different kinds of Graph: {@link GraphD2RQ D2RQ Data Graph},
 * {@link HybridGraph D2RQ Hybrid Graph}, {@link MappingGraph}, {@link UnionGraph}, etc.
 * <p>
 * Created by @ssz on 04.11.2018.
 *
 * @see Graphs
 */
public class D2RQGraphUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(D2RQGraphUtils.class);

    /**
     * Answers if the given graph is {@link GraphD2RQ D2RQ Data Graph}.
     *
     * @param g {@link Graph} to test
     * @return boolean
     */
    public static boolean isDataGraph(Graph g) {
        return g instanceof GraphD2RQ;
    }

    /**
     * Creates a fresh {@link UnionGraph union} no distict graph, that wraps the specified graph as base,
     *
     * @param g {@link Graph}
     * @return {@link UnionGraph}
     */
    public static UnionGraph createUnionGraph(Graph g) {
        return new UnionGraph(g, null, null, false) {

            @Override
            public String toString() {
                return OntGraphUtils.toString(this);
            }
        };
    }

    /**
     * Answers if the given graph is a {@link HybridGraph},
     * that is produced by the {@link ru.avicomp.ontapi.D2RQGraphDocumentSource D2RQ OGDS}.
     * The {@link HybridGraph D2RQ Hybrid} must have a Schema {@link MappingGraph} as primary part
     * and {@link GraphD2RQ D2RQ Data Graph} as hidden part.
     *
     * @param g {@link Graph} to test
     * @return boolean
     */
    public static boolean isD2RQHybrid(Graph g) {
        if (!(g instanceof HybridGraph)) {
            return false;
        }
        List<Graph> graphs = ((HybridGraph) g).hidden().collect(Collectors.toList());
        if (graphs.size() != 1) return false;
        return isDataGraph(graphs.get(0));
    }

    /**
     * Gets a {@link GraphD2RQ D2RQ Data Graph} from the given {@link HybridGraph D2RQ Hybrid Draph},
     * that is produced by the {@link ru.avicomp.ontapi.D2RQGraphDocumentSource D2RQ OGDS}.
     *
     * @param h {@link HybridGraph}, not {@code null}
     * @return {@link GraphD2RQ}, not {@code null}
     * @throws IllegalArgumentException in case the given hybrid has unexpected structure
     */
    public static GraphD2RQ getDataGraph(HybridGraph h) throws IllegalArgumentException {
        return h.hidden(GraphD2RQ.class).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Can't find D2RQ Graph inside the hybrid " + h));
    }

    /**
     * Closes all open connections, that may be encapsulated inside the specified graph.
     * The given graph may be {@link MappingGraph} or {@link HybridGraph},
     * or composite {@link UnionGraph} having one or more {@link MappingGraph Mapping Graph}s as components.
     *
     * @param graph {@link Graph} graph to analyse and search for connections
     * @see #isD2RQHybrid(Graph)
     */
    public static void closeConnections(Graph graph) {
        if (graph instanceof MappingGraph) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Close <{}>", graph);
            graph.close();
            return;
        }
        if (isD2RQHybrid(graph)) {
            closeConnections(getDataGraph((HybridGraph) graph));
            return;
        }
        Graphs.flat(graph).forEach(D2RQGraphUtils::closeConnections);
    }

    /**
     * Makes an in-memory Graph from the specified {@link HybridGraph},
     * that should have Schema Graph as primary and {@link GraphD2RQ D2RQ Data Graph} as a hidden part.
     *
     * @param h {@link HybridGraph}, not {@code null}
     * @return {@link Graph}
     * @throws IllegalArgumentException in case the given hybrid has unexpected structure
     * @see #isD2RQHybrid(Graph)
     */
    public static Graph toMemory(HybridGraph h) throws IllegalArgumentException {
        return ((MappingGraph) toVirtual(h)).toMemory();
    }

    /**
     * Makes a virtual Graph from the specified {@link HybridGraph},
     * that should have Schema Graph as primary and {@link GraphD2RQ D2RQ Data Graph} as a hidden part.
     *
     * @param h {@link HybridGraph}, not {@code null}
     * @return {@link Graph}
     * @throws IllegalArgumentException in case the given hybrid has unexpected structure
     * @see #isD2RQHybrid(Graph)
     */
    public static Graph toVirtual(HybridGraph h) throws IllegalArgumentException {
        GraphD2RQ data = getDataGraph(h);
        if (data.containsSchema()) return data;
        Graph schema = h.getWrapped();
        return new MappingGraph(data.getMapping()) {
            @Override
            public ExtendedIterator<Triple> graphBaseFind(Triple t) {
                return schema.find(t).andThen(data.find(t));
            }

            @Override
            public String toString() {
                return String.format("Virtual[%s, %s]", schema, data);
            }
        };
    }
}
