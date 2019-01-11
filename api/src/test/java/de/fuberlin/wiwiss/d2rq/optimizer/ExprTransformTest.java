/**
 */
package de.fuberlin.wiwiss.d2rq.optimizer;

import de.fuberlin.wiwiss.d2rq.engine.TransformFilterCNF.DeMorganLawApplyer;
import de.fuberlin.wiwiss.d2rq.engine.TransformFilterCNF.DistributiveLawApplyer;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprNone;
import org.apache.jena.sparql.util.ExprUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author dorgon
 */
public class ExprTransformTest {

    /**
     * To fix test due to upgrade from jena 3.0.1 -&gt; 3.6.0
     *
     * @param node {@link ExprNone}
     * @return String
     */
    public static String asString(Expr node) {
        return ExprUtils.fmtSPARQL(node);
    }

    @Test
    public void testExprDeMorganDoubleNotA() {
        Expr expr = ExprUtils.parse("!(!(?a))");
        DeMorganLawApplyer apply = new DeMorganLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("?a", asString(apply.result()));
    }

    @Test
    public void testExprDeMorganDoubleNotAB() {
        Expr expr = ExprUtils.parse("!(!(?a && ?b))");
        DeMorganLawApplyer apply = new DeMorganLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ?a && ?b )", asString(apply.result()));
    }

    @Test
    public void testExprDeMorganOr() {
        Expr expr = ExprUtils.parse("!(?a || ?b)");
        DeMorganLawApplyer apply = new DeMorganLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ( ! ?a ) && ( ! ?b ) )", asString(apply.result()));
    }

    @Test
    public void testExprDeMorganAndDontChange() {
        Expr expr = ExprUtils.parse("!(?a && ?b)");
        DeMorganLawApplyer apply = new DeMorganLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ! ( ?a && ?b ) )", asString(apply.result()));
    }

    @Test
    public void testExprDistributiveABOrC() {
        Expr expr = ExprUtils.parse("(( ?a && ?b ) || ?c )");
        DistributiveLawApplyer apply = new DistributiveLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ( ?a || ?c ) && ( ?b || ?c ) )", asString(apply.result()));
    }

    @Test
    public void testExprDistributiveCOrAB() {
        Expr expr = ExprUtils.parse("?c || ( ?a && ?b )");
        DistributiveLawApplyer apply = new DistributiveLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ( ?c || ?a ) && ( ?c || ?b ) )", asString(apply.result()));
    }

    @Test
    public void testExprDistributiveAndDontChange() {
        Expr expr = ExprUtils.parse("!(?a || ?b) && ?c");
        DistributiveLawApplyer apply = new DistributiveLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ( ! ( ?a || ?b ) ) && ?c )", asString(apply.result()));
    }

    @Test
    public void testExprDistributiveOrComplex() {
        Expr expr = ExprUtils.parse("(?c || ( ?a && ?b )) || (?d && ?e)");
        DistributiveLawApplyer apply = new DistributiveLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ( ( ( ?c || ?a ) || ?d ) && ( ( ?c || ?b ) || ?d ) ) && ( ( ( ?c || ?a ) || ?e ) && ( ( ?c || ?b ) || ?e ) ) )", asString(apply.result())); // correct
    }

    @Test
    public void testExprDistributiveABC() {
        Expr expr = ExprUtils.parse("(( ?a && ?b ) && ?c )");
        DistributiveLawApplyer apply = new DistributiveLawApplyer();
        expr.visit(apply);
        Assert.assertEquals("( ( ?a && ?b ) && ?c )", asString(apply.result()));
    }

    @Test
    public void testExprDistributiveUsingFunctions() {
        Expr expr = ExprUtils.parse("( ( ( ?n = 1 ) && bound(?pref) ) && bound(?n) )");
        DistributiveLawApplyer apply = new DistributiveLawApplyer();
        expr.visit(apply);
        Assert.assertEquals("( ( ( ?n = 1 ) && bound(?pref) ) && bound(?n) )", asString(apply.result()));
    }

    @Test
    public void testDeMorganNotEqual() {
        Expr expr = ExprUtils.parse("?x != ?z");
        DeMorganLawApplyer apply = new DeMorganLawApplyer();
        expr.visit(apply);
        Assert.assertNotNull(apply.result());
        Assert.assertEquals("( ?x != ?z )", asString(apply.result()));
    }
}
