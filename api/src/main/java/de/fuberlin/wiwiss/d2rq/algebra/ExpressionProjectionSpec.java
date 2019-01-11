package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.expr.NotNull;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Set;

public class ExpressionProjectionSpec implements ProjectionSpec {
    private Expression expression;
    private final String name;

    public ExpressionProjectionSpec(Expression expression) {
        this.expression = expression;
        this.name = "expr" + Integer.toHexString(expression.hashCode());
    }

    @Override
    public ProjectionSpec renameAttributes(ColumnRenamer renamer) {
        return new ExpressionProjectionSpec(renamer.applyTo(expression));
    }

    @Override
    public Set<Attribute> requiredAttributes() {
        return expression.attributes();
    }

    @Override
    public Expression toExpression() {
        return expression;
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return expression.toSQL(database, aliases) + " AS " + name;
    }

    @Override
    public Expression notNullExpression(ConnectedDB database, AliasMap aliases) {
        return NotNull.create(expression);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return (other instanceof ExpressionProjectionSpec)
                && expression.equals(((ExpressionProjectionSpec) other).expression);
    }

    @Override
    public int hashCode() {
        return expression.hashCode() ^ 684036;
    }

    @Override
    public String toString() {
        return "ProjectionSpec(" + expression + " AS " + name + ")";
    }

    /**
     * Compares columns alphanumerically by qualified name, case sensitive.
     * Attributes with schema are larger than attributes without schema.
     */
    @Override
    public int compareTo(ProjectionSpec other) {
        if (!(other instanceof ExpressionProjectionSpec)) {
            return 1;
        }
        ExpressionProjectionSpec otherExpr = (ExpressionProjectionSpec) other;
        return this.name.compareTo(otherExpr.name);
    }
}