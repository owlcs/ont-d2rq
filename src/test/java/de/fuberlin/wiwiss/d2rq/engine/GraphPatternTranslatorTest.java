package de.fuberlin.wiwiss.d2rq.engine;

import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.expr.Equality;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import org.apache.jena.graph.Triple;
import org.apache.jena.graph.test.NodeCreateUtils;
import org.apache.jena.sparql.core.Var;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GraphPatternTranslatorTest {
    private final static RelationName table1 = SQL.parseRelationName("table1");
    private final static Attribute table1id = SQL.parseAttribute("table1.id");
    private final static Attribute t1table1id = SQL.parseAttribute("T1_table1.id");
    private final static Attribute t2table1id = SQL.parseAttribute("T2_table1.id");
    private final static Var foo = Var.alloc("foo");
    private final static Var type = Var.alloc("type");
    private final static Var x = Var.alloc("x");


    @Test
    public void testEmptyGraphAndBGP() {
        NodeRelation nodeRel = translate1(Collections.emptyList(), Collections.emptyList());
        Assert.assertNotNull(nodeRel);
        Assert.assertEquals(Relation.TRUE, nodeRel.baseRelation());
        Assert.assertEquals(Collections.EMPTY_SET, nodeRel.variables());
    }

    @Test
    public void testEmptyGraph() {
        Assert.assertNull(translate1("?subject ?predicate ?object", Collections.emptyList()));
    }

    @Test
    public void testEmptyBGP() {
        NodeRelation nodeRel = translate1(Collections.emptyList(), "engine/type-bridge.n3");
        Assert.assertEquals(Relation.TRUE, nodeRel.baseRelation());
        Assert.assertEquals(Collections.EMPTY_SET, nodeRel.variables());
    }

    @Test
    public void testAskNoMatch() {
        Assert.assertNull(translate1("ex:res1 rdf:type foaf:Project", "engine/type-bridge.n3"));
    }

    @Test
    public void testAskMatch() {
        NodeRelation nodeRel = translate1("ex:res1 rdf:type ex:Class1", "engine/type-bridge.n3");
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals(Collections.singleton(table1), r.tables());
        Assert.assertEquals(Collections.EMPTY_SET, r.projections());
        Assert.assertEquals(Equality.createAttributeValue(table1id, "1"), r.condition());
        Assert.assertEquals(AliasMap.NO_ALIASES, r.aliases());
        Assert.assertEquals(Collections.EMPTY_SET, nodeRel.variables());
    }

    @Test
    public void testFindNoMatch() {
        Assert.assertNull(translate1("ex:res1 ex:foo ?foo", "engine/type-bridge.n3"));
    }

    @Test
    public void testFindFixedMatch() {
        NodeRelation nodeRel = translate1("ex:res1 rdf:type ?type", "engine/type-bridge.n3");
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals(Collections.singleton(table1), r.tables());
        Assert.assertEquals(Collections.EMPTY_SET, r.projections());
        Assert.assertEquals(Equality.createAttributeValue(table1id, "1"), r.condition());
        Assert.assertEquals(AliasMap.NO_ALIASES, r.aliases());
        Assert.assertEquals(Collections.singleton(type), nodeRel.variables());
        Assert.assertEquals("Fixed(<http://example.org/Class1>)", nodeRel.nodeMaker(type).toString());
    }

    @Test
    public void testFindMatch() {
        NodeRelation nodeRel = translate1("?x rdf:type ex:Class1", "engine/type-bridge.n3");
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals(Collections.singleton(table1), r.tables());
        Assert.assertEquals(Collections.singleton(table1id), r.projections());
        Assert.assertEquals(Expression.TRUE, r.condition());
        Assert.assertEquals(AliasMap.NO_ALIASES, r.aliases());
        Assert.assertEquals(Collections.singleton(x), nodeRel.variables());
        Assert.assertEquals("URI(Pattern(http://example.org/res@@table1.id@@))", nodeRel.nodeMaker(x).toString());
    }

    @Test
    public void testConstraintInTripleNoMatch() {
        Assert.assertNull(translate1("?x rdf:type ?x", "engine/type-bridge.n3"));
    }

    @Test
    public void testConstraintInTripleMatch() {
        NodeRelation nodeRel = translate1("?x rdf:type ?x", "engine/object-uricolumn.n3");
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals(Collections.singleton(table1), r.tables());
        Assert.assertTrue(r.condition() instanceof Equality);    // Too lazy to check both sides
        Assert.assertEquals(AliasMap.NO_ALIASES, r.aliases());
        Assert.assertEquals(Collections.singleton(x), nodeRel.variables());
    }

    @Test
    public void testReturnMultipleMatchesForSingleTriplePattern() {
        NodeRelation[] rels = translate("?s ?p ?o", "engine/simple.n3");
        Assert.assertEquals(2, rels.length);
    }

    @Test
    public void testMatchOneOfTwoPropertyBridges() {
        NodeRelation nodeRel = translate1("ex:res1 rdf:type ex:Class1", "engine/simple.n3");
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals(Collections.EMPTY_SET, r.projections());
        Assert.assertEquals(Equality.createAttributeValue(table1id, "1"), r.condition());
    }

    @Test
    public void testAskTwoTriplePatternsNoMatch() {
        Assert.assertNull(translate1(
                "ex:res1 rdf:type ex:Class1 . ex:res1 rdf:type ex:Class2",
                "engine/simple.n3"));
    }

    @Test
    public void testAskTwoTriplePatternsMatch() {
        NodeRelation nodeRel = translate1("ex:res1 rdf:type ex:Class1 . ex:res1 ex:foo ?foo", "engine/simple.n3");
        Assert.assertEquals(Collections.singleton(foo), nodeRel.variables());
        Assert.assertEquals("Literal(Column(T2_table1.foo))", nodeRel.nodeMaker(foo).toString());
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals("Conjunction(" +
                        "Equality(" +
                        "AttributeExpr(@@T1_table1.id@@), " +
                        "Constant(1@T1_table1.id)), " +
                        "Equality(" +
                        "AttributeExpr(@@T2_table1.id@@), " +
                        "Constant(1@T2_table1.id)))",
                r.condition().toString());
    }

    @Test
    public void testTwoTriplePatternsWithJoinMatch() {
        NodeRelation nodeRel = translate1(
                "?x rdf:type ex:Class1 . ?x ex:foo ?foo",
                "engine/simple.n3");
        Assert.assertEquals(2, nodeRel.variables().size());
        Assert.assertEquals("Literal(Column(T2_table1.foo))",
                nodeRel.nodeMaker(foo).toString());
        Assert.assertEquals("URI(Pattern(http://example.org/res@@T1_table1.id@@))",
                nodeRel.nodeMaker(x).toString());
        Relation r = nodeRel.baseRelation();
        Assert.assertEquals(Equality.createAttributeEquality(t1table1id, t2table1id), r.condition());
    }

    private NodeRelation translate1(String pattern, String mappingFile) {
        return translate1(triplesToList(pattern), mappingFile);
    }

    private NodeRelation translate1(List<Triple> triplePatterns, String mappingFile) {
        return translate1(triplePatterns,
                MapFixture.loadPropertyBridges(mappingFile));
    }

    private NodeRelation translate1(String pattern, Collection<TripleRelation> tripleRelations) {
        return translate1(triplesToList(pattern), tripleRelations);
    }

    private NodeRelation translate1(List<Triple> triplePatterns, Collection<TripleRelation> tripleRelations) {
        Collection<NodeRelation> rels = new GraphPatternTranslator(triplePatterns, tripleRelations, true).translate();
        if (rels.isEmpty()) return null;
        Assert.assertEquals(1, rels.size());
        return rels.iterator().next();
    }

    private NodeRelation[] translate(String pattern, String mappingFile) {
        Collection<NodeRelation> rels = new GraphPatternTranslator(triplesToList(pattern),
                MapFixture.loadPropertyBridges(mappingFile), true).translate();
        return rels.toArray(new NodeRelation[0]);
    }

    private List<Triple> triplesToList(String pattern) {
        List<Triple> results = new ArrayList<>();
        String[] parts = pattern.split("\\s+\\.\\s*");
        for (String part : parts) {
            results.add(NodeCreateUtils.createTriple(MapFixture.prefixes(), part));
        }
        return results;
    }
}
