package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ConcatenationTest {

    @Test
    public void testCreateEmpty() {
        Assert.assertEquals(new Constant(""), Concatenation.create(Collections.emptyList()));
    }

    @Test
    public void testCreateOnePart() {
        Expression expr = new AttributeExpr(new Attribute(null, "table", "col"));
        Assert.assertEquals(expr, Concatenation.create(Collections.singletonList(expr)));
    }

    @Test
    public void testTwoParts() {
        Expression expr1 = new Constant("mailto:");
        Expression expr2 = new AttributeExpr(new Attribute(null, "user", "email"));
        Expression concat = Concatenation.create(Arrays.asList(expr1, expr2));
        Assert.assertEquals("Concatenation(Constant(mailto:), AttributeExpr(@@user.email@@))",
                concat.toString());
    }

    @Test
    public void testFilterEmptyParts() {
        Expression empty = new Constant("");
        Expression expr1 = new Constant("aaa");
        Assert.assertEquals(expr1, Concatenation.create(Arrays.asList(
                empty, empty, expr1, empty)));
    }
}
