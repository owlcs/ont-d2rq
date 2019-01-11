package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.HashSet;
import java.util.Set;


public abstract class BinaryOperator extends Expression {

    protected final Expression expr1;
    protected final Expression expr2;
    protected final String operator;

    private final Set<Attribute> columns = new HashSet<Attribute>();


    protected BinaryOperator(Expression expr1, Expression expr2, String operator) {
        this.expr1 = expr1;
        this.expr2 = expr2;
        this.operator = operator;
        columns.addAll(expr1.attributes());
        columns.addAll(expr2.attributes());
    }

    @Override
    public Set<Attribute> attributes() {
        return columns;
    }

    @Override
    public boolean isFalse() {
        return false;
    }

    @Override
    public boolean isTrue() {
        return false;
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return expr1.toSQL(database, aliases) + " " + operator + " " + expr2.toSQL(database, aliases);
    }

    @Override
    public String toString() {
        return operator + "(" + expr1 + ", " + expr2 + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BinaryOperator)) {
            return false;
        }
        BinaryOperator otherBinaryOperator = (BinaryOperator) other;

        return expr1.equals(otherBinaryOperator.expr1) && expr2.equals(otherBinaryOperator.expr2) && operator.equals(otherBinaryOperator.operator);
    }

    @Override
    public int hashCode() {
        return operator.hashCode() ^ expr1.hashCode() ^ expr2.hashCode();
    }
}
