package de.fuberlin.wiwiss.d2rq.nodes;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.values.BlankNodeID;
import de.fuberlin.wiwiss.d2rq.values.Column;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class NodeMakerTest {
    private final static Attribute table_col1 = new Attribute(null, "table", "col1");
    private final static Attribute table_col2 = new Attribute(null, "table", "col2");

    @Test
    public void testFixedNodeMakerToString() {
        Assert.assertEquals("Fixed(\"foo\")",
                new FixedNodeMaker(NodeFactory.createLiteral("foo"), true).toString());
        Assert.assertEquals("Fixed(\"foo\"@en)",
                new FixedNodeMaker(NodeFactory.createLiteral("foo", "en", null), true).toString());
        Assert.assertEquals("Fixed(\"1\"^^" + PrettyPrinter.LIBRARY.shortForm(XSDDatatype.XSDint.getURI()) + ")",
                new FixedNodeMaker(NodeFactory.createLiteral("1", null, XSDDatatype.XSDint), true).toString());
        Assert.assertEquals("Fixed(_:foo)",
                new FixedNodeMaker(NodeFactory.createBlankNode("foo"), true).toString());
        Assert.assertEquals("Fixed(<http://example.org/>)",
                new FixedNodeMaker(NodeFactory.createURI("http://example.org/"), true).toString());
    }

    @Test
    public void testBlankNodeMakerToString() {
        BlankNodeID b = new BlankNodeID("classmap1", Arrays.asList(table_col1, table_col2));
        NodeMaker maker = new TypedNodeMaker(TypedNodeMaker.BLANK, b, true);
        Assert.assertEquals("Blank(BlankNodeID(table.col1,table.col2))",
                maker.toString());
    }

    @Test
    public void testPlainLiteralMakerToString() {
        TypedNodeMaker l = new TypedNodeMaker(TypedNodeMaker.PLAIN_LITERAL, new Column(table_col1), true);
        Assert.assertEquals("Literal(Column(table.col1))", l.toString());
    }

    @Test
    public void testLanguageLiteralMakerToString() {
        TypedNodeMaker l = new TypedNodeMaker(TypedNodeMaker.languageLiteral("en"), new Column(table_col1), true);
        Assert.assertEquals("Literal@en(Column(table.col1))", l.toString());
    }

    @Test
    public void testTypedLiteralMakerToString() {
        TypedNodeMaker l = new TypedNodeMaker(TypedNodeMaker.typedLiteral(XSDDatatype.XSDstring), new Column(table_col1), true);
        Assert.assertEquals("Literal^^xsd:string(Column(table.col1))", l.toString());
    }

    @Test
    public void testURIMakerToString() {
        NodeMaker u = new TypedNodeMaker(TypedNodeMaker.URI, new Column(table_col1), true);
        Assert.assertEquals("URI(Column(table.col1))", u.toString());
    }
}
