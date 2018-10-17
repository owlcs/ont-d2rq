package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import org.apache.jena.graph.Graph;

import java.util.Collection;

/**
 * A D2RQ connecting mapping, that is designed to provide a {@link Graph} view of relational databases.
 * Created by @ssz on 17.10.2018.
 */
public interface ConnectingMapping {

    /**
     * Gets set of the compiled property bridges.
     *
     * @return Collection of {@link TripleRelation}s
     */
    Collection<TripleRelation> compiledPropertyBridges();

    /**
     * Connects all databases. This is done automatically if needed.
     * The method can be used to test the connections earlier.
     *
     * @throws D2RQException on connection failure
     */
    void connect() throws D2RQException;

    /**
     * Closes all db connections.
     */
    void close();

    /**
     * Answers whether to use bleeding edge optimizations ({@code false} by default).
     *
     * @return boolean
     */
    boolean withAllOptimizations();

    /**
     * Answers whether to serve inferred and user-supplied vocabulary data ({@code true} by default).
     *
     * @return boolean
     */
    boolean withSchema();

    /**
     * Returns the vocabulary data.
     *
     * @return {@link Graph}
     */
    Graph getSchema();
}
