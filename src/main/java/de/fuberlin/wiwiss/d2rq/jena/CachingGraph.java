package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;

/**
 * A {@code CachingGraph} that caches the results of the most recently performed queries on an LRU basis.
 * Notice that it is a read only accessor.
 *
 * @author Holger Knublauch (holger@topquadrant.com)
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class CachingGraph extends GraphBase {

    protected static final List<Triple> OUT_OF_SPACE = new ArrayList<>();

    protected final Cache<Triple, List<Triple>> triples;
    protected final Graph base;
    protected final int maxCacheSize;
    protected final long maxLength;
    protected final int bucketCapacity;
    // atomic, since the concurrent guava cache is used,
    // which, in turn, was chosen since it used by jena (i.e. org.apache.jena.enhanced.EnhGraph)
    protected final LongAdder size;

    /**
     * Creates a caching graph with default settings.
     * Here the length limit is taken equal to {@code 30_000_000} that roughly matches 60mb
     * (grossly believing that a java(8) String consists only of chars).
     *
     * @param base {@link Graph} to wrap, not {@code null}
     */
    public CachingGraph(Graph base) {
        this(Objects.requireNonNull(base), 10_000, 30_000_000);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     *
     * @param base      {@link Graph} to wrap, not {@code null}
     * @param cacheSize int, the cache size
     * @param maxLength long, max number of chars that this cache can hold
     */
    public CachingGraph(Graph base, int cacheSize, long maxLength) {
        this(base, cacheSize, maxLength, maxLength > cacheSize ? (int) (maxLength / cacheSize) : cacheSize);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     * If the queried bunch size is too large to fit in the cache, then an uncached iterator is returned.
     *
     * @param base           {@link Graph} to wrap, not {@code null}
     * @param cacheSize      int, the cache size
     * @param maxLength      long, max number of chars that this cache can hold
     * @param bucketCapacity int, the default bucket capacity
     * @see LengthTripleIterator
     */
    public CachingGraph(Graph base, int cacheSize, long maxLength, int bucketCapacity) {
        this.base = Objects.requireNonNull(base, "Null graph.");
        this.maxCacheSize = requireNonNegative(cacheSize, "Negative cache size");
        this.maxLength = requireNonNegative(maxLength, "Negative max length");
        this.bucketCapacity = requireNonNegative(bucketCapacity, "Negative default bucket size");
        this.size = new LongAdder();
        this.triples = CacheFactory.createCache(cacheSize);
        this.triples.setDropHandler((k, v) -> size.add(-v.size()));
    }

    private static <N extends Number> N requireNonNegative(N n, String msg) {
        if (n.intValue() < 0) {
            throw new IllegalArgumentException(msg);
        }
        return n;
    }

    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple m) {
        List<Triple> res = triples.getIfPresent(m);
        if (OUT_OF_SPACE == res) {
            return base.find(m);
        } else if (res != null) {
            return WrappedIterator.create(res.iterator());
        }
        // do caching:
        LengthTripleIterator bucket = new LengthTripleIterator(base.find(m));
        res = new ArrayList<>(bucketCapacity);
        while (bucket.hasNext()) res.add(bucket.next());
        ((ArrayList<Triple>) res).trimToSize();
        long current = bucket.getLength();
        if (size.longValue() + current < maxLength) {
            triples.put(m, res);
            size.add(current);
        } else {
            // not enough space in the cache
            triples.put(m, OUT_OF_SPACE);
        }
        return WrappedIterator.create(res.iterator());
    }

    /**
     * Clears the current cache.
     * This can be used in case the database has been changed.
     */
    public void clearCache() {
        triples.clear();
    }

    /**
     * A {@link WrappedIterator} that calculates its length while iterating,
     * assuming that "length" of a {@link Triple} is equal (or proportional)
     * the number of characters in its String representation.
     * Language tag for literal nodes is not taken into account.
     * This iterator doesn't permit removing.
     */
    public static class LengthTripleIterator extends WrappedIterator<Triple> {
        private final double uriNodeFactor;
        private final double literalNodeFactor;
        private final double blankNodeFactor;
        private long counter;

        public LengthTripleIterator(Iterator<Triple> base) {
            this(base, 1, 1, 1);
        }

        public LengthTripleIterator(Iterator<Triple> base,
                                    double uriFactor,
                                    double bNodeFactor,
                                    double literalFactor) {
            super(base, true);
            uriNodeFactor = requireNonNegative(uriFactor, "Negative uri node factor");
            blankNodeFactor = requireNonNegative(bNodeFactor, "Negative blank node factor");
            literalNodeFactor = requireNonNegative(literalFactor, "Negative literal node factor");
        }

        @Override
        public Triple next() {
            Triple res = super.next();
            counter += calc(res);
            return res;
        }

        protected long calc(Triple t) {
            return calc(t.getSubject()) + calc(t.getPredicate()) + calc(t.getObject());
        }

        protected long calc(Node n) {
            if (n.isURI()) return calc(n.getURI(), uriNodeFactor);
            if (n.isLiteral()) return calc(n.getLiteral().getLexicalForm(), literalNodeFactor);
            if (n.isBlank()) return calc(n.getBlankNodeLabel(), blankNodeFactor);
            throw new IllegalStateException("Can't calculate node length: " + n);
        }

        public static long calc(String txt, double factor) {
            return (long) (txt.length() * factor);
        }

        public long getLength() {
            return counter;
        }
    }
}
