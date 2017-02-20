package de.fuberlin.wiwiss.d2rq.examples;

import java.util.Iterator;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.DC;

import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.parser.MapParser;

public class JenaGraphExample {

	public static void main(String[] args) {
		// Load mapping file
		Model mapModel = FileManager.get().loadModel("doc/example/mapping-iswc.mysql.ttl");
		
		// Parse mapping file
		MapParser parser = new MapParser(mapModel, "http://localhost:2020/");
		Mapping mapping = parser.parse();
		
		// Set up the GraphD2RQ
		GraphD2RQ g = new GraphD2RQ(mapping);

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
