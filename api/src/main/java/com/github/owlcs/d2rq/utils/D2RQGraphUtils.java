package com.github.owlcs.d2rq.utils;

import de.fuberlin.wiwiss.d2rq.jena.CachingGraph;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.jena.MappingGraph;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingHelper;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Triple;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.OntGraphUtils;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.utils.Graphs;

import java.util.Objects;

/**
 * Utilities to work with different kinds of {@code Graph}: {@link MappingGraph D2RQ Mapping Graph},
 * {@link GraphD2RQ D2RQ Data Graph}, {@link UnionGraph Hierarchical Union Graph}, etc.
 * <p>
 * Created by @ssz on 04.11.2018.
 *
 * @see Graphs
 */
@SuppressWarnings("WeakerAccess")
public class D2RQGraphUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(D2RQGraphUtils.class);

    /**
     * Creates a fresh {@link UnionGraph union} no distinct graph, that wraps the specified graph as primary.
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
     * Makes an in-memory {@code Graph} from the given {@link MappingGraph},
     * that refers to the {@link Mapping D2RQ Mapping Model}.
     * The returning graph contains both the schema and the data.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link Graph}, not {@code null}
     * @throws IllegalArgumentException in case the given is not {@link MappingGraph}
     * @see #isMappingGraph(Graph)
     */
    public static Graph toMemory(Graph g) {
        Mapping m = getMapping(g);
        GraphMem res = new GraphMem();
        if (!m.getConfiguration().getServeVocabulary()) {
            GraphUtil.addInto(res, m.getSchema());
        }
        GraphUtil.addInto(res, m.getData());
        return res;
    }

    /**
     * Makes a virtual Graph from the given {@link MappingGraph},
     * that refers to the {@link Mapping D2RQ Mapping Model}.
     * The returning graph contains both the schema and the data.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link Graph}, not {@code null}
     * @throws IllegalArgumentException in case the given graph is not Mapping
     * @see #isMappingGraph(Graph)
     */
    public static Graph toVirtual(Graph g) throws IllegalArgumentException {
        Mapping m = getMapping(g);
        Graph data = m.getData();
        if (m.getConfiguration().getServeVocabulary()) {
            return data;
        }
        Graph schema = m.getSchema();
        return new MappingGraph(MappingHelper.asConnectingMapping(m)) {
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

    /**
     * Extracts a Data {@link Graph} from the given graph, if it contains a reference to a {@link Mapping D2RQ Mapping}.
     * The returning {@code Graph} is either {@link GraphD2RQ D2RQ Data Graph} or {@link CachingGraph Caching Graph},
     * that wraps a D2RQ Data Graph.
     * If the flag {@link de.fuberlin.wiwiss.d2rq.map.Configuration#getServeVocabulary()} is set to {@code true},
     * the returning graph contains also a schema part.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link Graph}
     * @throws IllegalArgumentException in case the graph does not contain a reference to a {@code Mapping}
     */
    public static Graph getDataGraph(Graph g) {
        return getMapping(g).getData();
    }

    /**
     * Answers if the given graph contains a reference to a {@link Mapping D2RQ Mapping}.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@code true} if it is a Mapping Graph
     * @see #getMapping(Graph)
     */
    public static boolean isMappingGraph(Graph g) {
        return g instanceof MappingGraph
                || g instanceof CachingGraph
                && ((CachingGraph) g).getBase() instanceof MappingGraph;
    }

    /**
     * Extracts a {@link Mapping D2RQ Mapping} from the given graph if it is possible.
     *
     * @param g {@link Graph}, not {@code null}
     * @return {@link Mapping}
     * @throws IllegalArgumentException in case the graph does not contain a reference to a {@code Mapping}
     * @see #isMappingGraph(Graph)
     */
    public static Mapping getMapping(Graph g) throws IllegalArgumentException {
        Objects.requireNonNull(g, "Null graph");
        if (g instanceof CachingGraph) {
            g = ((CachingGraph) g).getBase();
        }
        if (g instanceof MappingGraph) {
            return MappingHelper.asMapping(((MappingGraph) g).getMapping());
        }
        throw new IllegalArgumentException("The given graph " + Graphs.getName(g) + " has no mapping inside");
    }

    /**
     * Closes all open connections, that may be encapsulated inside the specified graph.
     * The given graph may be {@link MappingGraph} or {@link GraphD2RQ},
     * or composite {@link UnionGraph} having one or more {@link MappingGraph Mapping Graph}s as components.
     *
     * @param g {@link Graph} graph to analyse and search for connections
     */
    public static void closeConnections(Graph g) {
        if (g instanceof MappingGraph) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Close <{}>", g);
            }
            g.close();
            return;
        }
        Graphs.baseGraphs(g).forEach(D2RQGraphUtils::closeConnections);
    }
}
