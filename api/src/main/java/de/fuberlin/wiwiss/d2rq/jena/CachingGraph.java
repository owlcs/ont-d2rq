package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.WrappedIterator;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * A {@code Graph} that caches the results of the most recently performed queries on
 * an LRU basis with fixed length to minimise query calls.
 * Must be thread-safe.
 * Notice that it is a read only accessor.
 *
 * Currently it is experimental optimization.
 *
 * @author Holger Knublauch (holger@topquadrant.com)
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings({"WeakerAccess"})
public class CachingGraph extends GraphBase {

    // value-marker, used to indicate that retrieved Triples set is too large to store in-memory
    protected static final List<Triple> OUT_OF_SPACE = Collections.unmodifiableList(new ArrayList<>());
    // caches, for find and contains operations
    protected final Cache<Triple, List<Triple>> findTriples;
    protected final Cache<Triple, Boolean> containsTriples;

    protected final Graph base;
    // cache parameters:
    protected final int findCacheSize;
    protected final int containsCacheSize;
    protected final long cacheLengthLimit;
    protected final int bucketCapacity;
    protected final ToLongFunction<Triple> lengthCalculator;
    protected final LongAdder size;

    /**
     * Creates a caching graph, that keeps track of its own size.
     * The returned graph has default settings.
     * Here the length limit ({@link #cacheLengthLimit}) is taken equal to {@code 30_000_000}
     * that very roughly matches 60mb
     * (grossly believing that a java(8) {@link String} consists only of chars
     * and {@link Node} memory consumption is equal to the {@code String}).
     * It is enough to restrict uncontrolled increasing of memory usage.
     * Also, the cached queries limit is taken equal to {@code 10_000},
     * which means both caches ({@link #findTriples} and {@link #containsTriples})
     * may have no more than this number items.
     *
     * @param base {@link Graph} to wrap, not {@code null}
     */
    public CachingGraph(Graph base) {
        this(Objects.requireNonNull(base), 10_000, 30_000_000);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     * The {@link #findTriples} cache will have the {@code cacheCapacity} size,
     * and the {@link #containsTriples} will have the half of {@code cacheCapacity} size.
     * This relation is found experimentally,
     * using the spin-map inference with {@code spin:concatWithSeparator} property rule
     * and, also, saving RDF as turtle.
     *
     * @param base             {@link Graph} to wrap, not {@code null}
     * @param cacheCapacity    int, the cache size
     * @param cacheLengthLimit long, max number of chars that this cache can hold
     */
    public CachingGraph(Graph base, int cacheCapacity, long cacheLengthLimit) {
        this(base, createTripleLengthCalculator(), cacheCapacity, cacheCapacity / 2, cacheLengthLimit,
                cacheLengthLimit > cacheCapacity ?
                        (int) (cacheLengthLimit / cacheCapacity) : cacheCapacity);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     * If the queried bunch size is too large to fit in the cache, then an uncached iterator is returned.
     *
     * @param graph                  {@link Graph} to wrap, not {@code null}
     * @param tripleLengthCalculator {@link ToLongFunction} to calculate {@link Triple} "length"
     * @param findCacheSize          int, the find cache size
     * @param containsCacheSize      int, the contains cache size
     * @param cacheLengthLimit       long, max number of chars that this cache can hold
     * @param bucketCapacity         int, the default initial bucket capacity
     */
    public CachingGraph(Graph graph,
                        ToLongFunction<Triple> tripleLengthCalculator,
                        int findCacheSize,
                        int containsCacheSize,
                        long cacheLengthLimit,
                        int bucketCapacity) {
        this.base = Objects.requireNonNull(graph, "Null graph.");
        this.lengthCalculator = Objects.requireNonNull(tripleLengthCalculator, "Null triple length calculator");
        this.findCacheSize = requirePositive(findCacheSize, "Negative find cache size parameter");
        this.containsCacheSize = requirePositive(findCacheSize, "Negative contains cache size parameter");
        this.cacheLengthLimit = requirePositive(cacheLengthLimit, "Negative max length");
        this.bucketCapacity = requirePositive(bucketCapacity, "Negative default bucket size");
        this.size = new LongAdder();
        this.findTriples = CacheFactory.createCache(findCacheSize);
        this.findTriples.setDropHandler((k, v) -> {
            if (v instanceof Bucket)
                size.add(-((Bucket) v).getLength());
        });
        this.containsTriples = CacheFactory.createCache(containsCacheSize);
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
        List<Triple> res = findTriples.getIfPresent(m);
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
            findTriples.put(m, list);
            size.add(current);
        } else {
            // not enough space in the cache
            findTriples.put(m, OUT_OF_SPACE);
        }
        return WrappedIterator.create(list.iterator());
    }

    @Override
    public boolean graphBaseContains(Triple t) {
        return containsTriples.getOrFill(t, () -> base.contains(t));
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
        findTriples.clear();
        containsTriples.clear();
    }

    /**
     * Creates default {@link Triple}s length calculator.
     * All factors equal {@code 1},
     * and skipping the calculation for constants (from OWL, RDF, RDFS and XSD vocabularies}.
     *
     * @return {@link ToLongFunction} for {@link Triple}
     */
    public static ToLongFunction<Triple> createTripleLengthCalculator() {
        Set<String> uris = VocabularySummarizer.resources(OWL.class, RDF.class, RDFS.class, XSD.class)
                .map(Resource::getURI).collect(Collectors.toSet());
        // todo: this is wrong, uri nodes are not cached to be skipped
        return new TripleLength(uris, 1, 1, 1);
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
        private final Set<String> skips;

        public TripleLength(Set<String> skipURIs, double uriFactor, double bNodeFactor, double literalFactor) {
            this.skips = Objects.requireNonNull(skipURIs);
            uriNodeFactor = requirePositive(uriFactor, "Negative uri node factor");
            blankNodeFactor = requirePositive(bNodeFactor, "Negative blank node factor");
            literalNodeFactor = requirePositive(literalFactor, "Negative literal node factor");
        }

        @Override
        public long applyAsLong(Triple t) {
            return calc(t.getSubject()) + calc(t.getPredicate()) + calc(t.getObject());
        }

        protected long calc(Node n) {
            if (n.isURI()) {
                return skips.contains(n.getURI()) ? 0 : calc(n.getURI(), uriNodeFactor);
            }
            if (n.isLiteral()) return calc(n.getLiteral().getLexicalForm(), literalNodeFactor);
            if (n.isBlank()) return calc(n.getBlankNodeLabel(), blankNodeFactor);
            throw new IllegalStateException("Can't calculate node length: " + n);
        }

        public static long calc(String txt, double factor) {
            return (long) (txt.length() * factor);
        }
    }

}
