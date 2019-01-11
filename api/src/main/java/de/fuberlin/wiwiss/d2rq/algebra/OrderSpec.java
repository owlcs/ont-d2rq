package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Collections;
import java.util.List;

public class OrderSpec {
    public final static List<OrderSpec> NONE = Collections.emptyList();

    private Expression expression;
    private boolean ascending;

    public OrderSpec(Expression expression) {
        this(expression, true);
    }

    public OrderSpec(Expression expression, boolean ascending) {
        this.expression = expression;
        this.ascending = ascending;
    }

    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return expression.toSQL(database, aliases) + (ascending ? "" : " DESC");
    }

    public Expression expression() {
        return expression;
    }

    public boolean isAscending() {
        return ascending;
    }

    @Override
    public String toString() {
        return (ascending ? "ASC(" : "DESC(") + expression + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other instanceof OrderSpec) {
            return ascending == ((OrderSpec) other).ascending &&
                    expression.equals(((OrderSpec) other).expression);
        }
        return false;
    }

    public int hashCode() {
        return Boolean.valueOf(ascending).hashCode() ^ expression.hashCode();
    }
}
