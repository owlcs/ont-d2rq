package de.fuberlin.wiwiss.d2rq.jena;

import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import de.fuberlin.wiwiss.d2rq.utils.ReadStatsGraph;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.shared.AddDeniedException;
import org.apache.jena.shared.DeleteDeniedException;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.OntModelFactory;
import ru.avicomp.ontapi.jena.UnionGraph;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntClass;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.utils.ReadWriteUtils;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * To test and develop {@link CachingGraph}
 * Created by @szz on 06.11.2018.
 */
public class CachingGraphTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingGraphTest.class);

    private static final PrintStream SINK = ReadWriteUtils.NULL_OUT;

    @SuppressWarnings("SameParameterValue")
    private static void validateCachingGraphWithCoverage(boolean containsByFind) {
        ReadStatsGraph rsg = new ReadStatsGraph(JenaModelUtils.loadTurtle("/pizza.ttl").getGraph());
        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(rsg));

        performSomeReadActionsOnPizza(m);

        CachingGraph cg = ((CachingGraph) m.getBaseGraph());
        Set<Triple> cachedFindTriples = Iter.asStream(cg.findCache.keys())
                .collect(Collectors.toSet());
        Set<Triple> cachedContainsTriples = Iter.asStream(cg.containsCache.keys())
                .collect(Collectors.toSet());

        for (Triple t : cachedFindTriples) {
            if (rsg.getFindStats().hasTriple(t)) continue;
            Assert.fail("Not in find stats map: " + t);
        }

        if (containsByFind) {
            Assert.assertTrue(rsg.getContainsStats().isEmpty());
        } else {
            for (Triple t : cachedContainsTriples) {
                if (rsg.getContainsStats().hasTriple(t)) continue;
                Assert.fail("Not in contains stats map: " + t);
            }
        }

        rsg.getFindStats().triples().forEach(t -> {
            if (cachedFindTriples.contains(t)) return;
            Assert.fail("Not in find cache: " + t);
        });
        rsg.getContainsStats().triples().forEach(t -> {
            if (cachedContainsTriples.contains(t)) return;
            Assert.fail("Not in contains cache: " + t);
        });
        ReadStatsGraph.debug(rsg);
        checkReadingStats(rsg.getFindStats(), 1);
    }

    @SuppressWarnings("SameParameterValue")
    private static void validateInternalGraphCachingBuckets(boolean containsByFind) {
        String uri = "http://x";
        OntGraphModel m1 = OntModelFactory.createModel()
                .setNsPrefixes(OntModelFactory.STANDARD).setNsPrefix("x", uri + "#");
        m1.setID(uri);
        OntClass c = m1.createOntClass(uri + "#NamedClass01");
        c.addLabel("TheClass");
        c.createIndividual(uri + "#Individual01").addComment("This is a first named individual");
        c.createIndividual(uri + "#Individual02").addComment("This is a second named individual");
        c.createIndividual().addComment("This is an anonymous individual");
        JenaModelUtils.print(m1);

        Model m2 = OntModelFactory.createModel(new CachingGraph(m1.getBaseGraph(), 10, 200));

        Assert.assertEquals(1, m2.listStatements(null, RDF.type, OWL.Class).toList().size());
        Assert.assertEquals(2, m2.listStatements(null, RDF.type, OWL.NamedIndividual)
                .mapWith(Statement::getSubject)
                .filterKeep(x -> m2.contains(x, RDF.type, c)).toList().size());
        Assert.assertEquals(3, m2.listStatements(null, RDFS.comment, (RDFNode) null).toList().size());
        Assert.assertEquals(0, m2.listStatements(null, RDFS.seeAlso, (RDFNode) null).toList().size());

        CachingGraph g = (CachingGraph) ((UnionGraph) m2.getGraph()).getBaseGraph();
        CachingGraph.CacheMap<Triple, CachingGraph.Bucket> findCache = g.findCache;
        CachingGraph.CacheMap<Triple, Boolean> containsCache = g.containsCache;

        Assert.assertEquals(containsByFind ? 6 : 4, findCache.size());
        Assert.assertEquals(2, containsCache.size());
        CachingGraph.Bucket outOfSpace = findCache.get(Triple.createMatch(null, RDFS.comment.asNode(), null));
        Assert.assertNotNull(outOfSpace);
        Assert.assertSame(CachingGraph.OUT_OF_SPACE, outOfSpace);
        CachingGraph.Bucket empty = findCache.get(Triple.createMatch(null, RDFS.seeAlso.asNode(), null));
        Assert.assertNotNull(empty);
        Assert.assertSame(CachingGraph.EMPTY, empty);

        findCache.keys().forEachRemaining(key -> {
            CachingGraph.Bucket cache = findCache.get(key);
            Assert.assertNotNull("Null cache for " + key, cache);
            if (Triple.createMatch(null, RDFS.comment.asNode(), null).equals(key)) {
                Assert.assertSame(CachingGraph.OUT_OF_SPACE, cache);
                return;
            }
            if (Triple.createMatch(null, RDFS.seeAlso.asNode(), null).equals(key)) {
                Assert.assertSame(CachingGraph.EMPTY, cache);
                return;
            }
            Assert.assertNotEquals(String.format("Key: %s, Value: %s", key, cache), 0, cache.size());
        });
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

    private static void checkReadingStats(ReadStatsGraph.Stats stats, int max) {
        stats.triples().forEach(t -> {
            long actual = stats.count(t);
            Assert.assertTrue("Unexpected number of the method '#find(Triple)' " +
                    "calls for pattern [" + t + "]: " + actual, actual >= 1 && actual <= max);
        });
    }

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
    public void testValidateCachingGraphWithCoverage() {
        validateCachingGraphWithCoverage(true);
    }

    @Test
    public void testInternalGraphCachingBuckets() {
        validateInternalGraphCachingBuckets(true);
    }

    @Test
    public void testCacheInMultiThreads() {
        int threadsNum = 120;
        int timeoutInMs = 5000;

        ReadStatsGraph rsg = new ReadStatsGraph(JenaModelUtils.loadTurtle("/pizza.ttl").getGraph());
        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(new ReadStatsGraph(rsg)));

        ExecutorService service = Executors.newFixedThreadPool(threadsNum);
        AtomicBoolean stop = new AtomicBoolean(false);
        List<Future<?>> res = new ArrayList<>();
        LOGGER.debug("Start concurrent reading");
        for (int i = 0; i < threadsNum; i++)
            res.add(service.submit(() -> {
                LOGGER.debug("{} go", Thread.currentThread());
                while (!stop.get()) {
                    performSomeReadActionsOnPizza(m);
                }
                LOGGER.debug("{} exit", Thread.currentThread());
            }));
        service.shutdown();
        try {
            Thread.sleep(timeoutInMs);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        stop.set(true);
        for (Future<?> f : res) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new AssertionError(e);
            }
        }
        LOGGER.debug("Fin.");
        ReadStatsGraph.debug(rsg);
        checkReadingStats(rsg.getFindStats(), threadsNum);
    }

    @Test
    public void testLargeGraphWithSmallCacheListIndividuals() {
        int size = 100;
        long length = 10;

        int individuals = 1000;
        String ns = "http://xxx#";
        OntGraphModel orig = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        for (int i = 0; i < individuals; i++) {
            orig.getOWLThing().createIndividual(ns + "Individual#" + i);
        }

        ReadStatsGraph rsg = new ReadStatsGraph(orig.getBaseGraph());
        CachingGraph cg = new CachingGraph(rsg, size, length);
        OntGraphModel m = OntModelFactory.createModel(cg);
        Assert.assertEquals(individuals, m.individuals().count());
        Assert.assertEquals(size, cg.tooLongChains.size());
        Assert.assertEquals(0, rsg.getContainsStats().size());
    }

    @Test
    public void testLargeGraphWithLargeCacheListIndividuals() {
        int individuals = 10000;
        String ns = "http://x#";
        OntGraphModel orig = OntModelFactory.createModel().setNsPrefixes(OntModelFactory.STANDARD);
        for (int i = 0; i < individuals; i++) {
            orig.getOWLThing().createIndividual(ns + "I#" + i);
        }

        ReadStatsGraph rsg = new ReadStatsGraph(orig.getBaseGraph());
        CachingGraph cg = new CachingGraph(rsg, 10000, 100_000_000);
        OntGraphModel m = OntModelFactory.createModel(cg);
        Assert.assertEquals(individuals, m.individuals().count());
        Assert.assertEquals(0, cg.tooLongChains.size());
        Assert.assertEquals(0, rsg.getContainsStats().size());
        // ~100 items (collecting system resources):
        long finds = rsg.getFindStats().size();
        Assert.assertTrue(finds > 0 && finds < 1000);
        Assert.assertEquals(finds, cg.findCache.size());
    }
}
