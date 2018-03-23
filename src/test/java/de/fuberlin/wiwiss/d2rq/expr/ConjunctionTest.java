package de.fuberlin.wiwiss.d2rq.expr;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ConjunctionTest {
    private final static String sql1 = "papers.publish = 1";
    private final static String sql2 = "papers.rating > 4";
    private final static String sql3 = "papers.reviewed = 1";
    private final static Expression expr1 = SQLExpression.create(sql1);
    private final static Expression expr2 = SQLExpression.create(sql2);
    private final static Expression expr3 = SQLExpression.create(sql3);
    private final static Expression conjunction12 =
            Conjunction.create(Arrays.asList(expr1, expr2));
    private final static Expression conjunction123 =
            Conjunction.create(Arrays.asList(expr1, expr2, expr3));
    private final static Expression conjunction21 =
            Conjunction.create(Arrays.asList(expr2, expr1));

    @Test
    public void testEmptyConjunctionIsTrue() {
        Assert.assertEquals(Expression.TRUE, Conjunction.create(Collections.emptySet()));
    }

    @Test
    public void testSingletonConjunctionIsSelf() {
        Expression e = SQLExpression.create("foo");
        Assert.assertEquals(e, Conjunction.create(Collections.singleton(e)));
    }

    @Test
    public void testCreateConjunction() {
        Assert.assertFalse(conjunction12.isTrue());
        Assert.assertFalse(conjunction12.isFalse());
    }

    @Test
    public void testToString() {
        Assert.assertEquals("Conjunction(SQL(papers.publish = 1), SQL(papers.rating > 4))",
                conjunction12.toString());
    }

    @Test
    public void testTrueExpressionsAreSkipped() {
        Assert.assertEquals(Expression.TRUE, Conjunction.create(Arrays.asList(Expression.TRUE, Expression.TRUE)));
        Assert.assertEquals(expr1, Conjunction.create(
                Arrays.asList(Expression.TRUE, expr1, Expression.TRUE)));
        Assert.assertEquals(conjunction12, Conjunction.create(Arrays.asList(Expression.TRUE, expr1, Expression.TRUE, expr2)));
    }

    @Test
    public void testFalseCausesFailure() {
        Assert.assertEquals(Expression.FALSE, Conjunction.create(Collections.singleton(Expression.FALSE)));
        Assert.assertEquals(Expression.FALSE, Conjunction.create(Arrays.asList(expr1, Expression.FALSE)));
    }

    @Test
    public void testRemoveDuplicates() {
        Assert.assertEquals(expr1, Conjunction.create(Arrays.asList(expr1, expr1)));
    }

    @Test
    public void testFlatten() {
        Assert.assertEquals(conjunction123, Conjunction.create(Arrays.asList(conjunction12, expr3)));
    }

    @Test
    public void testOrderDoesNotAffectEquality() {
        Assert.assertEquals(conjunction12, conjunction21);
        Assert.assertEquals(conjunction12.hashCode(), conjunction21.hashCode());
    }
}