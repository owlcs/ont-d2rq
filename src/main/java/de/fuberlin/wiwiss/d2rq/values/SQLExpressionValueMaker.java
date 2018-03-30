package de.fuberlin.wiwiss.d2rq.values;

import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.algebra.ExpressionProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.expr.Equality;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.nodes.NodeSetFilter;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A value maker that creates its values from a SQL expression.
 * <p>
 * TODO Write unit tests
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class SQLExpressionValueMaker implements ValueMaker {
    private final Expression expression;
    private final ProjectionSpec projection;

    public SQLExpressionValueMaker(Expression expression) {
        this.expression = expression;
        this.projection = new ExpressionProjectionSpec(expression);
    }

    @Override
    public void describeSelf(NodeSetFilter c) {
        c.limitValuesToExpression(expression);
    }

    @Override
    public Set<ProjectionSpec> projectionSpecs() {
        return Collections.singleton(projection);
    }

    @Override
    public String makeValue(ResultRow row) {
        return row.get(projection);
    }

    @Override
    public Expression valueExpression(String value) {
        return Equality.createExpressionValue(expression, value);
    }

    @Override
    public ValueMaker renameAttributes(ColumnRenamer renamer) {
        return new SQLExpressionValueMaker(renamer.applyTo(expression));
    }

    @Override
    public List<OrderSpec> orderSpecs(boolean ascending) {
        return Collections.singletonList(new OrderSpec(expression, ascending));
    }

    @Override
    public int hashCode() {
        return expression.hashCode() ^ 5835034;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SQLExpressionValueMaker)) {
            return false;
        }
        return expression.equals(((SQLExpressionValueMaker) other).expression);
    }

    @Override
    public String toString() {
        return "SQLExpression(" + expression + ")";
    }
}
