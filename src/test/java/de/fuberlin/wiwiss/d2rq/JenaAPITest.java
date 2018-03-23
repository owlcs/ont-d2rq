package de.fuberlin.wiwiss.d2rq;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;
import org.junit.Assert;
import org.junit.Test;

public class JenaAPITest {

    @Test
    public void testCopyPrefixesFromMapModelToD2RQModel() {
        ModelD2RQ m = MappingFactory.load(D2RQTestHelper.DIRECTORY_URL + "prefixes.ttl").getDataModel();
        Assert.assertEquals("http://example.org/", m.getNsPrefixURI("ex"));
    }

    @Test
    public void testCopyPrefixesFromMapModelToD2RQGraph() {
        Model model = FileManager.get().loadModel(D2RQTestHelper.DIRECTORY_URL + "prefixes.ttl");
        GraphD2RQ g = MappingFactory.create(model, "http://localhost/resource/").getDataModel().getGraph();
        Assert.assertEquals("http://example.org/", g.getPrefixMapping().getNsPrefixURI("ex"));
    }

    @Test
    public void testDontCopyD2RQPrefixFromMapModel() {
        ModelD2RQ m = MappingFactory.load(D2RQTestHelper.DIRECTORY_URL + "prefixes.ttl").getDataModel();
        Assert.assertNull(m.getNsPrefixURI("d2rq"));
    }
}
