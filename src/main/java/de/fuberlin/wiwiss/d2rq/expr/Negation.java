package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Set;

/**
 * An expression that negates an underlying expression
 *
 * @author Christian Becker &lt;http://beckr.org#chris&gt;
 */
public class Negation extends Expression {

    private Expression base;

    public Negation(Expression base) {
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
        return base.isTrue();
    }

    @Override
    public boolean isTrue() {
        return base.isFalse();
    }

    @Override
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        return new Negation(base.renameAttributes(columnRenamer));
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return "NOT (" + base.toSQL(database, aliases) + ")";
    }

    @Override
    public String toString() {
        return "Negation(" + base + ")";
    }

}