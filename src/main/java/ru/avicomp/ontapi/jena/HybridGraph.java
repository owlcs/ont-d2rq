package ru.avicomp.ontapi.jena;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.impl.WrappedGraph;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is a "hybrid" graph,
 * that is simply a wrapper for the primary base graph with a reference to the collection of hidden graphs.
 * The idea is to have all references in one place,
 * which can be useful if the schema and data are of different nature and need to be controlled separately.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
@SuppressWarnings("WeakerAccess")
public class HybridGraph extends WrappedGraph implements Graph {
    private final List<Graph> hidden;

    public HybridGraph(Graph primary, Graph other) {
        this(primary, Collections.singleton(other));
    }

    public HybridGraph(Graph primary, Collection<Graph> other) {
        super(Objects.requireNonNull(primary, "Null primary graph"));
        this.hidden = Objects.requireNonNull(other, "Null graphs collection.")
                .stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /**
     * Lists all hidden graphs.
     *
     * @return Stream of {@link Graph}s
     */
    public Stream<Graph> hidden() {
        return hidden.stream();
    }

    /**
     * Lists all graphs from this container.
     *
     * @return Stream of {@link Graph}s
     */
    public Stream<Graph> graphs() {
        return Stream.concat(Stream.of(getWrapped()), hidden());
    }

}
