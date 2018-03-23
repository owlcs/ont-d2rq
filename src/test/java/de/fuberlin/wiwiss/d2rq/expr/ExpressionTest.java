package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType.GenericType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ExpressionTest {
    private AliasMap aliases;

    @Before
    public void setUp() {
        aliases = AliasMap.create1(new RelationName(null, "table"), new RelationName(null, "alias"));
    }

    @Test
    public void testTrue() {
        Assert.assertEquals("TRUE", Expression.TRUE.toString());
        Assert.assertEquals(Expression.TRUE, Expression.TRUE);
        Assert.assertTrue(Expression.TRUE.isTrue());
        Assert.assertFalse(Expression.TRUE.isFalse());
    }

    @Test
    public void testFalse() {
        Assert.assertEquals("FALSE", Expression.FALSE.toString());
        Assert.assertEquals(Expression.FALSE, Expression.FALSE);
        Assert.assertFalse(Expression.FALSE.isTrue());
        Assert.assertTrue(Expression.FALSE.isFalse());
    }

    @Test
    public void testTrueNotEqualFalse() {
        Assert.assertFalse(Expression.TRUE.equals(Expression.FALSE));
    }

    @Test
    public void testConstant() {
        Expression expr = new Constant("foo");
        Assert.assertTrue(expr.attributes().isEmpty());
        Assert.assertFalse(expr.isFalse());
        Assert.assertFalse(expr.isTrue());
        Assert.assertEquals(expr, expr.renameAttributes(aliases));
    }

    @Test
    public void testConstantEquals() {
        Assert.assertTrue(new Constant("foo").equals(new Constant("foo")));
        Assert.assertFalse(new Constant("foo").equals(new Constant("bar")));
        Assert.assertFalse(new Constant("foo").equals(Expression.TRUE));
    }

    @Test
    public void testConstantHashCode() {
        Assert.assertEquals(new Constant("foo").hashCode(), new Constant("foo").hashCode());
        Assert.assertFalse(new Constant("foo").hashCode() == new Constant("bar").hashCode());
    }

    @Test
    public void testConstantToString() {
        Assert.assertEquals("Constant(foo)", new Constant("foo").toString());
    }

    @Test
    public void testConstantToSQL() {
        Assert.assertEquals("'foo'", new Constant("foo").toSQL(new DummyDB(), AliasMap.NO_ALIASES));
    }

    @Test
    public void testConstantToSQLWithType() {
        Attribute attribute = SQL.parseAttribute("table.col1");
        DummyDB db = new DummyDB(Collections.singletonMap("table.col1", GenericType.NUMERIC));
        Assert.assertEquals("42", new Constant("42", attribute).toSQL(db, AliasMap.NO_ALIASES));
    }

    @Test
    public void testConstantToSQLWithTypeAndAlias() {
        Attribute aliasedAttribute = SQL.parseAttribute("alias.col1");
        DummyDB db = new DummyDB(Collections.singletonMap("table.col1", GenericType.NUMERIC));
        Assert.assertEquals("42", new Constant("42", aliasedAttribute).toSQL(db, aliases));
    }

    @Test
    public void testConstantTypeAttributeIsRenamed() {
        Attribute attribute = SQL.parseAttribute("table.col1");
        Assert.assertEquals("Constant(42@alias.col1)",
                new Constant("42", attribute).renameAttributes(aliases).toString());
    }
}
