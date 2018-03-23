package de.fuberlin.wiwiss.d2rq.nodes;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.expr.AttributeExpr;
import de.fuberlin.wiwiss.d2rq.expr.Equality;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.expr.SQLExpression;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.BlankNodeID;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * TODO: Improve matching of datatypes, languages etc:
 * - "foo"@en and "foo"@en-US should match
 * - "1"@xsd:int and "1"@xsd:bye should match
 * - "asdf" and "asdf"@xsd:string should match
 * - "1"@xsd:double and "+1.0"@xsd:double should match
 * - Attributes with obviously incompatible types should not match
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class NodeSetTest {
    private final static Attribute table1foo = SQL.parseAttribute("table1.foo");
    private final static Attribute table1bar = SQL.parseAttribute("table1.bar");
    private final static Attribute alias1foo = SQL.parseAttribute("T2_table1.foo");
    private final static BlankNodeID fooBlankNodeID =
            new BlankNodeID("Foo", Collections.singletonList(table1foo));
    private final static BlankNodeID fooBlankNodeID2 =
            new BlankNodeID("Foo", Collections.singletonList(alias1foo));
    private final static BlankNodeID barBlankNodeID =
            new BlankNodeID("Bar", Collections.singletonList(table1foo));
    private final static Pattern pattern1 =
            new Pattern("http://example.org/res@@table1.foo@@");
    private final static Pattern pattern1aliased =
            new Pattern("http://example.org/res@@T2_table1.foo@@");
    private final static Pattern pattern2 =
            new Pattern("http://example.org/thing@@table1.foo@@");
    private final static Pattern pattern3 =
            new Pattern("http://example.org/res@@table1.foo@@/@@table1.bar@@");
    private final static Expression expression1 =
            SQLExpression.create("SHA1(table1.foo)");
    private final static Expression expression2 =
            SQLExpression.create("LOWER(table1.bar)");
    private NodeSetConstraintBuilder nodes;

    @Before
    public void setUp() {
        nodes = new NodeSetConstraintBuilder();
    }

    @Test
    public void testInitiallyNotEmpty() {
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitToURIsNotEmpty() {
        nodes.limitToURIs();
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitToLiteralsNotEmpty() {
        nodes.limitToLiterals(null, null);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitToBlankNodes() {
        nodes.limitToBlankNodes();
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitToFixedNodeNotEmpty() {
        nodes.limitTo(RDF.Nodes.type);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitToEmptySetIsEmpty() {
        nodes.limitToEmptySet();
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testLimitValuesToConstantNotEmpty() {
        nodes.limitValues("foo");
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitValuesToAttributeNotEmpty() {
        nodes.limitValuesToAttribute(table1foo);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitValuesToPatternNotEmpty() {
        nodes.limitValuesToPattern(pattern1);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitValuesToBlankNodeIDNotEmpty() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testLimitValuesToExpressionNotEmpty() {
        nodes.limitValuesToExpression(expression1);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testURIsAndLiteralsEmpty() {
        nodes.limitToURIs();
        nodes.limitToLiterals(null, null);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testURIsAndBlanksEmpty() {
        nodes.limitToURIs();
        nodes.limitToBlankNodes();
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testBlanksAndLiteralsEmpty() {
        nodes.limitToBlankNodes();
        nodes.limitToLiterals(null, null);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentFixedBlanksEmpty() {
        nodes.limitTo(NodeFactory.createBlankNode("foo"));
        nodes.limitTo(NodeFactory.createBlankNode("bar"));
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentFixedURIsEmpty() {
        nodes.limitTo(RDF.Nodes.type);
        nodes.limitTo(RDF.Nodes.Property);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentFixedLiteralsEmpty() {
        nodes.limitTo(NodeFactory.createLiteral("foo"));
        nodes.limitTo(NodeFactory.createLiteral("bar"));
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentTypeFixedLiteralsEmpty() {
        nodes.limitTo(NodeFactory.createURI("http://example.org/"));
        nodes.limitTo(NodeFactory.createLiteral("http://example.org/"));
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentConstantsEmpty() {
        nodes.limitValues("foo");
        nodes.limitValues("bar");
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testFixedAndConstantEmpty() {
        nodes.limitTo(NodeFactory.createURI("http://example.org/"));
        nodes.limitValues("foo");
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testFixedAndConstantNotEmpty() {
        nodes.limitTo(NodeFactory.createURI("http://example.org/"));
        nodes.limitValues("http://example.org/");
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testDifferentLanguagesEmpty() {
        nodes.limitToLiterals("en", null);
        nodes.limitToLiterals("de", null);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentLanguagesFixedEmpty() {
        nodes.limitTo(NodeFactory.createLiteral("foo", "de", null));
        nodes.limitTo(NodeFactory.createLiteral("foo", "en", null));
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentDatatypesEmpty() {
        nodes.limitToLiterals(null, XSDDatatype.XSDstring);
        nodes.limitToLiterals(null, XSDDatatype.XSDinteger);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testDifferentDatatypesFixedEmpty() {
        nodes.limitTo(NodeFactory.createLiteral("42", null, XSDDatatype.XSDstring));
        nodes.limitTo(NodeFactory.createLiteral("42", null, XSDDatatype.XSDinteger));
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testSameAttributeTwiceNotEmpty() {
        nodes.limitValuesToAttribute(table1foo);
        nodes.limitValuesToAttribute(table1foo);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testAttributeAndConstantNotEmpty() {
        nodes.limitValues("foo");
        nodes.limitValuesToAttribute(table1foo);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testSameBlankNodeIDTwiceNotEmpty() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testSamePatternTwiceNotEmpty() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToPattern(pattern1);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testSameExpressionTwiceNotEmpty() {
        nodes.limitValuesToExpression(expression1);
        nodes.limitValuesToExpression(expression1);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testBlankNodesFromDifferentClassMapsEmpty() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitValuesToBlankNodeID(barBlankNodeID);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testBlankNodeAndConstantMatchNotEmpty() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitTo(NodeFactory.createBlankNode("Foo@@42"));
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testBlankNodeAndConstantNoMatchEmpty() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitTo(NodeFactory.createBlankNode("Bar@@42"));
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testIncompatiblePatternsEmpty() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToPattern(pattern2);
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testPatternAndConstantMatchNotEmpty() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValues("http://example.org/res42");
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testPatternAndConstantNoMatchEmpty() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValues("http://example.org/thing42");
        Assert.assertTrue(nodes.isEmpty());
    }

    @Test
    public void testAliasedPatternsNotEmpty() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToPattern(pattern1aliased);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testExpressionAndConstantNotEmpty() {
        nodes.limitValues("foo");
        nodes.limitValuesToExpression(expression1);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testSameAttributeTwiceExpressionIsTrue() {
        nodes.limitValuesToAttribute(table1foo);
        nodes.limitValuesToAttribute(table1foo);
        Assert.assertEquals(Expression.TRUE, nodes.constraint());
    }

    @Test
    public void testAttributeAndConstantExpressionIsEquality() {
        nodes.limitValues("foo");
        nodes.limitValuesToAttribute(table1foo);
        Assert.assertEquals(Equality.createAttributeValue(table1foo, "foo"),
                nodes.constraint());
    }

    @Test
    public void testSameBlankNodeIDTwiceExpressionIsTrue() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        Assert.assertEquals(Expression.TRUE, nodes.constraint());
    }

    @Test
    public void testSamePatternTwiceExpressionIsTrue() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToPattern(pattern1);
        Assert.assertEquals(Expression.TRUE, nodes.constraint());
    }

    @Test
    public void testSameExpressionTwiceExpressionIsTrue() {
        nodes.limitValuesToExpression(expression1);
        nodes.limitValuesToExpression(expression1);
        Assert.assertEquals(Expression.TRUE, nodes.constraint());
    }

    @Test
    public void testBlankNodeAndConstantMatchExpressionIsEquality() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitTo(NodeFactory.createBlankNode("Foo@@42"));
        Assert.assertEquals(Equality.createAttributeValue(table1foo, "42"),
                nodes.constraint());
    }

    @Test
    public void testPatternAndConstantMatchExpressionIsEquality() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValues("http://example.org/res42");
        Assert.assertEquals(Equality.createAttributeValue(table1foo, "42"),
                nodes.constraint());
    }

    @Test
    public void testExpressionAndConstantExpressionIsEquality() {
        nodes.limitValues("foo");
        nodes.limitValuesToExpression(expression1);
        Assert.assertEquals(Equality.createExpressionValue(expression1, "foo"),
                nodes.constraint());
    }

    @Test
    public void testTwoAttributesExpressionIsEquality() {
        nodes.limitValuesToAttribute(table1foo);
        nodes.limitValuesToAttribute(table1bar);
        Assert.assertEquals(Equality.createAttributeEquality(table1foo, table1bar),
                nodes.constraint());
    }

    @Test
    public void testEquivalentPatternsExpressionIsEquality() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToPattern(pattern1aliased);
        Assert.assertEquals(Equality.createAttributeEquality(table1foo, alias1foo),
                nodes.constraint());
    }

    @Test
    public void testTwoPatternsExpressionIsConcatEquality() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToPattern(pattern3);
        Assert.assertEquals("Equality(" +
                        "Concatenation(" +
                        "Constant(http://example.org/res), " +
                        "AttributeExpr(@@table1.foo@@)), " +
                        "Concatenation(" +
                        "Constant(http://example.org/res), " +
                        "AttributeExpr(@@table1.foo@@), " +
                        "Constant(/), " +
                        "AttributeExpr(@@table1.bar@@)))",
                nodes.constraint().toString());
    }

    @Test
    public void testTwoExpressionsTranslatesToEquality() {
        nodes.limitValuesToExpression(expression1);
        nodes.limitValuesToExpression(expression2);
        Assert.assertEquals(Equality.create(expression1, expression2),
                nodes.constraint());
    }

    @Test
    public void testEquivalentBlankNodeIDsExpressionIsEquality() {
        nodes.limitValuesToBlankNodeID(fooBlankNodeID);
        nodes.limitValuesToBlankNodeID(fooBlankNodeID2);
        Assert.assertEquals(Equality.createAttributeEquality(table1foo, alias1foo),
                nodes.constraint());
    }

    @Test
    public void testPatternEqualsAttribute() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToAttribute(table1bar);
        Assert.assertEquals("Equality(" +
                        "AttributeExpr(@@table1.bar@@), " +
                        "Concatenation(" +
                        "Constant(http://example.org/res), " +
                        "AttributeExpr(@@table1.foo@@)))",
                nodes.constraint().toString());
    }

    @Test
    public void testExpressionEqualsAttribute() {
        nodes.limitValuesToExpression(expression1);
        nodes.limitValuesToAttribute(table1bar);
        Assert.assertEquals(Equality.create(expression1, new AttributeExpr(table1bar)),
                nodes.constraint());
    }

    @Test
    public void testExpressionEqualsPattern() {
        nodes.limitValuesToPattern(pattern1);
        nodes.limitValuesToExpression(expression1);
        Assert.assertEquals("Equality(" +
                        "SQL(SHA1(table1.foo)), " +
                        "Concatenation(" +
                        "Constant(http://example.org/res), " +
                        "AttributeExpr(@@table1.foo@@)))",
                nodes.constraint().toString());
    }

    @Test
    public void testANYNodeDoesNotLimit() {
        nodes.limitTo(Node.ANY);
        nodes.limitTo(RDF.Nodes.type);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testVariableNodeDoesNotLimit() {
        nodes.limitTo(NodeFactory.createVariable("foo"));
        nodes.limitTo(RDF.Nodes.type);
        Assert.assertFalse(nodes.isEmpty());
    }

    @Test
    public void testPatternDifferentColumnFunctionsUnsupported() {
        nodes.limitValuesToPattern(new Pattern("test/@@table1.foo|urlify@@"));
        nodes.limitValuesToPattern(new Pattern("test/@@table1.bar|urlencode@@"));
        Assert.assertFalse(nodes.isEmpty());
        Assert.assertTrue(nodes.isUnsupported());
    }

    @Test
    public void testTranslatorUnsupported() {
        nodes.setUsesTranslator(Translator.IDENTITY);
        nodes.setUsesTranslator(new TranslationTable(ResourceFactory.createResource()).translator());
        Assert.assertFalse(nodes.isEmpty());
        Assert.assertTrue(nodes.isUnsupported());
    }

    @Test
    public void testPatternWithColumnFunctionAndColumnUnsupported() {
        nodes.limitValuesToAttribute(table1foo);
        nodes.limitValuesToPattern(new Pattern("@@table2.bar|urlify@@"));
        Assert.assertEquals("Equality(AttributeExpr(@@table1.foo@@), AttributeExpr(@@table2.bar@@))",
                nodes.constraint().toString());
        Assert.assertTrue(nodes.isUnsupported());
    }
}
