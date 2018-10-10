package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.graph.Capabilities;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Created by @ssz on 10.10.2018.
 */
public class MaskGraph extends VirtualGraph {
    private final BiPredicate<Graph, Triple> hide;

    public MaskGraph(Graph g, BiPredicate<Graph, Triple> hide) {
        super(g);
        this.hide = Objects.requireNonNull(hide);
    }

    @Override
    public Capabilities getCapabilities() {
        return new CapabilitiesImpl() {
            @Override
            public boolean addAllowed(boolean every) {
                return true;
            }

            @Override
            public boolean deleteAllowed(boolean every) {
                return true;
            }
        };
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        return graph.find(m).filterDrop(t -> hide.test(graph, t));
    }

    @Override
    public void add(Triple t) throws AddDeniedException {
        graph.add(t);
    }

    @Override
    public void delete(Triple t) throws DeleteDeniedException {
        graph.delete(t);
    }
}
