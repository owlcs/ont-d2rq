package ru.avicomp.ontapi.jena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.graph.*;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;

/**
 * Implementation of a {@link Hybrid} graph.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public class HybridImpl implements Hybrid {
    private final List<Graph> graphs;
    private int cursor;

    public HybridImpl(Collection<Graph> graphs) {
        ArrayList<Graph> _graphs = OntJenaException.notNull(graphs, "Null graphs collection.")
                .stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
        if (_graphs.isEmpty()) {
            throw new OntJenaException("Empty graphs collection.");
        }
        this.graphs = _graphs;
    }

    @Override
    public Graph switchTo(Graph graph) {
        if (!graphs.contains(OntJenaException.notNull(graph, "Null graph."))) {
            throw new OntJenaException("Unable to find the specified graph.");
        }
        Graph current = get();
        for (int i = 0; i < graphs.size(); i++) {
            if (graph.equals(graphs.get(i))) {
                cursor = i;
                break;
            }
        }
        return current;
    }

    @Override
    public Graph get() {
        return graphs.get(cursor);
    }

    @Override
    public Stream<Graph> graphs() {
        return graphs.stream();
    }

    @Override
    public boolean dependsOn(Graph other) {
        return get().dependsOn(other);
    }

    @Override
    public TransactionHandler getTransactionHandler() {
        return get().getTransactionHandler();
    }

    @Override
    public Capabilities getCapabilities() {
        return get().getCapabilities();
    }

    @Override
    public GraphEventManager getEventManager() {
        return get().getEventManager();
    }

    @Override
    public GraphStatisticsHandler getStatisticsHandler() {
        return get().getStatisticsHandler();
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return get().getPrefixMapping();
    }

    @Override
    public void add(Triple t) throws AddDeniedException {
        get().add(t);
    }

    @Override
    public void delete(Triple t) throws DeleteDeniedException {
        get().delete(t);
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        return get().find(m);
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        return get().find(s, p, o);
    }

    @Override
    public boolean isIsomorphicWith(Graph g) {
        return get().isIsomorphicWith(g);
    }

    @Override
    public boolean contains(Node s, Node p, Node o) {
        return get().contains(s, p, o);
    }

    @Override
    public boolean contains(Triple t) {
        return get().contains(t);
    }

    @Override
    public void clear() {
        get().clear();
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        get().remove(s, p, o);
    }

    @Override
    public void close() {
        get().close();
    }

    @Override
    public boolean isEmpty() {
        return get().isEmpty();
    }

    @Override
    public int size() {
        return get().size();
    }

    @Override
    public boolean isClosed() {
        return get().isClosed();
    }

}
