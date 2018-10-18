package ru.avicomp.ontapi.jena.utils;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import org.apache.jena.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.HybridGraph;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.OntGraphModelImpl;
import ru.avicomp.ontapi.jena.impl.conf.D2RQModelConfig;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.util.Objects;

/**
 * Utils to work with {@link GraphD2RQ} and with ONT-API Graphs.
 * <p>
 * Created by @szuev on 23.03.2018.
 * @see Graphs
 * @see GraphD2RQ
 * @see UnionGraph
 * @see OntGraphModel
 */
@SuppressWarnings("WeakerAccess")
public class D2RQGraphs {

    private static final Logger LOGGER = LoggerFactory.getLogger(D2RQGraphs.class);

    /**
     * Re-assemblies the model.
     *
     * @param model {@link OntGraphModel}
     * @return {@link OntGraphModel}
     * @see #reassembly(OntGraphModel, OntPersonality)
     */
    public static OntGraphModel reassembly(OntGraphModel model) {
        OntPersonality personality;
        if (model instanceof OntGraphModelImpl) {
            personality = ((OntGraphModelImpl) model).getPersonality();
        } else {
            personality = D2RQModelConfig.D2RQ_PERSONALITY;
        }
        return reassembly(model, personality);
    }

    /**
     * Re-assemblies the model.
     * If it contains a {@link HybridGraph} graph with {@link GraphD2RQ} as shadow inside,
     * the method extracts that D2RQ graph and wraps it as fresh {@link OntGraphModel}.
     * Otherwise returns an equivalent model with the same hierarchy structure.
     *
     * @param model       {@link OntGraphModel}
     * @param personality {@link OntPersonality}
     * @return {@link OntGraphModel}
     */
    public static OntGraphModel reassembly(OntGraphModel model, OntPersonality personality) {
        return OntModelFactory.createModel(reassembly((UnionGraph) model.getGraph()), personality);
    }

    /**
     * Closes all connections linked to underlying D2RQ Graphs
     *
     * @param o {@link OntGraphModel}
     */
    public static void close(OntGraphModel o) {
        close((UnionGraph) o.getGraph());
    }

    /**
     * Closes all D2RQ Graphs from the given graph-container.
     *
     * @param graph {@link UnionGraph}
     */
    public static void close(UnionGraph graph) {
        Graphs.flat(graph)
                .map(D2RQGraphs::extractD2RQ)
                .filter(Objects::nonNull)
                .forEach(g -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Close {}", g);
                    }
                    g.close();
                });
    }

    /**
     * Makes a new {@link UnionGraph} from existing one by extracting hidden {@link GraphD2RQ} graphs.
     * The result graph has the same structure as specified, but instead a {@link HybridGraph hybrid} there are {@link GraphD2RQ d2rq} members.
     *
     * @param graph {@link UnionGraph}
     * @return {@link UnionGraph}
     */
    public static UnionGraph reassembly(UnionGraph graph) {
        UnionGraph res = new UnionGraph(graph.getBaseGraph() instanceof HybridGraph ?
                extractD2RQ(graph.getBaseGraph()) : graph.getBaseGraph());
        graph.getUnderlying().graphs()
                .map(g -> g instanceof HybridGraph ? extractD2RQ(g) : g)
                .forEach(g -> res.addGraph(g instanceof UnionGraph ? reassembly((UnionGraph) g) : g));
        return res;
    }

    /**
     * Extracts D2RQ Graph from a graph container, if it is possible.
     *
     * @param g {@link Graph}
     * @return {@link GraphD2RQ} or {@code null}
     */
    public static GraphD2RQ extractD2RQ(Graph g) {
        if (g instanceof GraphD2RQ) {
            return (GraphD2RQ) g;
        }
        if (g instanceof HybridGraph) {
            return ((HybridGraph) g).hidden()
                    .filter(GraphD2RQ.class::isInstance)
                    .map(GraphD2RQ.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Can't find D2RQ Graph"));
        }
        return null;
    }
}
