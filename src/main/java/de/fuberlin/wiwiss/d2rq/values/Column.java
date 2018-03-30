package de.fuberlin.wiwiss.d2rq.values;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.expr.AttributeExpr;
import de.fuberlin.wiwiss.d2rq.expr.Equality;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.nodes.NodeSetFilter;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A {@link ValueMaker} that takes its values from a single column.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class Column implements ValueMaker {
    private Attribute attribute;
    private Set<ProjectionSpec> attributeAsSet;

    public Column(Attribute attribute) {
        this.attribute = attribute;
        this.attributeAsSet = Collections.singleton(this.attribute);
    }

    @Override
    public String makeValue(ResultRow row) {
        return row.get(this.attribute);
    }

    @Override
    public void describeSelf(NodeSetFilter c) {
        c.limitValuesToAttribute(this.attribute);
    }

    @Override
    public Expression valueExpression(String value) {
        if (value == null) {
            return Expression.FALSE;
        }
        return Equality.createAttributeValue(attribute, value);
    }

    @Override
    public Set<ProjectionSpec> projectionSpecs() {
        return this.attributeAsSet;
    }

    @Override
    public ValueMaker renameAttributes(ColumnRenamer renamer) {
        return new Column(renamer.applyTo(this.attribute));
    }

    @Override
    public List<OrderSpec> orderSpecs(boolean ascending) {
        return Collections.singletonList(
                new OrderSpec(new AttributeExpr(attribute), ascending));
    }

    @Override
    public String toString() {
        return "Column(" + this.attribute.qualifiedName() + ")";
    }
}
