package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.List;
import java.util.Objects;

/**
 * A GraphD2RQ that caches the results of the most recently performed queries on an LRU basis.
 * Notice that it is a read only accessor.
 * TODO: implement caching iterator
 * @author Holger Knublauch (holger@topquadrant.com)
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class CachingGraph extends GraphBase {

    protected final Cache<Triple, List<Triple>> triples;
    protected final Graph base;

    public CachingGraph(Graph base) {
        this(Objects.requireNonNull(base), 10_000);
    }

    public CachingGraph(Graph base, int maxSize) {
        this.base = Objects.requireNonNull(base, "Null graph");
        triples = CacheFactory.createCache(maxSize);
    }

    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple m) {
        return WrappedIterator.create(triples.getOrFill(m, () -> base.find(m).toList()).iterator());
    }

    /**
     * Clears the current cache.
     * This can be used in case the database has been changed.
     */
    public void clearCache() {
        triples.clear();
    }
}
