package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import org.apache.jena.util.iterator.WrappedIterator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * A {@code Graph} that caches the results of the most recently performed queries on
 * an LRU basis with fixed length to minimise query calls.
 * Must be thread-safe.
 * Notice that it is a read only accessor.
 * <p>
 * Currently it is experimental optimization.
 *
 * @author Holger Knublauch (holger@topquadrant.com)
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings({"WeakerAccess"})
public class CachingGraph extends GraphBase {

    // value-marker, used to indicate that retrieved Triples set is too large to store in-memory
    protected static final Bucket OUT_OF_SPACE = new Bucket() {

        @Override
        public String toString() {
            return "OutOfSpace";
        }
    };
    // the base graph
    protected final Graph base;

    // limit of a total sum lengths of all cached queries
    protected final long cacheLengthLimit;
    // limit of a single query length
    protected final long queryLengthLimit;
    // initial bucket capacity
    protected final int bucketCapacity;
    // the current sum of lengths of all cached queries
    protected final LongAdder queriesLength;

    // cache parameters:
    protected final int findCacheSize;
    protected final int containsCacheSize;
    // cache for find operations. use jena cache for some abstract uniformity reasons
    protected final Cache<Triple, Bucket> findCache;
    // cache for contains operation.
    protected final Cache<Triple, Boolean> containsCache;
    // the Map to provide synchronisation when caching
    protected final Map<Triple, Lock> locks = new ConcurrentHashMap<>();
    // a Set of all triplets that definitely cannot fit the cache
    protected final Set<Triple> forbidden = new HashSet<>();
    // a Map with all builtin URIs that do not need caching and can appear in a subject or object position
    protected final Map<Node, Node> resourceVocabulary;
    // a Map with all builtin URIs that do not need caching and can appear in a predicate position
    protected final Map<Node, Node> propertyVocabulary;
    // to calculate the length of URI
    protected final ToLongFunction<Node> uriLengthCalculator;
    // to calculate the length of b-Node
    protected final ToLongFunction<Node> bNodeLengthCalculator;
    // to calculate the length of literal
    protected final ToLongFunction<Node> literalLengthCalculator;

