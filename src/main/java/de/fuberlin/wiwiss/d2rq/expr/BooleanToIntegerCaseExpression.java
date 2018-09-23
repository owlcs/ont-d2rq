package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.Set;

/**
 * A CASE statement that turns a BOOLEAN (TRUE, FALSE) into an
 * INT (1, 0)
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class BooleanToIntegerCaseExpression extends Expression {
    private Expression base;

    public BooleanToIntegerCaseExpression(Expression base) {
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
        return base.isFalse();
    }

    @Override
    public boolean isTrue() {
        return base.isTrue();
    }

    @Override
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        return new BooleanToIntegerCaseExpression(base.renameAttributes(columnRenamer));
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return "(CASE WHEN (" + base.toSQL(database, aliases) + ") THEN 1 ELSE 0 END)";
    }

    @Override
    public String toString() {
        return "Boolean2Int(" + base + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BooleanToIntegerCaseExpression)) {
            return false;
        }
        BooleanToIntegerCaseExpression otherExpression = (BooleanToIntegerCaseExpression) other;
        return this.base.equals(otherExpression.base);
    }

    @Override
    public int hashCode() {
        return base.hashCode() ^ 2341234;
    }
}