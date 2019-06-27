package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.vocab.VocabularySummarizer;
import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.atlas.lib.CacheFactory;
import org.apache.jena.graph.FrontsNode;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.GraphBase;
import org.apache.jena.graph.impl.GraphWithPerform;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;
import ru.avicomp.ontapi.jena.utils.Iter;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

/**
 * A {@code Graph} that caches the results of the most recently performed queries on
 * an LRU basis to minimise query calls.
 * The graph keeps track of its own size, any cache operation is performed under control.
 * Must be thread-safe, locking is performed per triple pattern.
 * Notice that it is a read only accessor: any mutation is prohibited
 * and an attempt to modify the graph will lead to {@link org.apache.jena.shared.JenaException}.
 * Also note that the external changing of underlying (DB) data may lead to graph inconsistently.
 * <p>
 * Currently it is an experimental optimization.
 * See also {@code ru.avicomp.d2rq.InferenceStrategies} -
 * a minimal set of tests to compare and measure performance while graph inference.
 * <p>
 * It is a former <a href='https://github.com/d2rq/d2rq/blob/master/src/de/fuberlin/wiwiss/d2rq/jena/CachingGraphD2RQ.java'>de.fuberlin.wiwiss.d2rq.jena.CachingGraphD2RQ</a>
 *
 * @author Holger Knublauch (holger@topquadrant.com)
 * <p>
 * Created by @ssz on 27.10.2018.
 */
@SuppressWarnings({"WeakerAccess"})
public class CachingGraph extends GraphBase {

    // value-marker, used to indicate that retrieved Triples Set is too large to be stored in-memory
    protected static final Bucket OUT_OF_SPACE = new EmptyBucket() {

        @Override
        public ExtendedIterator<Triple> iterator() {
            throw new IllegalStateException("OutOfSpace: attempt to iterate.");
        }

        @Override
        public String toString() {
            return "OutOfSpace";
        }
    };
    // the base graph
    protected final Graph base;

    // limit of total lengths sum of all cached chains
    protected final long cacheLengthLimit;
    // limit of single chain length
    protected final long queryLengthLimit;
    // the current sum of lengths of all cached chains
    protected final LongAdder queriesLength;

