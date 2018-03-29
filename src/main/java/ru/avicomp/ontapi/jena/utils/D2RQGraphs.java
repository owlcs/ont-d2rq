package ru.avicomp.ontapi.jena.utils;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import org.apache.jena.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.internal.ConfigProvider;
import ru.avicomp.ontapi.jena.Hybrid;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.impl.conf.OntPersonality;
import ru.avicomp.ontapi.jena.model.OntGraphModel;

import java.util.Objects;

/**
 * Utils to work with {@link GraphD2RQ} and with ONT-API Graphs.
 * <p>
 * Created by @szuev on 23.03.2018.
 * @see GraphD2RQ
 * @see UnionGraph
 * @see OntGraphModel
 */
public class D2RQGraphs {

    private static final Logger LOGGER = LoggerFactory.getLogger(D2RQGraphs.class);

    public static OntGraphModel reassembly(OntGraphModel model) {
        // class-cast-exception if it is not model from manager:
        return reassembly(model, ((ConfigProvider) model).getConfig().loaderConfig().getPersonality());
    }

    public static OntGraphModel reassembly(OntGraphModel model, OntPersonality personality) {
        return OntModelFactory.createModel(reassembly((UnionGraph) model.getGraph()), personality);
    }

    public static void close(OntGraphModel o) {
        close((UnionGraph) o.getGraph());
    }

    /**
     * Closes all D2RQ Graphs from a graph-container.
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
     * The result graph has the same structure as specified, but instead a {@link Hybrid hybrid} there are {@link GraphD2RQ d2rq} members.
     *
     * @param graph {@link UnionGraph}
     * @return {@link UnionGraph}
     */
    public static UnionGraph reassembly(UnionGraph graph) {
        UnionGraph res = new UnionGraph(graph.getBaseGraph() instanceof Hybrid ? extractD2RQ(graph.getBaseGraph()) : graph.getBaseGraph());
        graph.getUnderlying().graphs()
                .map(g -> g instanceof Hybrid ? extractD2RQ(g) : g)
                .forEach(g -> res.addGraph(g instanceof UnionGraph ? reassembly((UnionGraph) g) : g));
        return res;
    }

    /**
     * Extracts D2RQ Graph from a graph container, if possible.
     *
     * @param g {@link Graph}
     * @return {@link GraphD2RQ} or {@code null}.
     */
    public static GraphD2RQ extractD2RQ(Graph g) {
        if (g instanceof GraphD2RQ) {
            return (GraphD2RQ) g;
        }
        if (g instanceof Hybrid) {
            return ((Hybrid) g).hidden()
                    .filter(GraphD2RQ.class::isInstance)
                    .map(GraphD2RQ.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Can't find D2RQ Graph"));
        }
        return null;
    }
}
