package de.fuberlin.wiwiss.d2rq.jena;

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
import tmp.Tmp_Caching;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Created by @szz on 06.11.2018.
 */
public class CachingGraphTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachingGraphTest.class);

    private static final PrintStream SINK = ReadWriteUtils.NULL_OUT;

    @Test
    public void testCaching() throws IOException {
        Map<Triple, LongAdder> map = new HashMap<>();
        Graph g = new GraphMem() {
            @Override
            public ExtendedIterator<Triple> graphBaseFind(Triple m) {
                map.computeIfAbsent(m, x -> new LongAdder()).increment();
                return super.graphBaseFind(m);
            }
        };
        Model base = ModelFactory.createModelForGraph(g);
        try (InputStream in = Tmp_Caching.class.getResourceAsStream("/pizza.ttl")) {
            base.read(in, null, "ttl");
        }
        long size = base.size();
        map.clear();

        OntGraphModel m = OntModelFactory.createModel(new CachingGraph(g));
        m.write(SINK, "ttl");
        Assert.assertEquals(332, m.ontObjects(OntCE.class).peek(x -> LOGGER.debug("{}", x)).count());
        Assert.assertEquals(5, m.ontObjects(OntIndividual.class).peek(x -> LOGGER.debug("{}", x)).count());
        Assert.assertEquals(size, m.size());
        OntIndividual.Named ind = m.getOntEntity(OntIndividual.Named.class,
                "http://www.co-ode.org/ontologies/pizza/pizza.owl#America");
        Assert.assertNotNull(ind);
        // modify:
        try {
            m.createOntEntity(OntClass.class, "x");
            Assert.fail("Possible to add class");
        } catch (AddDeniedException ade) {
            LOGGER.debug("Expected: '{}'", ade.getMessage());
        }
        try {
            m.removeOntObject(ind);
            Assert.fail("Possible to delete individual");
        } catch (DeleteDeniedException dde) {
            LOGGER.debug("Expected: '{}'", dde.getMessage());
        }


        map.entrySet().stream()
                .sorted(Comparator.comparingLong(o -> o.getValue().longValue()))
                .forEach(e -> LOGGER.debug("{}:::{}", e.getValue().longValue(), e.getKey()));
        map.forEach((t, l) -> Assert.assertEquals("Incorrect count for " + t, 1, l.longValue()));
    }
}
