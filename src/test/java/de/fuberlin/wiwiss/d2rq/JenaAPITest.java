package de.fuberlin.wiwiss.d2rq;

import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;
import org.junit.Assert;
import org.junit.Test;

public class JenaAPITest {

    @Test
    public void testCopyPrefixesFromMapModelToD2RQModel() {
        Model m = MappingFactory.load(JenaAPITest.class.getResource("/prefixes.ttl").toString()).getDataModel();
        Assert.assertEquals("http://example.org/", m.getNsPrefixURI("ex"));
    }

    @Test
    public void testCopyPrefixesFromMapModelToD2RQGraph() {
        Model model = FileManager.get().loadModel(JenaAPITest.class.getResource("/prefixes.ttl").toString());
        Graph g = MappingFactory.create(model, "http://localhost/resource/").getData();
        Assert.assertEquals("http://example.org/", g.getPrefixMapping().getNsPrefixURI("ex"));
    }

    @Test
    public void testDontCopyD2RQPrefixFromMapModel() {
        Model m = MappingFactory.load(JenaAPITest.class.getResource("/prefixes.ttl").toString()).getDataModel();
        Assert.assertNull(m.getNsPrefixURI("d2rq"));
    }
}
