package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.expr.SQLExpression;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RelationTest {
    private ConnectedDB db;
    private Relation rel1;

    @Before
    public void setUp() {
        db = DummyDB.create();
        rel1 = Relation.createSimpleRelation(db,
                new Attribute[]{new Attribute(null, "foo", "bar")});
    }

    @Test
    public void testSelectFalseIsEmptyRelation() {
        Assert.assertEquals(Relation.EMPTY, rel1.select(Expression.FALSE));
    }

    @Test
    public void testTrueRelationIsTrivial() {
        Assert.assertTrue(Relation.TRUE.isTrivial());
    }

    @Test
    public void testConditionWithNoSelectColumnsIsNotTrivial() {
        Assert.assertFalse(Relation
                .createSimpleRelation(db, new Attribute[]{})
                .select(SQLExpression.create("foo.bar = 1")).isTrivial());
    }

    @Test
    public void testQueryWithSelectColumnsIsNotTrivial() {
        Assert.assertFalse(rel1.isTrivial());
    }
}
