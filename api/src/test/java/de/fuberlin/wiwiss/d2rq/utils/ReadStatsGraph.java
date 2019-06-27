package de.fuberlin.wiwiss.d2rq.utils;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.impl.WrappedGraph;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by @ssz on 22.05.2019.
 */
public class ReadStatsGraph extends WrappedGraph {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadStatsGraph.class);

    private final Map<Triple, LongAdder> findStats = new ConcurrentHashMap<>();
    private final Map<Triple, LongAdder> containsStats = new ConcurrentHashMap<>();

    public ReadStatsGraph(Graph base) {
        super(Objects.requireNonNull(base));
    }

    public static void debug(ReadStatsGraph g) {
        debug("FIND", g.getFindStats());
        debug("CONTAINS", g.getFindStats());
    }

    public static void debug(String msg, Stats stats) {
        stats.triples().collect(Collectors.toMap(Function.identity(), stats::count))
                .entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .forEach(e -> LOGGER.debug("{} --- {}:::{}", msg, e.getValue(), e.getKey()));
    }

    @Override
    public ExtendedIterator<Triple> find(Triple m) {
        putToFindStats(m);
        return super.find(m);
    }

    @Override
    public ExtendedIterator<Triple> find(Node s, Node p, Node o) {
        putToFindStats(Triple.createMatch(s, p, o));
        return super.find(s, p, o);
    }

    @Override
    public boolean contains(Node s, Node p, Node o) {
        putToContainsStats(Triple.createMatch(s, p, o));
        return super.contains(s, p, o);
    }

    @Override
    public boolean contains(Triple t) {
        putToContainsStats(t);
        return super.contains(t);
    }

    private void putToFindStats(Triple t) {
        findStats.computeIfAbsent(t, x -> new LongAdder()).increment();
    }

    private void putToContainsStats(Triple t) {
        containsStats.computeIfAbsent(t, x -> new LongAdder()).increment();
    }

    public Stats getFindStats() {
        return new Stats(findStats);
    }

    public Stats getContainsStats() {
        return new Stats(containsStats);
    }

    public static class Stats {
        private final Map<Triple, LongAdder> map;

        private Stats(Map<Triple, LongAdder> stats) {
            this.map = stats;
        }

        public Stream<Triple> triples() {
            return map.keySet().stream();
        }

        public long count(Triple t) {
            LongAdder r = map.get(t);
            return r == null ? 0 : r.longValue();
        }

        public boolean hasTriple(Triple t) {
            return map.containsKey(t);
        }

        public void clear() {
            map.clear();
        }

        public boolean isEmpty() {
            return map.isEmpty();
        }

        public long size() {
            return map.size();
        }

        public enum Type {
            FIND,
            CONTAINS
        }

    }
}
