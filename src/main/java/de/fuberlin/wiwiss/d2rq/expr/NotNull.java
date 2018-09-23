package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Set;

public class NotNull extends Expression {

    public static Expression create(Expression expr) {
        return new NotNull(expr);
    }

    private Expression expr;

    private NotNull(Expression expr) {
        this.expr = expr;
    }

    @Override
    public Set<Attribute> attributes() {
        return expr.attributes();
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
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        return NotNull.create(columnRenamer.applyTo(expr));
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return expr.toSQL(database, aliases) + " IS NOT NULL";
    }

    @Override
    public String toString() {
        return "NotNull(" + this.expr + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof NotNull)) {
            return false;
        }
        NotNull otherExpression = (NotNull) other;
        return expr.equals(otherExpression.expr);
    }

    @Override
    public int hashCode() {
        return this.expr.hashCode() ^ 58473;
    }
}