    /**
     * Creates a caching graph, that keeps track of its own size.
     * The returned graph has default settings.
     * Here the length limit ({@link #cacheLengthLimit}) is taken equal to {@code 30_000_000}
     * that very roughly matches 60mb
     * (grossly believing that a java(8) {@link String} consists only of chars
     * and {@link Node} memory consumption is equal to the {@code String}).
     * It is enough to restrict uncontrolled increasing of memory usage.
     * Also, the cached queries limit is taken equal to {@code 10_000},
     * which means both caches ({@link #findCache} and {@link #containsCache})
     * may have no more than this number items.
     *
     * @param base {@link Graph} to wrap, not {@code null}
     */
    public CachingGraph(Graph base) {
        this(Objects.requireNonNull(base), 10_000, 30_000_000);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     * <p>
     * The {@link #findCache} cache will have the {@code cacheCapacity} size,
     * and the {@link #containsCache} will have the half of {@code cacheCapacity} size.
     * This relation is found experimentally,
     * using the spin-map inference with {@code spin:concatWithSeparator} property rule
     * and, also, saving RDF as turtle.
     *
     * @param base                  {@link Graph} to wrap, not {@code null}
     * @param cacheCapacity         int, the cache size
     * @param cacheTotalLengthLimit long, max number of chars that this cache can hold
     */
    public CachingGraph(Graph base, int cacheCapacity, long cacheTotalLengthLimit) {
        this(base,
                VocabularySummarizer.getStandardResources(),
                VocabularySummarizer.getStandardProperties(),
                n -> n.getURI().length(),
                n -> n.getBlankNodeLabel().length(),
                n -> n.getLiteralLexicalForm().length(),
                cacheCapacity,                          // find-cache size
                cacheCapacity / 2,                      // contains-cache size
                cacheTotalLengthLimit,                  // total (sum) length limit
                cacheTotalLengthLimit / 2,              // a query length limit, half of total limit
                cacheTotalLengthLimit > cacheCapacity ? // ArrayList initial capacity
                        (int) (cacheTotalLengthLimit / cacheCapacity) : cacheCapacity);
    }

    /**
     * Creates a caching graph, that keeps track of its own size.
     * This is the base constructor.
     * If the queried bunch size is too large to fit in the cache, then an uncached iterator is returned.
     *
     * @param graph                   {@link Graph} to wrap, not {@code null}
     * @param builtinResources        a {@code Collection} of all builtin {@link Resource}s to skip from length calculation
     * @param builtinProperties       a {@code Collection} of all builtin {@link Property}s to skip from length calculation
     * @param uriLengthCalculator     a URI length calculator
     * @param bNodeLengthCalculator   a b-node length calculator
     * @param literalLengthCalculator a literal calculator
     * @param findCacheSize           int, the find-cache size
     * @param containsCacheSize       int, the contains-cache size
     * @param cacheLengthLimit        long, max number of lengths of all queries that this cache can hold
     * @param queryLengthLimit        long, max number of a query length,
     *                                if a query has length greater then it should not be cached
     * @param bucketCapacity          int, the default initial bucket ({@code ArrayList}) capacity
     */
    protected CachingGraph(Graph graph,
                           Collection<Resource> builtinResources,
                           Collection<Property> builtinProperties,
                           ToLongFunction<Node> uriLengthCalculator,
                           ToLongFunction<Node> bNodeLengthCalculator,
                           ToLongFunction<Node> literalLengthCalculator,
                           int findCacheSize,
                           int containsCacheSize,
                           long cacheLengthLimit,
                           long queryLengthLimit,
                           int bucketCapacity) {
        this.base = Objects.requireNonNull(graph, "Null graph.");
        this.uriLengthCalculator = Objects.requireNonNull(uriLengthCalculator, "Null URI length calculator");
        this.bNodeLengthCalculator = Objects.requireNonNull(bNodeLengthCalculator, "Null Blank Node length calculator");
        this.literalLengthCalculator = Objects.requireNonNull(literalLengthCalculator, "Null Literal length calculator");
        this.resourceVocabulary = asCacheMap(builtinResources);
        this.propertyVocabulary = asCacheMap(builtinProperties);
        this.findCacheSize = requirePositive(findCacheSize, "Negative find cache size parameter");
        this.containsCacheSize = requirePositive(findCacheSize, "Negative contains cache size parameter");
        this.cacheLengthLimit = requirePositive(cacheLengthLimit, "Negative queries max length");
        this.queryLengthLimit = requirePositive(queryLengthLimit, "Negative query max length");
        this.bucketCapacity = requirePositive(bucketCapacity, "Negative default bucket size");
        this.findCache = CacheFactory.createCache(findCacheSize);
        this.containsCache = CacheFactory.createCache(containsCacheSize);
        this.queriesLength = new LongAdder();
        this.findCache.setDropHandler((k, v) -> queriesLength.add(-v.getLength()));
    }

    private static <N extends Number> N requirePositive(N n, String msg) {
        if (n.intValue() <= 0) {
            throw new IllegalArgumentException(msg);
        }
        return n;
    }

    protected static Map<Node, Node> asCacheMap(Collection<? extends Resource> vocabulary) {
        return Collections.unmodifiableMap(Objects.requireNonNull(vocabulary, "Null vocabulary")
                .stream().map(FrontsNode::asNode)
                .collect(Collectors.toMap(Function.identity(), Function.identity())));
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
        ExtendedIterator<Triple> res = fromCache(m);
        if (res != null) {
            return res;
        }
        // use lock per triple pattern
        Lock lock = locks.computeIfAbsent(m, x -> createLock());
        try {
            lock.lock();
            // double checking:
            res = fromCache(m);
            if (res != null) {
                return res;
            }
            // prepare data for caching:
            Bucket list = createTripleBucket();
            Iterator<Triple> it = base.find(m);
            while (it.hasNext()) {
                list.put(it.next());
                // check if there is enough space in the cache
                if (queriesLength.longValue() + list.getLength() > cacheLengthLimit) {
                    // to not even try to put this query into the cache next time
                    if (list.getLength() > queryLengthLimit) {
                        forbidden.add(m);
                    }
                    // or until the value will be invalidated by the LRU basis
                    findCache.put(m, OUT_OF_SPACE);
                    return WrappedIterator.create(list.iterator()).andThen(it);
                }
            }
            queriesLength.add(list.getLength());
            list.trimToSize();
            // do cache:
            findCache.put(m, list);
            return WrappedIterator.create(list.iterator());
        } finally {
            lock.unlock();
            locks.remove(m);
        }
    }

    protected ExtendedIterator<Triple> fromCache(Triple m) {
        if (forbidden.contains(m)) {
            return base.find(m);
        }
        Bucket res = findCache.getIfPresent(m);
        if (OUT_OF_SPACE == res) {
            return base.find(m);
        } else if (res != null) {
            return WrappedIterator.create(res.iterator());
        }
        return null;
    }

    protected Bucket createTripleBucket() {
        return new BucketImpl(bucketCapacity, resourceVocabulary, propertyVocabulary,
                uriLengthCalculator, bNodeLengthCalculator, literalLengthCalculator);
    }

    protected Lock createLock() {
        return new ReentrantLock();
    }

    @Override
    public boolean graphBaseContains(Triple t) {
        return containsCache.getOrFill(t, () -> base.contains(t));
    }

    /**
     * Clears the current cache.
     * This can be used in case the database has been changed.
     */
    @SuppressWarnings("unused")
    public void clearCache() {
        findCache.clear();
        containsCache.clear();
    }

    @Override
    public void close() {
        clearCache();
        base.close();
        super.close();
    }

    @Override
    public String toString() {
        return String.format("CachingGraph{queries-length=%s}{base=%s}", queriesLength, base);
    }

    /**
     * An abstract {@link Triple triple} container, that is used as value in the find-cache.
     */
    public interface Bucket {

        default void put(Triple t) {
            throw new IllegalStateException("Attempt to put " + t);
        }

        default long getLength() {
            return 0;
        }

        default int size() {
            return 0;
        }

        default Iterator<Triple> iterator() {
            return NullIterator.instance();
        }

        default void trimToSize() {
            // nothing
        }
    }

    /**
     * Default {@link ArrayList Array-based} implementation of {@link Bucket}.
     */
    public static class BucketImpl extends ArrayList<Triple> implements Bucket {
        protected final Map<Node, Node> resourceMap;
        protected final Map<Node, Node> propertyMap;
        protected final ToLongFunction<Node> uriLength;
        protected final ToLongFunction<Node> bNodeLength;
        protected final ToLongFunction<Node> literalLength;
        private long length;

        protected BucketImpl(int initialCapacity,
                             Map<Node, Node> resources,
                             Map<Node, Node> properties,
                             ToLongFunction<Node> uriLength,
                             ToLongFunction<Node> bNodeLength,
                             ToLongFunction<Node> literalLength) {
            super(initialCapacity);
            this.resourceMap = Objects.requireNonNull(resources);
            this.propertyMap = Objects.requireNonNull(properties);
            this.uriLength = Objects.requireNonNull(uriLength);
            this.bNodeLength = Objects.requireNonNull(bNodeLength);
            this.literalLength = Objects.requireNonNull(literalLength);
        }

        @Override
        public void put(Triple t) {
            Node s = t.getSubject();
            Node p = t.getPredicate();
            Node o = t.getObject();
            if (s.isURI()) {
                Node replace = resourceMap.get(s);
                if (replace != null) {
                    s = replace;
                    t = null;
                } else {
                    length += uriLength.applyAsLong(s);
                }
            } else if (s.isBlank()) {
                length += bNodeLength.applyAsLong(s);
            } else {
                throw new IllegalStateException("Unexpected subject for " + t);
            }
            if (p.isURI()) {
                Node replace = propertyMap.get(p);
                if (replace != null) {
                    p = replace;
                    t = null;
                } else {
                    length += uriLength.applyAsLong(p);
                }
            } else {
                throw new IllegalStateException("Unexpected predicate for " + t);
            }
            if (o.isURI()) {
                Node replace = resourceMap.get(o);
                if (replace != null) {
                    o = replace;
                    t = null;
                } else {
                    length += uriLength.applyAsLong(o);
                }
            } else if (o.isBlank()) {
                length += bNodeLength.applyAsLong(o);
            } else if (o.isLiteral()) {
                length += literalLength.applyAsLong(o);
            } else {
                throw new IllegalStateException("Unexpected object for " + t);
            }
            if (t == null) {
                t = Triple.create(s, p, o);
            }
            super.add(t);
        }

        @Override
        public long getLength() {
            return length;
        }

        @Override
        public String toString() {
            return String.format("Bucket{length=%d}{super=%s}", length, super.toString());
        }
    }

}