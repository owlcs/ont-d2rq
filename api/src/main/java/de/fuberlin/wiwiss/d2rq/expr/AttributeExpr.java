package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Collections;
import java.util.Set;

public class AttributeExpr extends Expression {
    private final Attribute attribute;

    public AttributeExpr(Attribute attribute) {
        this.attribute = attribute;
    }

    @Override
    public Set<Attribute> attributes() {
        return Collections.singleton(attribute);
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
        return new AttributeExpr(columnRenamer.applyTo(attribute));
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return database.vendor().quoteAttribute(attribute);
    }

    @Override
    public String toString() {
        return "AttributeExpr(" + attribute + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AttributeExpr)) {
            return false;
        }
        return attribute.equals(((AttributeExpr) other).attribute);
    }

    @Override
    public int hashCode() {
        return this.attribute.hashCode();
    }
}
