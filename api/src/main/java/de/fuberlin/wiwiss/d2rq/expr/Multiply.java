package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;


public class Multiply extends BinaryOperator {

    public Multiply(Expression expr1, Expression expr2) {
        super(expr1, expr2, "*");
    }

    @Override
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        return new Multiply(expr1.renameAttributes(columnRenamer), expr2.renameAttributes(columnRenamer));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Multiply)) {
            return false;
        }
        Multiply otherMultiply = (Multiply) other;
        if (expr1.equals(otherMultiply.expr1) && expr2.equals(otherMultiply.expr2)) {
            return true;
        }
        return expr1.equals(otherMultiply.expr2) && expr2.equals(otherMultiply.expr1);
    }


}
