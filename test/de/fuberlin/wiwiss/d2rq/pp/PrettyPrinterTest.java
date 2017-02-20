package de.fuberlin.wiwiss.d2rq.pp;

import junit.framework.TestCase;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;

public class PrettyPrinterTest extends TestCase {
	
	public void testNodePrettyPrinting() {
		assertEquals("\"foo\"", 
				PrettyPrinter.toString(NodeFactory.createLiteral("foo")));
		assertEquals("\"foo\"@en", 
				PrettyPrinter.toString(NodeFactory.createLiteral("foo", "en", null)));
		assertEquals("\"1\"^^<" + XSDDatatype.XSDint.getURI() + ">",
				PrettyPrinter.toString(NodeFactory.createLiteral("1", null, XSDDatatype.XSDint)));
		assertEquals("\"1\"^^xsd:int",
				PrettyPrinter.toString(NodeFactory.createLiteral("1", null, XSDDatatype.XSDint), PrefixMapping.Standard));
		assertEquals("_:foo", 
				PrettyPrinter.toString(NodeFactory.createBlankNode("foo")));
		assertEquals("<http://example.org/>", 
				PrettyPrinter.toString(NodeFactory.createURI("http://example.org/")));
		assertEquals("<" + RDF.type.getURI() + ">", 
				PrettyPrinter.toString(RDF.type.asNode(), new PrefixMappingImpl()));
		assertEquals("rdf:type", 
				PrettyPrinter.toString(RDF.type.asNode(), PrefixMapping.Standard));
		assertEquals("?x", 
				PrettyPrinter.toString(NodeFactory.createVariable("x")));
		assertEquals("?ANY",
				PrettyPrinter.toString(Node.ANY));
	}
	
	public void testTriplePrettyPrinting() {
		assertEquals("<http://example.org/a> <" + RDFS.label.getURI() + "> \"Example\" .",
				PrettyPrinter.toString(new Triple(
						NodeFactory.createURI("http://example.org/a"),
						RDFS.label.asNode(),
						NodeFactory.createLiteral("Example", null, null))));
	}

	public void testTriplePrettyPrintingWithNodeANY() {
		assertEquals("?ANY ?ANY ?ANY .", PrettyPrinter.toString(Triple.ANY));
	}
	
	public void testTriplePrettyPrintingWithPrefixMapping() {
		PrefixMappingImpl prefixes = new PrefixMappingImpl();
		prefixes.setNsPrefixes(PrefixMapping.Standard);
		prefixes.setNsPrefix("ex", "http://example.org/");
		assertEquals("ex:a rdfs:label \"Example\" .",
				PrettyPrinter.toString(new Triple(
						NodeFactory.createURI("http://example.org/a"),
						RDFS.label.asNode(),
						NodeFactory.createLiteral("Example", null, null)), prefixes));
	}
	
	public void testResourcePrettyPrinting() {
		Model m = ModelFactory.createDefaultModel();
		assertEquals("\"foo\"", PrettyPrinter.toString(m.createLiteral("foo")));
		assertEquals("<http://test/>", PrettyPrinter.toString(m.createResource("http://test/")));
	}
	
	public void testUsePrefixMappingWhenPrintingURIResources() {
		Model m = ModelFactory.createDefaultModel();
		m.setNsPrefix("ex", "http://example.org/");
		assertEquals("ex:foo", PrettyPrinter.toString(m.createResource("http://example.org/foo")));
	}
	
	public void testD2RQTermsHaveD2RQPrefix() {
		assertEquals("d2rq:ClassMap", PrettyPrinter.toString(D2RQ.ClassMap));
	}
	
	public void testSomeRDFDatatypeToString() {
		RDFDatatype someDatatype = TypeMapper.getInstance().getSafeTypeByName("http://example.org/mytype");
		assertEquals("<http://example.org/mytype>", PrettyPrinter.toString(someDatatype));
	}
	
	public void testXSDTypeToString() {
		assertEquals("xsd:string", PrettyPrinter.toString(XSDDatatype.XSDstring));
	}
}
