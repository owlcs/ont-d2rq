package de.fuberlin.wiwiss.d2rq.optimizer;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.NodeRelation;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.engine.GraphPatternTranslator;
import de.fuberlin.wiwiss.d2rq.engine.MapFixture;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.optimizer.expr.TransformExprToSQLApplyer;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.expr.nodevalue.NodeValueNode;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class ExprTransformTest2 {


    private NodeRelation search(String tableName, String attributeName, NodeRelation[] relation) {
        for (NodeRelation rel : relation) {
            for (ProjectionSpec p : rel.baseRelation().projections()) {
                Attribute attribute = (Attribute) p;
                if (attribute.tableName().equals(tableName) && attribute.attributeName().equals(attributeName))
                    return rel;
            }
        }
        return null;
    }


    @Test
    public void testLang() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"), RDFS.label.asNode(), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation label_fr_be = search("table1", "label_fr_be", rels);
        NodeRelation label_en = search("table1", "label_en", rels);
        NodeRelation label_noLang = search("table1", "label", rels);

        Expr filterFR = new E_Equals(new E_Lang(new ExprVar("o")), NodeValue.makeString("fr"));
        Expr filterEN_TAG_EN = new E_Equals(new E_Lang(new ExprVar("o")), NodeValue.makeNode("en", "en", (String) null));
        Expr filterFR_BE = new E_Equals(new E_Lang(new ExprVar("o")), NodeValue.makeString("fr-BE"));
        Expr filter = new E_Equals(new E_Lang(new ExprVar("o")), NodeValue.makeString(""));

        Assert.assertEquals("LANG(label_fr_be) = \"fr\" should be FALSE", Expression.FALSE, TransformExprToSQLApplyer.convert(filterFR, label_fr_be));
        Assert.assertEquals("LANG(label_en) = \"fr\" should be FALSE", Expression.FALSE, TransformExprToSQLApplyer.convert(filterFR, label_en));
        Assert.assertEquals("LANG(label_fr_be) = \"fr_be\" should be TRUE", Expression.TRUE, TransformExprToSQLApplyer.convert(filterFR_BE, label_fr_be));
        Assert.assertEquals("LANG(label_en) = \"en\"@en should be FALSE", Expression.FALSE, TransformExprToSQLApplyer.convert(filterEN_TAG_EN, label_en));
        Assert.assertEquals("LANG(label_noLang) = \"\" should be TRUE", Expression.TRUE, TransformExprToSQLApplyer.convert(filter, label_noLang));
    }

    @Test
    public void testLangMatches() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"), RDFS.label.asNode(), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation label_fr_be = search("table1", "label_fr_be", rels);
        NodeRelation label_en = search("table1", "label_en", rels);
        NodeRelation label = search("table1", "label", rels);

        Expr filterFR = new E_LangMatches(new E_Lang(new ExprVar("o")), NodeValue.makeString("fr"));
        Expr filterEN = new E_LangMatches(new E_Lang(new ExprVar("o")), NodeValue.makeString("en"));
        Expr filterFR_BE = new E_LangMatches(new E_Lang(new ExprVar("o")), NodeValue.makeString("fr-BE"));
        Expr filterALL = new E_LangMatches(new E_Lang(new ExprVar("o")), NodeValue.makeString("*"));

        Assert.assertEquals("LANGMATCHES(LANG(label_fr_be), \"fr\") should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterFR, label_fr_be));
        Assert.assertEquals("LANGMATCHES(LANG(label_en), \"fr\") should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(filterFR, label_en));
        Assert.assertEquals("LANGMATCHES(LANG(label_en), \"en\") should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterEN, label_en));
        Assert.assertEquals("LANGMATCHES(LANG(label_fr_BE, \"fr_BE\") should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterFR_BE, label_fr_be));

        Assert.assertEquals("LANGMATCHES(LANG(label), \"en\") should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(filterEN, label));
        Assert.assertEquals("LANGMATCHES(LANG(label_fr_BE, \"*\") should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterALL, label_fr_be));
        Assert.assertEquals("LANGMATCHES(LANG(label_en, \"*\") should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterALL, label_en));
        Assert.assertEquals("LANGMATCHES(LANG(label, \"*\") should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(filterALL, label));
    }

    @Test
    public void testIsLiteral() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"), RDFS.label.asNode(), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation label = search("table1", "label", rels);
        NodeRelation label_en = search("table1", "label_en", rels);

        pattern.clear();
        pattern.add(Triple.create(NodeFactory.createVariable("s"),
                NodeFactory.createURI("http://example.org/value"), NodeFactory.createVariable("o")));
        rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation intvalue = search("table2", "intvalue", rels);

        Expr subject = new E_IsLiteral(new ExprVar("s"));
        Expr object = new E_IsLiteral(new ExprVar("o"));

        Assert.assertEquals("ISLITERAL(literal) should be TRUE",
                Expression.TRUE, TransformExprToSQLApplyer.convert(object, label));
        Assert.assertEquals("ISLITERAL(literal@lang) should be TRUE",
                Expression.TRUE, TransformExprToSQLApplyer.convert(object, label_en));
        Assert.assertEquals("ISLITERAL(uri) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(subject, label));
        Assert.assertEquals("ISLITERAL(intvalue) should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(object, intvalue));
        Assert.assertEquals("ISLITERAL(blanknode) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(subject, intvalue));
    }

    @Test
    public void testIsIRI() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"), RDFS.label.asNode(), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation label = search("table1", "label", rels);
        NodeRelation label_en = search("table1", "label_en", rels);

        pattern.clear();
        pattern.add(Triple.create(NodeFactory.createVariable("s"),
                NodeFactory.createURI("http://example.org/value"), NodeFactory.createVariable("o")));
        rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation intvalue = search("table2", "intvalue", rels);

        Expr subject = new E_IsIRI(new ExprVar("s"));
        Expr object = new E_IsIRI(new ExprVar("o"));

        Assert.assertEquals("ISIRI(literal) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(object, label));
        Assert.assertEquals("ISIRI(literal@lang) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(object, label_en));
        Assert.assertEquals("ISIRI(uri) should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(subject, label));
        Assert.assertEquals("ISIRI(intvalue) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(object, intvalue));
        Assert.assertEquals("ISIRI(blanknode) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(subject, intvalue));
    }


    @Test
    public void testIsBlank() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"), RDFS.label.asNode(), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation label = search("table1", "label", rels);
        NodeRelation label_en = search("table1", "label_en", rels);

        pattern.clear();
        pattern.add(Triple.create(NodeFactory.createVariable("s"),
                NodeFactory.createURI("http://example.org/value"), NodeFactory.createVariable("o")));
        rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation intvalue = search("table2", "intvalue", rels);

        Expr subject = new E_IsBlank(new ExprVar("s"));
        Expr object = new E_IsBlank(new ExprVar("o"));

        Assert.assertEquals("ISBLANK(literal) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(object, label));
        Assert.assertEquals("ISBLANK(literal@lang) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(object, label_en));
        Assert.assertEquals("ISBLANK(uri) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(subject, label));
        Assert.assertEquals("ISBLANK(intvalue) should be FALSE", Expression.FALSE,
                TransformExprToSQLApplyer.convert(object, intvalue));
        Assert.assertEquals("ISBLANK(blanknode) should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(subject, intvalue));
    }

    @Test
    public void testDataType() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"),
                NodeFactory.createURI("http://example.org/value"), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation intvalue = search("table2", "intvalue", rels);
        NodeRelation value = search("table2", "value", rels);

        pattern.clear();
        pattern.add(Triple.create(NodeFactory.createVariable("s"), RDFS.label.asNode(), NodeFactory.createVariable("o")));
        rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation langliteral = search("table1", "label_en", rels);

        Expr filterint = new E_Equals(new E_Datatype(new ExprVar("o")),
                NodeValueNode.makeNode(NodeFactory.createURI(XSDDatatype.XSDint.getURI())));
        Expr filterstring = new E_Equals(new E_Datatype(new ExprVar("o")),
                NodeValueNode.makeNode(NodeFactory.createURI(XSDDatatype.XSDstring.getURI())));

        Assert.assertEquals("DATATYPE(intliteral) = xsd:int should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterint, intvalue));
        Assert.assertEquals("DATATYPE(simpleliteral) = xsd:string should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterstring, value));
        Assert.assertEquals("DATATYPE(langliteral) = xsd:string should be TRUE", Expression.TRUE,
                TransformExprToSQLApplyer.convert(filterstring, langliteral));
    }

    @Test
    public void testDisjunction() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"),
                NodeFactory.createURI("http://example.org/value"), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation intvalue = search("table2", "intvalue", rels);
        Assert.assertNotNull(intvalue);

        Expr disjunction = new E_LogicalOr(new E_Equals(new ExprVar("o"),
                NodeValue.makeNode("1", XSDDatatype.XSDint)),
                new E_Equals(new ExprVar("o"), NodeValue.makeNode("2", XSDDatatype.XSDint)));

        Expression result = TransformExprToSQLApplyer.convert(disjunction, intvalue);
        TypedNodeMaker nm = (TypedNodeMaker) intvalue.nodeMaker(Var.alloc("o"));
        Expression e1 = nm.valueMaker().valueExpression("1");
        Expression e2 = nm.valueMaker().valueExpression("2");
        Expression expected = e1.or(e2);

        Assert.assertEquals("?o = \"1\"^^xsd:int || ?o = \"2\"^^xsd:int", expected, result);
    }

    @Test
    public void testSameTerm() {
        List<Triple> pattern = new ArrayList<>();
        pattern.add(Triple.create(NodeFactory.createVariable("s"),
                NodeFactory.createURI("http://example.org/value"), NodeFactory.createVariable("o")));
        NodeRelation[] rels = translate(pattern, "optimizer/filtertests.n3");

        NodeRelation intvalue = search("table2", "intvalue", rels);
        Assert.assertNotNull(intvalue);

        Expr sameTerm = new E_SameTerm(new ExprVar("o"), NodeValue.makeNode("1", XSDDatatype.XSDint));

        Expression result = TransformExprToSQLApplyer.convert(sameTerm, intvalue);
        TypedNodeMaker nm = (TypedNodeMaker) intvalue.nodeMaker(Var.alloc("o"));
        Expression expected = nm.valueMaker().valueExpression("1");

        Assert.assertEquals("sameTerm(?o, \"1\"^^xsd:int)", expected, result);

        sameTerm = new E_SameTerm(new ExprVar("o"), NodeValue.makeNode("1", XSDDatatype.XSDdecimal));

        result = TransformExprToSQLApplyer.convert(sameTerm, intvalue);

        Assert.assertEquals("sameTerm(?o, \"1\"^^xsd:decimal)", Expression.FALSE, result);
    }

    @SuppressWarnings("SameParameterValue")
    private NodeRelation[] translate(List<Triple> pattern, String mappingFile) {
        Collection<NodeRelation> rels = new GraphPatternTranslator(pattern,
                MapFixture.loadPropertyBridges(mappingFile), true).translate();
        return rels.toArray(new NodeRelation[0]);
    }

}