    // cache parameters:
    protected final int findCacheSize;
    protected final int containsCacheSize;
    // cache for find operations.
    protected final CacheMap<Triple, Bucket> findCache;
    // cache for contains operation.
    protected final CacheMap<Triple, Boolean> containsCache;
    // a Set of all triplet-patters for which result chains definitely cannot fit in the main cache
    // it is decorated as a cache to prevent uncontrolled increase its size for cahing graph with small limit
    protected final CacheSet<Triple> tooLongChains;
    // the Map to provide synchronisation when caching in find
    protected final Map<Triple, Lock> locks = new ConcurrentHashMap<>();
    // a Map with all builtin URIs that may appear in a subject or an object position,
    // to skip during chain length calculation
    protected final Map<String, Node> resourceVocabulary;
    // a Map with all builtin URIs that may appear in a predicate position, to skip during chain length calculation
    protected final Map<String, Node> propertyVocabulary;
    // A calculator to derive the length of URI-Node
    protected final ToLongFunction<Node> uriLengthCalculator;
    // A calculator to derive the length of b-Node
    protected final ToLongFunction<Node> bNodeLengthCalculator;
    // A calculator to derive the length of literal-Node
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
                cacheTotalLengthLimit / 2              // a query length limit, half of total limit
        );
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
                           long queryLengthLimit) {
        this.base = Objects.requireNonNull(graph, "Null graph.");
        this.uriLengthCalculator = Objects.requireNonNull(uriLengthCalculator, "Null URI length calculator");
        this.bNodeLengthCalculator = Objects.requireNonNull(bNodeLengthCalculator, "Null Blank Node length calculator");
        this.literalLengthCalculator = Objects.requireNonNull(literalLengthCalculator, "Null Literal length calculator");
        this.resourceVocabulary = toNodesMap(builtinResources);
        this.propertyVocabulary = toNodesMap(builtinProperties);
        this.findCacheSize = requirePositive(findCacheSize, "Negative find cache size parameter");
        this.containsCacheSize = requirePositive(findCacheSize, "Negative contains cache size parameter");
        this.cacheLengthLimit = requirePositive(cacheLengthLimit, "Negative queries max length");
        this.queryLengthLimit = requirePositive(queryLengthLimit, "Negative query max length");
        this.queriesLength = new LongAdder();
        this.findCache = createCacheMap(findCacheSize, (k, v) -> queriesLength.add(-v.getLength()));
        this.containsCache = createCacheMap(containsCacheSize, null);
        this.tooLongChains = createCacheSet(findCacheSize);
    }

    private static <N extends Number> N requirePositive(N n, String msg) {
        if (n.intValue() <= 0) {
            throw new IllegalArgumentException(msg);
        }
        return n;
    }

    /**
     * Turns the given collection of {@link Resource}s
     * into an unmodifiable {@code Map} with URIs as keys and {@link Node}s as values.
     *
     * @param vocabulary {@code Collection}
     * @return {@code Map}
     * @throws RuntimeException in case the input contains {@code null}s or anonymous resources
     */
    public static Map<String, Node> toNodesMap(Collection<? extends Resource> vocabulary) {
        return Collections.unmodifiableMap(Objects.requireNonNull(vocabulary, "Null vocabulary")
                .stream()
                .peek(r -> {
                    if (!Objects.requireNonNull(r).isURIResource()) {
                        throw new IllegalArgumentException("Not uri: " + r);
                    }
                })
                .map(FrontsNode::asNode)
                .distinct()
                .collect(Collectors.toMap(Node::getURI, Function.identity())));
    }

    private static boolean isValid(Bucket bucket) {
        return bucket != null && bucket != OUT_OF_SPACE;
    }

    /**
     * A factory method to produce customized cache-set.
     *
     * @param size maxSize
     * @param <K>  key
     * @return {@link CacheSet}
     */
    protected <K> CacheSet<K> createCacheSet(int size) {
        org.apache.jena.atlas.lib.CacheSet<K> res = CacheFactory.createCacheSet(size);
        return new CacheSet<K>() {
            @Override
            public void add(K k) {
                res.add(k);
            }

            @Override
            public boolean contains(K k) {
                return res.contains(k);
            }

            @Override
            public long size() {
                return res.size();
            }

            @Override
            public void clear() {
                res.clear();
            }
        };
    }

    /**
     * A factory method to produce customized cache-map.
     * Can be overridden to use some other cache vendor.
     * Currently a Jena Guava is used, since, it seems, it does not matter what cache is used.
     *
     * @param size            maxSize
     * @param removalListener {@code BiConsumer}, can be {@code null}, called when an object is dropped from the cache
     * @param <K>             key
     * @param <V>             value
     * @return {@link CacheMap}
     */
    protected <K, V> CacheMap<K, V> createCacheMap(int size, BiConsumer<K, V> removalListener) {
        Cache<K, V> res = CacheFactory.createCache(size);
        if (removalListener != null) {
            res.setDropHandler(removalListener);
        }
        return new CacheMap<K, V>() {
            @Override
            public void put(K k, V v) {
                res.put(k, v);
            }

            @Override
            public Iterator<K> keys() {
                return res.keys();
            }

            @Override
            public V getOrFill(K key, Callable<V> callable) {
                return res.getOrFill(key, callable);
            }

            @Override
            public V get(K key) {
                return res.getIfPresent(key);
            }

            @Override
            public long size() {
                return res.size();
            }

            @Override
            public void clear() {
                res.clear();
            }
        };
    }

    @Override
    public PrefixMapping getPrefixMapping() {
        return base.getPrefixMapping();
    }

    /**
     * Returns the wrapped base graph.
     *
     * @return {@link Graph}
     */
    public Graph getBase() {
        return base;
    }

    @Override
    public ExtendedIterator<Triple> graphBaseFind(Triple m) {
        ExtendedIterator<Triple> res = findIterator(m);
        if (res != null) {
            return res;
        }
        // use lock per triple pattern
        Lock lock = locks.computeIfAbsent(m, x -> createLock());
        try {
            lock.lock();
            // double checking:
            res = findIterator(m);
            if (res != null) {
                return res;
            }
            // prepare data for caching:
            Bucket list = createTripleBucket(m);
            Iterator<Triple> it = base.find(m);
            while (it.hasNext()) {
                list.put(it.next());
                // check if there is enough space in the cache
                if (queriesLength.longValue() + list.getLength() > cacheLengthLimit) {
                    // to not even try to put this query into the cache next time
                    if (list.getLength() > queryLengthLimit) {
                        tooLongChains.add(m);
                    }
                    // or until the value will be invalidated by the LRU basis
                    findCache.put(m, OUT_OF_SPACE);
                    return list.iterator().andThen(it);
                }
            }
            queriesLength.add(list.getLength());
            list.flush();
            // do cache:
            findCache.put(m, list);
            return list.iterator();
        } finally {
            lock.unlock();
            locks.remove(m);
        }
    }

    @Override
    public boolean graphBaseContains(Triple m) {
        return containsCache.getOrFill(m, () -> {
            if (tooLongChains.contains(m)) {
                return true;
            }
            Bucket res = findCache.get(m);
            if (OUT_OF_SPACE == res) {
                return base.contains(m);
            }
            if (res != null) {
                return res.size() != 0;
            }
            res = findBucket(m);
            if (res != null) {
                return res.contains(m);
            }
            return containsByFind(m);
        });
    }

    /**
     * Finds a {@link ExtendedIterator} by the {@link Triple} pattern.
     *
     * @param m {@link Triple} to search, not {@code null}
     * @return {@link ExtendedIterator} of {@link Triple}s or {@code null}
     */
    protected ExtendedIterator<Triple> findIterator(Triple m) {
        if (tooLongChains.contains(m)) {
            return base.find(m);
        }
        Bucket res = findCache.get(m);
        if (OUT_OF_SPACE == res) {
            return base.find(m);
        } else if (res != null) {
            return res.iterator();
        }

        res = findBucket(m);
        if (res != null) {
            return res.iterator(m);
        }
        return null;
    }

    /**
     * Finds a {@link Bucket} by the {@link Triple} pattern.
     *
     * @param m {@link Triple} to search, not {@code null}
     * @return {@link Bucket} or {@code null}
     */
    protected Bucket findBucket(Triple m) {
        Bucket res = findCache.get(Triple.ANY);
        if (isValid(res)) {
            return res;
        }
        if (Triple.ANY.equals(m)) {
            return null;
        }

        Node s = m.getSubject();
        Node p = m.getPredicate();
        Node o = m.getObject();

        res = findCache.get(Triple.createMatch(s, Node.ANY, Node.ANY));
        if (isValid(res)) {
            return res;
        }
        res = findCache.get(Triple.createMatch(Node.ANY, p, Node.ANY));
        if (isValid(res)) {
            return res;
        }
        res = findCache.get(Triple.createMatch(Node.ANY, Node.ANY, o));
        if (isValid(res)) {
            return res;
        }

        res = findCache.get(Triple.createMatch(s, p, Node.ANY));
        if (isValid(res)) {
            return res;
        }
        res = findCache.get(Triple.createMatch(s, Node.ANY, o));
        if (isValid(res)) {
            return res;
        }
        res = findCache.get(Triple.createMatch(Node.ANY, p, o));
        if (isValid(res)) {
            return res;
        }
        return null;
    }

    /**
     * Creates a {@link Bucket} instance.
     *
     * @param m {@link Triple}, not {@code null}
     * @return {@link Bucket}
     */
    protected Bucket createTripleBucket(Triple m) {
        if (m.isConcrete()) {
            // small array for a concrete triple search results,
            // for a distinct graph it should contain only one item:
            return new ArrayBucketImpl(1, resourceVocabulary, propertyVocabulary,
                    uriLengthCalculator, bNodeLengthCalculator, literalLengthCalculator);
        }
        return new GraphBucketImpl(new GraphMem(), resourceVocabulary, propertyVocabulary,
                uriLengthCalculator, bNodeLengthCalculator, literalLengthCalculator);
    }

    /**
     * A factory-method to create lock instance.
     *
     * @return {@link Lock}
     */
    protected Lock createLock() {
        return new ReentrantLock();
    }

    /**
     * Clears the current cache.
     * This can be used in case the database has been changed.
     */
    @SuppressWarnings("unused")
    public void clearCache() {
        findCache.clear();
        containsCache.clear();
        tooLongChains.clear();
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

        /**
         * Puts triple into this bucket.
         *
         * @param t {@link Triple}
         */
        void put(Triple t);

        /**
         * Gets the bucket current length.
         *
         * @return long
         */
        long getLength();

        /**
         * Answers an iterator over all content.
         *
         * @return {@link ExtendedIterator} of {@link Triple}s
         */
        ExtendedIterator<Triple> iterator();

        /**
         * Returns the count of cached triples.
         *
         * @return int
         */
        default int size() {
            return (int) Iter.asStream(iterator()).count();
        }

        /**
         * Answers an iterator over the content selected by the {@code SPO} pattern.
         *
         * @param m {@link Triple}, not {@code null}
         * @return {@link ExtendedIterator} of {@link Triple}s
         */
        default ExtendedIterator<Triple> iterator(Triple m) {
            return iterator().filterKeep(m::matches);
        }

        /**
         * Answers {@code true} if this triple contains the specified {@link Triple} pattern.
         *
         * @param m {@link Triple}, not {@code null}
         * @return boolean
         */
        default boolean contains(Triple m) {
            return Iter.findFirst(iterator(m)).isPresent();
        }

        /**
         * Performs some final actions to release the memory.
         */
        default void flush() {
            // nothing
        }
    }

    /**
     * A simple abstract cache-map adapter.
     *
     * @param <K> anything, key-type
     * @param <V> anything, value-type
     */
    public interface CacheMap<K, V> {

        void put(K k, V v);

        Iterator<K> keys();

        V getOrFill(K key, Callable<V> callable);

        V get(K key);

        long size();

        void clear();
    }

    /**
     * A simple abstract cache-set adapter.
     *
     * @param <K> anything, key-type
     */
    public interface CacheSet<K> {
        void add(K k);

        boolean contains(K k);

        long size();

        void clear();
    }

    /**
     * A {@link Bucket} impl, that does not contain anything.
     */
    public static class EmptyBucket implements Bucket {

        @Override
        public void put(Triple t) {
            throw new UnsupportedOperationException("Attempt to put " + t);
        }

        @Override
        public long getLength() {
            return 0;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public ExtendedIterator<Triple> iterator() {
            return NullIterator.instance();
        }
    }

    /**
     * A {@link GraphWithPerform}-based bucket.
     */
    public static class GraphBucketImpl extends BaseBucketImpl implements Bucket {
        protected final GraphWithPerform graph;

        protected GraphBucketImpl(GraphWithPerform graph,
                                  Map<String, Node> resources,
                                  Map<String, Node> properties,
                                  ToLongFunction<Node> uriLength,
                                  ToLongFunction<Node> bNodeLength,
                                  ToLongFunction<Node> literalLength) {
            super(resources, properties, uriLength, bNodeLength, literalLength);
            this.graph = Objects.requireNonNull(graph);
        }

        @Override
        public void put(Triple t) {
            graph.performAdd(update(t));
        }

        @Override
        public int size() {
            return graph.size();
        }

        @Override
        public ExtendedIterator<Triple> iterator() {
            return graph.find();
        }

        @Override
        public ExtendedIterator<Triple> iterator(Triple m) {
            return graph.find(m);
        }

        @Override
        public boolean contains(Triple m) {
            return graph.contains(m);
        }

        @Override
        public void flush() {
            super.flush();
        }
    }

    /**
     * Default {@link ArrayList Array-based} implementation of {@link Bucket}.
     */
    @SuppressWarnings("unused")
    public static class ArrayBucketImpl extends BaseBucketImpl implements Bucket {
        protected final ArrayList<Triple> array;

        protected ArrayBucketImpl(int initialCapacity,
                                  Map<String, Node> resources,
                                  Map<String, Node> properties,
                                  ToLongFunction<Node> uriLength,
                                  ToLongFunction<Node> bNodeLength,
                                  ToLongFunction<Node> literalLength) {
            super(resources, properties, uriLength, bNodeLength, literalLength);
            this.array = new ArrayList<>(initialCapacity);
        }

        @Override
        public void put(Triple t) {
            array.add(update(t));
        }

        @Override
        public int size() {
            return array.size();
        }

        @Override
        public ExtendedIterator<Triple> iterator() {
            return Iter.create(array);
        }

        @Override
        public void flush() {
            array.trimToSize();
            super.flush();
        }
    }

    /**
     * Base impl.
     */
    protected static abstract class BaseBucketImpl {
        protected final Map<String, Node> standardResources;
        protected final Map<String, Node> standardProperties;

        protected final ToLongFunction<Node> uriLength;
        protected final ToLongFunction<Node> bNodeLength;
        protected final ToLongFunction<Node> literalLength;

        protected final Map<String, Node> uriCache = new HashMap<>();
        protected final Map<String, Node> bnodeCache = new HashMap<>();
        private long length;

        protected BaseBucketImpl(Map<String, Node> resources,
                                 Map<String, Node> properties,
                                 ToLongFunction<Node> uriLength,
                                 ToLongFunction<Node> bNodeLength,
                                 ToLongFunction<Node> literalLength) {
            this.standardResources = Objects.requireNonNull(resources);
            this.standardProperties = Objects.requireNonNull(properties);
            this.uriLength = Objects.requireNonNull(uriLength);
            this.bNodeLength = Objects.requireNonNull(bNodeLength);
            this.literalLength = Objects.requireNonNull(literalLength);
        }

        public Triple update(final Triple t) {
            Node s = t.getSubject();
            Node p = t.getPredicate();
            Node o = t.getObject();
            boolean createNewTriple = false;
            if (s.isURI()) {
                Node replace = updateURI(s, standardResources, uriCache);
                if (replace != null) {
                    s = replace;
                    createNewTriple = true;
                }
            } else if (s.isBlank()) {
                Node replace = updateBNode(s, bnodeCache);
                if (replace != null) {
                    s = replace;
                    createNewTriple = true;
                }
            } else {
                throw new IllegalStateException("Unexpected subject for " + t);
            }
            if (p.isURI()) {
                Node replace = updateURI(p, standardProperties, uriCache);
                if (replace != null) {
                    p = replace;
                    createNewTriple = true;
                }
            } else {
                throw new IllegalStateException("Unexpected predicate for " + t);
            }
            if (o.isURI()) {
                Node replace = updateURI(o, standardResources, uriCache);
                if (replace != null) {
                    o = replace;
                    createNewTriple = true;
                }
            } else if (o.isBlank()) {
                Node replace = updateBNode(o, bnodeCache);
                if (replace != null) {
                    o = replace;
                    createNewTriple = true;
                }
            } else if (o.isLiteral()) {
                length += literalLength.applyAsLong(o);
            } else {
                throw new IllegalStateException("Unexpected object for " + t);
            }
            if (createNewTriple) {
                return Triple.create(s, p, o);
            }
            return t;
        }

        private Node updateURI(Node n, Map<String, Node> vocabulary, Map<String, Node> cache) {
            String id = n.getURI();
            Node res = vocabulary.get(id);
            if (res != null) {
                return res;
            }
            if ((res = cache.get(id)) != null) {
                return res;
            }
            length += uriLength.applyAsLong(n);
            cache.put(id, n);
            return null;
        }

        private Node updateBNode(Node n, Map<String, Node> cache) {
            String id = n.getBlankNodeLabel();
            Node res = cache.get(id);
            if (res != null) {
                return res;
            }
            length += bNodeLength.applyAsLong(n);
            cache.put(id, n);
            return null;
        }

        public long getLength() {
            return length;
        }

        public void flush() {
            uriCache.clear();
            bnodeCache.clear();
        }

        @Override
        public String toString() {
            return String.format("%s{length=%d}{super=%s}", getClass().getSimpleName(), length, super.toString());
        }
    }
}
