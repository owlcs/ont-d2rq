package ru.avicomp.ontapi.jena;

import org.apache.jena.graph.Graph;

import java.util.stream.Stream;

/**
 * This is an interface to a "hybrid" graph,
 * which is simply a wrapper for the primary graph with a reference to the collection of hidden graphs.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public interface Hybrid extends Graph {

    /**
     * Returns the primary graph.
     *
     * @return {@link Graph}
     */
    Graph get();


    /**
     * Returns attached hidden graph collection.
     *
     * @return Stream of graphs.
     */
    Stream<Graph> hidden();

    /**
     * Returns all graphs from this container.
     *
     * @return Stream of {@link Graph}s.
     */
    default Stream<Graph> graphs() {
        return Stream.concat(Stream.of(get()), hidden());
    }
}
