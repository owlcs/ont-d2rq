package de.fuberlin.wiwiss.d2rq.functional_tests;

import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.DC;
import org.apache.jena.vocabulary.SKOS;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ISWCData;
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
    private Model data;

    @Before
    public void setUp() {
        this.data = ISWCData.MYSQL.loadMapping("http://test/")
                .getConfiguration().setServeVocabulary(false).getMapping().getDataModel();
    }

    @After
    public void tearDown() {
        this.data.close();
    }

    @Test
    public void testListStatements() {
        Assert.assertEquals(283, Iter.asStream(this.data.listStatements())
                .peek(s -> LOGGER.debug("S={}", PrettyPrinter.toString(s))).count());
    }

    @Test
    public void testHasProperty1() {
        Assert.assertTrue(this.data.getResource("http://test/papers/1").hasProperty(DC.creator));
    }

    @Test
    public void testHasProperty2() {
        Assert.assertTrue(this.data.getResource("http://test/topics/11")
                .hasProperty(SKOS.broader, data.createResource("http://test/topics/5")));
    }
}
