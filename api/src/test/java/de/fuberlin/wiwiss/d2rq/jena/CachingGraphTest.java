package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.utils.ReadStatsGraph;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.lib.Cache;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
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

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * To test and develop {@link CachingGraph}
 * Created by @szz on 06.11.2018.
 */
public class CachingGraphTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingGraphTest.class);

    private static final PrintStream SINK = ReadWriteUtils.NULL_OUT;

    @Test
    public void testCannotModify() {
        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(JenaModelUtils.loadTurtle("/pizza.ttl").getGraph()));
        try {
            m.createOntClass("x");
            Assert.fail("Possible to add class");
        } catch (AddDeniedException ade) {
            LOGGER.debug("Expected: '{}'", ade.getMessage());
        }
        try {
            m.removeOntObject(m.getIndividual(m.expandPrefix(":Germany")));
            Assert.fail("Possible to delete individual");
        } catch (DeleteDeniedException dde) {
            LOGGER.debug("Expected: '{}'", dde.getMessage());
        }
    }

    @Test
    public void testValidateCachingGraph() {
        ReadStatsGraph g = new ReadStatsGraph(JenaModelUtils.loadTurtle("/pizza.ttl").getGraph());
        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(g));

        performSomeReadActionsOnPizza(m);

        CachingGraph cg = ((CachingGraph) m.getBaseGraph());
        Set<Triple> cachedFindTriples = Iter.asStream(cg.findTriples.keys())
                .collect(Collectors.toSet());
        Set<Triple> cachedContainsTriples = Iter.asStream(cg.containsTriples.keys())
                .collect(Collectors.toSet());

        for (Triple t : cachedFindTriples) {
            if (g.getFindStats().hasTriple(t)) continue;
            Assert.fail("Not in find stats map: " + t);
        }
        for (Triple t : cachedContainsTriples) {
            if (g.getContainsStats().hasTriple(t)) continue;
            Assert.fail("Not in contains stats map: " + t);
        }

        g.getFindStats().triples().forEach(t -> {
            if (cachedFindTriples.contains(t)) return;
            Assert.fail("Not in find cache: " + t);
        });
        g.getContainsStats().triples().forEach(t -> {
            if (cachedContainsTriples.contains(t)) return;
            Assert.fail("Not in contains cache: " + t);
        });
        debugReadingStats("FIND", g.getFindStats());
        debugReadingStats("CONTAINS", g.getContainsStats());
        checkReadingStats(g.getFindStats(), 1);
    }

    @Test
    public void testSmallCache() {
        String uri = "http://x";
        OntGraphModel m = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("x", uri + "#");
        m.setID(uri);
        OntClass c = m.createOntClass(uri + "#Class");
        c.addLabel("TheClass");
        OntIndividual i = c.createIndividual(uri + "#Individual");
        i.addComment("This is individual");

        m = OntModelFactory.createModel(new CachingGraph(m.getBaseGraph(), 10, 150));
        m.write(SINK, "ttl");
        Assert.assertEquals(1, m.ontObjects(OntCE.class).peek(x -> LOGGER.debug("{}", x)).count());
        Assert.assertEquals(1, m.ontObjects(OntIndividual.class).peek(x -> LOGGER.debug("{}", x)).count());
        Assert.assertEquals(6, m.size());

        Cache<Triple, List<Triple>> findCache = ((CachingGraph) m.getBaseGraph()).findTriples;
        Cache<Triple, Boolean> containsCache = ((CachingGraph) m.getBaseGraph()).containsTriples;
        Assert.assertTrue(findCache.size() >= 7);
        Assert.assertTrue(containsCache.size() >= 5);

        List<Triple> header = findCache.getIfPresent(Triple.createMatch(m.getID().asNode(), null, null));
        Assert.assertEquals(1, header.size());
        Assert.assertEquals(m.getID().getRoot().asTriple(), header.get(0));

        Stream.of(c, i)
                .map(x -> Triple.createMatch(x.asNode(), null, null))
                .forEach(x -> Assert.assertSame(CachingGraph.OUT_OF_SPACE, findCache.getIfPresent(x)));
    }

    @Test
    public void testCacheInMultiThreads() {
        ReadStatsGraph g = new ReadStatsGraph(JenaModelUtils.loadTurtle("/pizza.ttl").getGraph());
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
        debugReadingStats("FIND", g.getFindStats());
        debugReadingStats("CONTAINS", g.getFindStats());
        checkReadingStats(g.getFindStats(), threadsNum);
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

    private static void debugReadingStats(String msg, ReadStatsGraph.Stats stats) {
        stats.triples().collect(Collectors.toMap(Function.identity(), stats::count))
                .entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .forEach(e -> LOGGER.debug("{} --- {}:::{}", msg, e.getValue(), e.getKey()));
    }

    private static void checkReadingStats(ReadStatsGraph.Stats stats, int max) {
        stats.triples().forEach(t -> {
            long actual = stats.count(t);
            Assert.assertTrue("Unexpected number of the method '#find(Triple)' " +
                    "calls for pattern [" + t + "]: " + actual, actual >= 1 && actual <= max);
        });
    }
}
