package ru.avicomp.ontapi.jena;

import org.apache.jena.graph.Graph;

import java.util.stream.Stream;

/**
 * This is an interface to a 'hybrid' graph which wraps a collection of true graphs.
 * It was introduced to have possibility to change behaviour dynamically
 * by switching to the specified graph from the inner collection inside implementation.
 * Also it can be used as common wrapper just with additional access to the other graphs,
 * and it seems this last way is preferable as it is safe.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public interface Hybrid extends Graph {

    /**
     * TODO: should not be possible to change state, otherwise the overlying data may be corrupted.
     * switches to the specified graph.
     *
     * @param graph {@link Graph} to switch
     * @return the previous primary {@link Graph}
     * @throws OntJenaException if there is no specified graph inside.
     */
    @Deprecated
    Graph switchTo(Graph graph);

    /**
     * returns the current(primary) graph.
     *
     * @return {@link Graph}
     */
    Graph get();

    /**
     * returns all graphs from this container.
     *
     * @return Stream of {@link Graph}s.
     */
    Stream<Graph> graphs();
}
