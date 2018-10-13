package de.fuberlin.wiwiss.d2rq.functional_tests;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.DC;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.ontapi.jena.utils.Iter;

/**
 * Functional tests that exercise a ModelD2RQ by calling Model API functions. For
 * notes on running the tests, see {@code AllTests}.
 * <p>
 * To see debug information, uncomment the enableDebug() call in the setUp() method.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ModelAPITest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelAPITest.class);
    private Model model;

    @Before
    public void setUp() {
        this.model = MappingFactory.load(D2RQTestHelper.getResourceURI("/mapping-iswc.mysql.ttl"),
                "TURTLE", "http://test/").getDataModel();
    }

    @After
    public void tearDown() {
        this.model.close();
    }

    @Test
    public void testListStatements() {
        Assert.assertEquals(358, Iter.asStream(this.model.listStatements())
                .peek(s -> LOGGER.debug("S={}", PrettyPrinter.toString(s))).count());
    }

    @Test
    public void testHasProperty() {
        Assert.assertTrue(this.model.getResource("http://test/papers/1").hasProperty(DC.creator));
    }
}
