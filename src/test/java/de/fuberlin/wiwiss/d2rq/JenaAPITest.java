package de.fuberlin.wiwiss.d2rq;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import junit.framework.TestCase;

public class JenaAPITest extends TestCase {

    public void testCopyPrefixesFromMapModelToD2RQModel() {
        ModelD2RQ m = MappingFactory.load(D2RQTestSuite.DIRECTORY_URL + "prefixes.ttl").getDataModel();
        assertEquals("http://example.org/", m.getNsPrefixURI("ex"));
    }

    public void testCopyPrefixesFromMapModelToD2RQGraph() {
        Model model = FileManager.get().loadModel(D2RQTestSuite.DIRECTORY_URL + "prefixes.ttl");
        GraphD2RQ g = MappingFactory.create(model, "http://localhost/resource/").getDataModel().getGraph();
        assertEquals("http://example.org/", g.getPrefixMapping().getNsPrefixURI("ex"));
    }

    public void testDontCopyD2RQPrefixFromMapModel() {
        ModelD2RQ m = MappingFactory.load(D2RQTestSuite.DIRECTORY_URL + "prefixes.ttl").getDataModel();
        assertNull(m.getNsPrefixURI("d2rq"));
    }
}
