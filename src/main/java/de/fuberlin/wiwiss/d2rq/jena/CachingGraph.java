package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.WrappedGraph;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.List;
import java.util.Objects;

/**
 * A GraphD2RQ that caches the results of the most recently performed queries on an LRU basis.
 * TODO: handle contains, implement caching iterator
 * @author Holger Knublauch (holger@topquadrant.com)
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class CachingGraph extends WrappedGraph {

    protected final Cache<Triple, List<Triple>> triples;

    public CachingGraph(Graph base) {
        this(Objects.requireNonNull(base), 10_000);
    }

    public CachingGraph(Graph base, int maxSize) {
        super(Objects.requireNonNull(base));
        triples = CacheFactory.createCache(maxSize);
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        return withCache(m, super.find(m));
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        return withCache(Triple.create(s, p, o), super.find(s, p, o));
    }

    protected ExtendedIterator<Triple> withCache(Triple m, ExtendedIterator<Triple> res) {
        return WrappedIterator.create(triples.getOrFill(m, res::toList).iterator());
    }

    /**
     * Clears the current cache.
     * This can be used in case the database has been changed.
     */
    public void clearCache() {
        triples.clear();
    }
}
