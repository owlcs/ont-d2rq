package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

public class SQLSyntaxTest {
    private final static Attribute foo_col1 = new Attribute(null, "foo", "col1");
    private final static Attribute foo_col2 = new Attribute(null, "foo", "col2");
    private final static Attribute bar_col1 = new Attribute(null, "bar", "col1");
    private final static Attribute bar_col2 = new Attribute(null, "bar", "col2");
    private final static Attribute baz_col1 = new Attribute(null, "baz", "col1");
    private final static Alias fooAsBar = new Alias(new RelationName(null, "foo"), new RelationName(null, "bar"));

    @Test
    public void testParseRelationNameNoSchema() {
        RelationName r = SQL.parseRelationName("table");
        Assert.assertEquals("table", r.tableName());
        Assert.assertNull(r.schemaName());
    }

    @Test
    public void testParseRelationNameWithSchema() {
        RelationName r = SQL.parseRelationName("schema.table");
        Assert.assertEquals("table", r.tableName());
        Assert.assertEquals("schema", r.schemaName());
    }

    @Test
    public void testParseInvalidRelationName() {
        try {
            SQL.parseRelationName(".");
            Assert.fail();
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.SQL_INVALID_RELATIONNAME, ex.errorCode());
        }
    }

    @Test
    public void testParseInvalidAttributeName() {
        try {
            SQL.parseAttribute("column");
            Assert.fail("not fully qualified name -- should have failed");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.SQL_INVALID_ATTRIBUTENAME, ex.errorCode());
        }
    }

    @Test
    public void testFindColumnInEmptyExpression() {
        Assert.assertEquals(Collections.EMPTY_SET, SQL.findColumnsInExpression("1+2"));
    }

    @Test
    public void testNumbersInExpressionsAreNotColumns() {
        Assert.assertEquals(Collections.EMPTY_SET, SQL.findColumnsInExpression("1.2"));
    }

    @Test
    public void testFindColumnInColumnName() {
        Assert.assertEquals(Collections.singleton(foo_col1),
                SQL.findColumnsInExpression("foo.col1"));
    }

    @Test
    public void testFindColumnsInExpression() {
        Assert.assertEquals(new HashSet<>(Arrays.asList(foo_col1, bar_col2)),
                SQL.findColumnsInExpression("foo.col1 + bar.col2 = 135"));
    }

    @Test
    public void testFindColumnsInExpression2() {
        Assert.assertEquals(new HashSet<>(Arrays.asList(foo_col1, foo_col2)),
                SQL.findColumnsInExpression("'must.not.match' = foo.col1 && foo.col2 = 'must.not' && foo.col2"));
    }

    @Test
    public void testFindColumnsInExpressionWithSchema() {
        Assert.assertEquals(new HashSet<>(Arrays.asList(new Attribute("s1", "t1", "c1"),
                new Attribute("s2", "t2", "c2"))),
                SQL.findColumnsInExpression("s1.t1.c1 + s2.t2.c2 = 135"));
    }

    @Test
    public void testFindColumnsInExpressionWithStrings() {
        Assert.assertEquals(new HashSet<>(Arrays.asList(foo_col1, foo_col2, bar_col1)),
                SQL.findColumnsInExpression("FUNC('mustnot.match', foo.col1, 'must.not.match') = foo.col2 && FUNC(F2(), bar.col1)"));
    }

    @Test
    public void testFindColumnsInExpressionWithStrings2() { // may occur with d2rq:sqlExpression
        Assert.assertEquals(new HashSet<>(Collections.singletonList(foo_col1)),
                SQL.findColumnsInExpression("FUNC('mustnot.match', foo.col1, 'must.not.match')"));
    }

    @Test
    public void testReplaceColumnsInExpressionWithAliasMap() {
        Alias alias = new Alias(new RelationName(null, "foo"), new RelationName(null, "bar"));
        AliasMap fooAsBar = new AliasMap(Collections.singleton(alias));
        Assert.assertEquals("bar.col1",
                SQL.replaceColumnsInExpression("foo.col1", fooAsBar));
        Assert.assertEquals("LEN(bar.col1) > 0",
                SQL.replaceColumnsInExpression("LEN(foo.col1) > 0", fooAsBar));
        Assert.assertEquals("baz.col1",
                SQL.replaceColumnsInExpression("baz.col1", fooAsBar));
        Assert.assertEquals("fooo.col1",
                SQL.replaceColumnsInExpression("fooo.col1", fooAsBar));
        Assert.assertEquals("ofoo.col1",
                SQL.replaceColumnsInExpression("ofoo.col1", fooAsBar));
    }

    @Test
    public void testReplaceColumnsWithSchemaInExpressionWithAliasMap() {
        Alias alias = new Alias(new RelationName("schema", "foo"), new RelationName("schema", "bar"));
        AliasMap fooAsBar = new AliasMap(Collections.singleton(alias));
        Assert.assertEquals("schema.bar.col1",
                SQL.replaceColumnsInExpression("schema.foo.col1", fooAsBar));
    }

    @Test
    public void testReplaceColumnsInExpressionWithColumnReplacer() {
        Map<Attribute, Attribute> map = new HashMap<>();
        map.put(foo_col1, bar_col2);
        ColumnRenamerMap col1ToCol2 = new ColumnRenamerMap(map);
        Assert.assertEquals("bar.col2", SQL.replaceColumnsInExpression("foo.col1", col1ToCol2));
        Assert.assertEquals("LEN(bar.col2) > 0", SQL.replaceColumnsInExpression("LEN(foo.col1) > 0", col1ToCol2));
        Assert.assertEquals("foo.col3", SQL.replaceColumnsInExpression("foo.col3", col1ToCol2));
        Assert.assertEquals("foo.col11", SQL.replaceColumnsInExpression("foo.col11", col1ToCol2));
        Assert.assertEquals("ofoo.col1", SQL.replaceColumnsInExpression("ofoo.col1", col1ToCol2));
    }

    @Test
    public void testParseAliasIsCaseInsensitive() {
        Assert.assertEquals(fooAsBar, SQL.parseAlias("foo AS bar"));
        Assert.assertEquals(fooAsBar, SQL.parseAlias("foo as bar"));
    }

    @Test
    public void testParseAlias() {
        Assert.assertEquals(new Alias(new RelationName(null, "table1"), new RelationName("schema", "table2")),
                SQL.parseAlias("table1 AS schema.table2"));
    }

    @Test
    public void testParseInvalidAlias() {
        try {
            SQL.parseAlias("asdf");
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.SQL_INVALID_ALIAS, ex.errorCode());
        }
    }

    @Test
    public void testParseInvalidJoin() {
        try {
            SQL.parseJoins(Collections.singleton("asdf"));
        } catch (D2RQException ex) {
            Assert.assertEquals(D2RQException.SQL_INVALID_JOIN, ex.errorCode());
        }
    }

    @Test
    public void testParseJoinOneCondition() {
        Set<Join> joins = SQL.parseJoins(Collections.singleton("foo.col1 = bar.col2"));
        Assert.assertEquals(1, joins.size());
        Join join = joins.iterator().next();
        Assert.assertEquals(Collections.singletonList(bar_col2), join.attributes1());
        Assert.assertEquals(Collections.singletonList(foo_col1), join.attributes2());
    }

    @Test
    public void testParseJoinTwoConditionsOnSameTables() {
        Set<Join> joins = SQL.parseJoins(Arrays.asList("foo.col1 = bar.col1", "foo.col2 = bar.col2"));
        Assert.assertEquals(1, joins.size());
        Join join = joins.iterator().next();
        Assert.assertEquals(Arrays.asList(bar_col1, bar_col2), join.attributes1());
        Assert.assertEquals(Arrays.asList(foo_col1, foo_col2), join.attributes2());
        Assert.assertEquals(foo_col1, join.equalAttribute(bar_col1));
    }

    @Test
    public void testParseJoinTwoConditionsOnDifferentTables() {
        Set<Join> joins = SQL.parseJoins(Arrays.asList("foo.col1 <= bar.col1", "foo.col2 => baz.col1", "foo.col2 = bar.col1"));
        Assert.assertEquals(3, joins.size());
        Assert.assertEquals(new HashSet<>(Arrays.asList(new Join(bar_col1, foo_col1, Join.DIRECTION_LEFT),
                new Join(baz_col1, foo_col2, Join.DIRECTION_RIGHT),
                new Join(foo_col2, bar_col1, Join.DIRECTION_UNDIRECTED))),
                joins);
    }
}
