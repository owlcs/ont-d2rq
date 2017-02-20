package de.fuberlin.wiwiss.d2rq.expr;

import java.util.Arrays;
import java.util.Collections;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import junit.framework.TestCase;

public class ConcatenationTest extends TestCase {

    public void testCreateEmpty() {
        assertEquals(new Constant(""), Concatenation.create(Collections.emptyList()));
    }

    public void testCreateOnePart() {
        Expression expr = new AttributeExpr(new Attribute(null, "table", "col"));
        assertEquals(expr, Concatenation.create(Collections.singletonList(expr)));
    }

    public void testTwoParts() {
        Expression expr1 = new Constant("mailto:");
        Expression expr2 = new AttributeExpr(new Attribute(null, "user", "email"));
        Expression concat = Concatenation.create(Arrays.asList(expr1, expr2));
        assertEquals("Concatenation(Constant(mailto:), AttributeExpr(@@user.email@@))",
                concat.toString());
    }

    public void testFilterEmptyParts() {
        Expression empty = new Constant("");
        Expression expr1 = new Constant("aaa");
        assertEquals(expr1, Concatenation.create(Arrays.asList(
                empty, empty, expr1, empty)));
    }
}
