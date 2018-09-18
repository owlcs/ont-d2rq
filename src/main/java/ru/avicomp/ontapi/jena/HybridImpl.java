package ru.avicomp.ontapi.jena;

import org.apache.jena.graph.*;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of a {@link Hybrid} graph.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
@SuppressWarnings("WeakerAccess")
public class HybridImpl implements Hybrid {
    private final List<Graph> hidden;
    private final Graph primary;

    public HybridImpl(Graph primary, Collection<Graph> other) {
        this.primary = Objects.requireNonNull(primary, "Null primary graph");
        this.hidden = Objects.requireNonNull(other, "Null graphs collection.")
                .stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    public HybridImpl(Graph primary, Graph other) {
        this(primary, Collections.singleton(other));
    }

    @Override
    public Graph get() {
        return primary;
    }

    @Override
    public Stream<Graph> hidden() {
        return hidden.stream();
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
