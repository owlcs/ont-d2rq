package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.avicomp.conf.ISWCData;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public abstract class FindTestFramework {
    protected static final Logger LOGGER = LoggerFactory.getLogger(FindTestFramework.class);

    protected static final Model m = ModelFactory.createDefaultModel();

    protected Graph graph;
    private Set<Triple> resultTriples;

    @Before
    public void setUp() {
        LOGGER.debug("SET UP");
        Mapping mapping = ISWCData.MYSQL.loadMapping("http://test/");
        // no schema (schema validation tests are separated now)
        mapping.getConfiguration().setServeVocabulary(false);
        this.graph = mapping.getData();
    }

    @After
    public void tearDown() {
        this.graph.close();
    }

    protected void find(RDFNode s, RDFNode p, RDFNode o) {
        this.resultTriples = new HashSet<>();
        ExtendedIterator<Triple> it = this.graph.find(toNode(s), toNode(p), toNode(o));
        while (it.hasNext()) {
            this.resultTriples.add(it.next());
        }
    }

    protected RDFNode resource(String relativeURI) {
        return m.createResource("http://test/" + relativeURI);
    }

    protected void dump() {
        resultTriples.forEach(x -> LOGGER.debug("S={}", PrettyPrinter.toString(x)));
    }

    protected void assertStatementCount(int count) {
        dump();
        Assert.assertEquals("Found " + resultTriples.size() + " triples", count, resultTriples.size());
    }

    protected void assertStatement(RDFNode s, RDFNode p, RDFNode o) {
        dump();
        Triple t = new Triple(toNode(s), toNode(p), toNode(o));
        Assert.assertTrue("Not found " + PrettyPrinter.toString(t), this.resultTriples.contains(t));
    }

    protected void assertNoStatement(RDFNode s, RDFNode p, RDFNode o) {
        Triple t = new Triple(toNode(s), toNode(p), toNode(o));
        Assert.assertFalse("Found " + PrettyPrinter.toString(t), this.resultTriples.contains(t));
    }

    private Node toNode(RDFNode n) {
        if (n == null) {
            return Node.ANY;
        }
        return n.asNode();
    }
}
