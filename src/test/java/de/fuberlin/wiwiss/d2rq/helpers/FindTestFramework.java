package de.fuberlin.wiwiss.d2rq.helpers;

import de.fuberlin.wiwiss.d2rq.D2RQTestHelper;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public abstract class FindTestFramework {
    protected static final Logger LOGGER = Logger.getLogger(FindTestFramework.class);
    protected static final Model m = ModelFactory.createDefaultModel();

    protected GraphD2RQ graph;
    private Set<Triple> resultTriples;

    @Before
    public void setUp() {
        LOGGER.debug("SET UP");
        this.graph = MappingFactory.load(D2RQTestHelper.getResourceURI("/mapping-iswc.mysql.ttl"), "TURTLE", "http://test/").getDataGraph();
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
        int count = 0;
        for (Triple t : resultTriples) {
            count++;
            System.out.println("Result Triple " + count + ": " +
                    PrettyPrinter.toString(t, this.graph.getPrefixMapping()));
        }
        System.out.println(count + " triples.");
        System.out.println();
    }

    protected void assertStatementCount(int count) {
        Assert.assertEquals("Found " + resultTriples, count, resultTriples.size());
    }

    protected void assertStatement(RDFNode s, RDFNode p, RDFNode o) {
        Assert.assertTrue(this.resultTriples.contains(new Triple(toNode(s), toNode(p), toNode(o))));
    }

    protected void assertNoStatement(RDFNode s, RDFNode p, RDFNode o) {
        Assert.assertFalse(this.resultTriples.contains(new Triple(toNode(s), toNode(p), toNode(o))));
    }

    private Node toNode(RDFNode n) {
        if (n == null) {
            return Node.ANY;
        }
        return n.asNode();
    }
}
