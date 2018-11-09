package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToLongFunction;

/**
 * A {@code Graph} that caches the results of the most recently performed queries on
 * an LRU basis with fixed length to minimise query calls.
 * Must be thread-safe.
 * Notice that it is a read only accessor.
 *
 * @author Holger Knublauch (holger@topquadrant.com)
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings({"WeakerAccess"})
public class CachingGraph extends GraphBase {

    // value-marker
    protected static final List<Triple> OUT_OF_SPACE = new ArrayList<>();
    // cache
    protected final Cache<Triple, List<Triple>> triples;
    protected final Graph base;
    // cache parameters:
    protected final int cacheMaxSize;
    protected final long cacheLengthLimit;
    protected final int bucketCapacity;
    protected final ToLongFunction<Triple> lengthCalculator;
    protected final LongAdder size;

    /**
     * Creates a caching graph, that keeps track of its own size.
     * The graph has default settings.
     * Here the length limit ({@link #cacheLengthLimit}) is taken equal to {@code 30_000_000} that roughly matches 60mb
     * (grossly believing that a java(8) String consists only of chars)
     * and the cached queries limit ({@link #cacheMaxSize}) is {@code 10_000}.
     *
     * @param base {@link Graph} to wrap, not {@code null}
     */
    public CachingGraph(Graph base) {
        this(Objects.requireNonNull(base), 10_000, 30_000_000);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     *
     * @param base             {@link Graph} to wrap, not {@code null}
     * @param cacheMaxSize     int, the cache size
     * @param cacheLengthLimit long, max number of chars that this cache can hold
     */
    public CachingGraph(Graph base, int cacheMaxSize, long cacheLengthLimit) {
        this(base, new TripleLength(), cacheMaxSize, cacheLengthLimit,
                cacheLengthLimit > cacheMaxSize ? (int) (cacheLengthLimit / cacheMaxSize) : cacheMaxSize);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     * If the queried bunch size is too large to fit in the cache, then an uncached iterator is returned.
     *
     * @param graph                  {@link Graph} to wrap, not {@code null}
     * @param tripleLengthCalculator {@link ToLongFunction} to calculate {@link Triple} "length"
     * @param cacheMaxSize           int, the cache size
     * @param cacheLengthLimit       long, max number of chars that this cache can hold
     * @param bucketCapacity         int, the default initial bucket capacity
     */
    protected CachingGraph(Graph graph,
                           ToLongFunction<Triple> tripleLengthCalculator,
                           int cacheMaxSize,
                           long cacheLengthLimit,
                           int bucketCapacity) {
        this.base = Objects.requireNonNull(graph, "Null graph.");
        this.lengthCalculator = Objects.requireNonNull(tripleLengthCalculator, "Null triple length calculator");
        this.cacheMaxSize = requirePositive(cacheMaxSize, "Negative cache size");
        this.cacheLengthLimit = requirePositive(cacheLengthLimit, "Negative max length");
        this.bucketCapacity = requirePositive(bucketCapacity, "Negative default bucket size");
        this.size = new LongAdder();
        this.triples = CacheFactory.createCache(cacheMaxSize);
        this.triples.setDropHandler((k, v) -> {
            if (v instanceof Bucket)
                size.add(-((Bucket) v).getLength());
        });
    }

    private static <N extends Number> N requirePositive(N n, String msg) {
        if (n.intValue() <= 0) {
            throw new IllegalArgumentException(msg);
        }
        return n;
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return base.getPrefixMapping();
    }

    /**
     * Returns the wrapped graph.
     *
     * @return {@link Graph}
     */
    public Graph getBase() {
        return base;
    }

    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple m) {
        List<Triple> res = triples.getIfPresent(m);
        if (OUT_OF_SPACE == res) {
            return base.find(m);
        } else if (res != null) {
            return WrappedIterator.create(res.iterator());
        }
        // prepare data for caching:
        Bucket list = new Bucket();
        Iterator<Triple> it = base.find(m);
        while (it.hasNext()) list.add(it.next());
        list.trimToSize();
        // put into cache:
        long current = list.getLength();
        if (size.longValue() + current < cacheLengthLimit) {
            triples.put(m, list);
            size.add(current);
        } else {
            // not enough space in the cache
            triples.put(m, OUT_OF_SPACE);
        }
        return WrappedIterator.create(list.iterator());
    }

    /**
     * A {@link Triple triple} container, that is used as value in the cache.
     */
    public class Bucket extends ArrayList<Triple> {
        private long length;

        public Bucket() {
            super(bucketCapacity);
        }

        public boolean add(Triple t) {
            length += lengthCalculator.applyAsLong(t);
            return super.add(t);
        }

        public long getLength() {
            return length;
        }
    }

    /**
     * Clears the current cache.
     * This can be used in case the database has been changed.
     */
    @SuppressWarnings("unused")
    public void clearCache() {
        triples.clear();
    }

    /**
     * A class-helper to calculate {@link Triple} "length",
     * assuming it is equal (or proportional to) the number of characters in a {@code Triple} String representation.
     * Language tag for literal nodes is not taken into account.
     */
    public static class TripleLength implements ToLongFunction<Triple> {
        private final double uriNodeFactor;
        private final double literalNodeFactor;
        private final double blankNodeFactor;

        public TripleLength() {
            this(1, 1, 1);
        }

        public TripleLength(double uriFactor, double bNodeFactor, double literalFactor) {
            uriNodeFactor = requirePositive(uriFactor, "Negative uri node factor");
            blankNodeFactor = requirePositive(bNodeFactor, "Negative blank node factor");
            literalNodeFactor = requirePositive(literalFactor, "Negative literal node factor");
        }

        @Override
        public long applyAsLong(Triple t) {
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
    }

}
