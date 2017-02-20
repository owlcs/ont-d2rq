package de.fuberlin.wiwiss.d2rq.nodes;

import java.util.Arrays;

import junit.framework.TestCase;

import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.AnonId;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.values.BlankNodeID;
import de.fuberlin.wiwiss.d2rq.values.Column;

public class NodeMakerTest extends TestCase {
	private final static Attribute table_col1 = new Attribute(null, "table", "col1");
	private final static Attribute table_col2 = new Attribute(null, "table", "col2");
	
	public void testFixedNodeMakerToString() {
		assertEquals("Fixed(\"foo\")", 
				new FixedNodeMaker(NodeFactory.createLiteral("foo"), true).toString());
		assertEquals("Fixed(\"foo\"@en)", 
				new FixedNodeMaker(NodeFactory.createLiteral("foo", "en", null), true).toString());
		assertEquals("Fixed(\"1\"^^<" + XSDDatatype.XSDint.getURI() + ">)",
				new FixedNodeMaker(NodeFactory.createLiteral("1", null, XSDDatatype.XSDint), true).toString());
		assertEquals("Fixed(_:foo)", 
				new FixedNodeMaker(NodeFactory.createBlankNode("foo"), true).toString());
		assertEquals("Fixed(<http://example.org/>)", 
				new FixedNodeMaker(NodeFactory.createURI("http://example.org/"), true).toString());
	}
	
	public void testBlankNodeMakerToString() {
		BlankNodeID b = new BlankNodeID("classmap1", Arrays.asList(new Attribute[]{table_col1, table_col2}));
		NodeMaker maker = new TypedNodeMaker(TypedNodeMaker.BLANK, b, true);
		assertEquals("Blank(BlankNodeID(table.col1,table.col2))", 
				maker.toString());
	}
	
	public void testPlainLiteralMakerToString() {
		TypedNodeMaker l = new TypedNodeMaker(TypedNodeMaker.PLAIN_LITERAL, new Column(table_col1), true);
		assertEquals("Literal(Column(table.col1))", l.toString());
	}
	
	public void testLanguageLiteralMakerToString() {
		TypedNodeMaker l = new TypedNodeMaker(TypedNodeMaker.languageLiteral("en"), new Column(table_col1), true);
		assertEquals("Literal@en(Column(table.col1))", l.toString());
	}
	
	public void testTypedLiteralMakerToString() {
		TypedNodeMaker l = new TypedNodeMaker(TypedNodeMaker.typedLiteral(XSDDatatype.XSDstring),
				new Column(table_col1), true);
		assertEquals("Literal^^xsd:string(Column(table.col1))", l.toString());
	}
	
	public void testURIMakerToString() {
		NodeMaker u = new TypedNodeMaker(TypedNodeMaker.URI, new Column(table_col1), true);
		assertEquals("URI(Column(table.col1))", u.toString());
	}
}
