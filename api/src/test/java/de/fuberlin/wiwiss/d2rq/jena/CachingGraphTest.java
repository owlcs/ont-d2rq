package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.mem.GraphMem;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

/**
 * Created by @szz on 06.11.2018.
 */
public class CachingGraphTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingGraphTest.class);

    private static final PrintStream SINK = ReadWriteUtils.NULL_OUT;

    @Test
    public void testCommonCaching() {
        Map<Triple, LongAdder> stats = new HashMap<>();
        Graph g = loadPizza(stats);
        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(g));

        performSomeReadActionsOnPizza(m);
        // modify:
        try {
            m.createOntEntity(OntClass.class, "x");
            Assert.fail("Possible to add class");
        } catch (AddDeniedException ade) {
            LOGGER.debug("Expected: '{}'", ade.getMessage());
        }
        try {
            m.removeOntObject(m.getOntEntity(OntIndividual.Named.class, m.expandPrefix(":Germany")));
            Assert.fail("Possible to delete individual");
        } catch (DeleteDeniedException dde) {
            LOGGER.debug("Expected: '{}'", dde.getMessage());
        }

        Cache<Triple, List<Triple>> cache = ((CachingGraph) m.getBaseGraph()).triples;
        Set<Triple> keys = Iter.asStream(cache.keys()).collect(Collectors.toSet());
        for (Triple t : keys) {
            if (stats.containsKey(t)) continue;
            Assert.fail("Not in map: " + t);
        }
        for (Triple t : stats.keySet()) {
            if (keys.contains(t)) continue;
            Assert.fail("Not in cache: " + t);
        }
        debugReadingStats(stats);
        checkReadingStats(stats, 1);
    }

    @Test
    public void testSmallCache() {
        String uri = "http://x";
        OntGraphModel m = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("x", uri + "#");
        m.setID(uri);
        OntClass c = m.createOntEntity(OntClass.class, uri + "#Class");
        c.addLabel("TheClass");
        OntIndividual i = c.createIndividual(uri + "#Individual");
        i.addComment("This is individual");

        m = OntModelFactory.createModel(new CachingGraph(m.getBaseGraph(), 10, 3));
        m.write(SINK, "ttl");
        Assert.assertEquals(1, m.ontObjects(OntCE.class).peek(x -> LOGGER.debug("{}", x)).count());
        Assert.assertEquals(1, m.ontObjects(OntIndividual.class).peek(x -> LOGGER.debug("{}", x)).count());
        Assert.assertEquals(6, m.size());
        Cache<Triple, List<Triple>> cache = ((CachingGraph) m.getBaseGraph()).triples;
        Assert.assertEquals(10, cache.size());
        cache.keys().forEachRemaining(x -> Assert.assertTrue(cache.getOrFill(x, () -> {
            throw new AssertionError("No value");
        }).isEmpty()));
        Assert.assertSame(CachingGraph.OUT_OF_SPACE, cache.getIfPresent(i.getRoot().asTriple()));
        Assert.assertSame(CachingGraph.OUT_OF_SPACE, cache.getIfPresent(c.getRoot().asTriple()));
    }

    @Test
    public void testCacheInMultiThreads() {
        Map<Triple, LongAdder> stats = new ConcurrentHashMap<>();
        Graph g = loadPizza(stats);
        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(g));

        int threadsNum = 120;
        ExecutorService service = Executors.newFixedThreadPool(threadsNum);
        List<Future<?>> res = new ArrayList<>();
        LOGGER.debug("Start concurrent reading");
        for (int i = 0; i < threadsNum; i++)
            res.add(service.submit(() -> {
                LOGGER.debug("{} go", Thread.currentThread());
                performSomeReadActionsOnPizza(m);
                LOGGER.debug("{} exit", Thread.currentThread());
            }));
        service.shutdown();
        for (Future<?> f : res) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new AssertionError(e);
            }
        }
        LOGGER.debug("Fin.");
        debugReadingStats(stats);
        checkReadingStats(stats, threadsNum);
    }

    private static void performSomeReadActionsOnPizza(OntGraphModel pizza) {
        Assert.assertEquals(100, pizza.classes().count());
        Assert.assertEquals(332, pizza.ontObjects(OntCE.class).count());
        Assert.assertEquals(5, pizza.ontObjects(OntIndividual.class).count());
        OntClass c = pizza.getOntEntity(OntClass.class, pizza.expandPrefix(":AnchoviesTopping"));
        Assert.assertNotNull(c);
        Assert.assertEquals(1, c.superClasses().count());
        Assert.assertEquals(2, c.disjointClasses().count());
        Assert.assertEquals("CoberturaDeAnchovies", c.getLabel("pt"));
        OntIndividual.Named ind = pizza.getOntEntity(OntIndividual.Named.class, pizza.expandPrefix(":America"));
        Assert.assertNotNull(ind);
        Assert.assertEquals(2, ind.classes().count());
        Set<String> classes = new HashSet<>();
        classes.add(ind.classes().findFirst().orElseThrow(AssertionError::new).getURI());
        classes.add(ind.classes().skip(1).findFirst().orElseThrow(AssertionError::new).getURI());
        Assert.assertTrue(classes.contains(pizza.expandPrefix(":Country")));
        Assert.assertTrue(classes.contains(pizza.expandPrefix("owl:Thing")));
        Assert.assertEquals(1937, pizza.size());
        pizza.write(SINK, "ttl");
    }

    private static Graph loadPizza(Map<Triple, LongAdder> stats) {
        return loadGraphWithCoverage("/pizza.ttl", stats);
    }

    @SuppressWarnings("SameParameterValue")
    private static Graph loadGraphWithCoverage(String file, Map<Triple, LongAdder> stats) {
        Graph g = new GraphMem() {
            @Override
            public ExtendedIterator<Triple> graphBaseFind(Triple m) {
                stats.computeIfAbsent(m, x -> new LongAdder()).increment();
                return super.graphBaseFind(m);
            }
        };
        Model base = ModelFactory.createModelForGraph(g);
        try (InputStream in = CachingGraphTest.class.getResourceAsStream(file)) {
            base.read(in, null, "ttl");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Assert.assertTrue(stats.isEmpty());
        return g;
    }

    private static void debugReadingStats(Map<Triple, LongAdder> stats) {
        stats.entrySet().stream()
                .sorted(Comparator.comparingLong(o -> o.getValue().longValue()))
                .forEach(e -> LOGGER.debug("{}:::{}", e.getValue().longValue(), e.getKey()));
    }

    private static void checkReadingStats(Map<Triple, LongAdder> stats, int max) {
        stats.forEach((t, l) ->
                Assert.assertTrue("Unexpected number of the method '#find(Triple)' calls for pattern " + t,
                        1 <= l.longValue() && l.longValue() <= max));
    }
}
