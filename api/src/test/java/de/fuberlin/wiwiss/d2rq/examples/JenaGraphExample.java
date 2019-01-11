package de.fuberlin.wiwiss.d2rq.examples;

import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.DC;

import java.util.Iterator;

public class JenaGraphExample {

    public static void main(String[] args) {
        // Load mapping file
        Model mapModel = FileManager.get().loadModel(TestConstants.MAPPING);

        // Parse mapping file
        Mapping mapping = MappingFactory.create(mapModel, "http://localhost:2020/");

        // Set up the GraphD2RQ
        Graph g = mapping.getData();

        // Create a find(spo) pattern
        Node subject = Node.ANY;
        Node predicate = DC.date.asNode();
        Node object = NodeFactory.createLiteral("2003", null, XSDDatatype.XSDgYear);
        Triple pattern = new Triple(subject, predicate, object);

        // Query the graph
        Iterator<Triple> it = g.find(pattern);

        // Output query results
        while (it.hasNext()) {
            Triple t = it.next();
            System.out.println("Published in 2003: " + t.getSubject());
        }
        g.close();
    }
}
