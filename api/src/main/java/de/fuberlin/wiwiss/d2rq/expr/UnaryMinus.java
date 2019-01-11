package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Set;

public class UnaryMinus extends Expression {

    private Expression base;

    public UnaryMinus(Expression base) {
        this.base = base;
    }

    public Expression getBase() {
        return base;
    }

    @Override
    public Set<Attribute> attributes() {
        return base.attributes();
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
        return new UnaryMinus(base.renameAttributes(columnRenamer));
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return "- (" + base.toSQL(database, aliases) + ")";
    }

    @Override
    public String toString() {
        return "- (" + base + ")";
    }

}
