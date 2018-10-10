package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.Objects;

/**
 * Created by @ssz on 21.09.2018.
 */
public class DynamicGraph extends VirtualGraph {
    private final DynamicTriples triplesFinder;

    public DynamicGraph(Graph g, DynamicTriples f) {
        super(g);
        this.triplesFinder = Objects.requireNonNull(f);
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        return triplesFinder.find(graph, m);
    }

}
