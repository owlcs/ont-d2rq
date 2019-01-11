package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamerMap;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class SQLExpressionTest {

    @Test
    public void testCreate() {
        Expression e = SQLExpression.create("papers.publish = 1");
        Assert.assertEquals("SQL(papers.publish = 1)", e.toString());
        Assert.assertFalse(e.isTrue());
        Assert.assertFalse(e.isFalse());
    }

    @Test
    public void testFindsColumns() {
        Expression e = SQLExpression.create("papers.publish = 1 AND papers.url1 != 'http://www.example.com\\'http://www.example.com' AND papers.url2 != 'http://www.example.com\\\\\\\\http://www.example.com' AND papers.rating > 4");
        Set<Attribute> expectedColumns = new HashSet<>(Arrays.asList(
                new Attribute(null, "papers", "publish"),
                new Attribute(null, "papers", "url1"),
                new Attribute(null, "papers", "url2"),
                new Attribute(null, "papers", "rating")));
        Assert.assertEquals(expectedColumns, e.attributes());
    }

    @Test
    public void testToString() {
        Expression e = SQLExpression.create("papers.publish = 1");
        Assert.assertEquals("SQL(papers.publish = 1)", e.toString());
    }

    @Test
    public void testTwoExpressionsAreEqual() {
        Assert.assertEquals(SQLExpression.create("1=1"), SQLExpression.create("1=1"));
        Assert.assertEquals(SQLExpression.create("1=1").hashCode(), SQLExpression.create("1=1").hashCode());
    }

    @Test
    public void testTwoExpressionsAreNotEqual() {
        Assert.assertNotEquals(SQLExpression.create("1=1"), SQLExpression.create("2=2"));
        Assert.assertNotEquals(SQLExpression.create("1=1").hashCode(), SQLExpression.create("2=2").hashCode());
    }

    @Test
    public void testRenameColumnsWithAliasMap() {
        Alias a = new Alias(new RelationName(null, "foo"), new RelationName(null, "bar"));
        Assert.assertEquals(SQLExpression.create("bar.col1 = baz.col1"),
                SQLExpression.create("foo.col1 = baz.col1").renameAttributes(
                        new AliasMap(Collections.singleton(a))));
    }

    @Test
    public void testRenameColumnsWithColumnReplacer() {
        Map<Attribute, Attribute> map = new HashMap<Attribute, Attribute>();
        map.put(new Attribute(null, "foo", "col1"), new Attribute(null, "foo", "col2"));
        Assert.assertEquals(SQLExpression.create("foo.col2=foo.col3"),
                SQLExpression.create("foo.col1=foo.col3").renameAttributes(new ColumnRenamerMap(map)));
    }
}
