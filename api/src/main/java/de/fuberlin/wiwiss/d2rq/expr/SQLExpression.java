package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;

import java.util.Set;


/**
 * An SQL expression.
 * <p>
 * TODO: Shouldn't call to SQL so much
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class SQLExpression extends Expression {

    public static Expression create(String sql) {
        sql = sql.trim();
        if ("1".equals(sql)) {
            return Expression.TRUE;
        }
        if ("0".equals(sql)) {
            return Expression.FALSE;
        }
        return new SQLExpression(sql);
    }

    private String expression;
    private Set<Attribute> columns;

    private SQLExpression(String expression) {
        this.expression = expression;
        this.columns = SQL.findColumnsInExpression(this.expression);
    }

    @Override
    public boolean isTrue() {
        return false;
    }

    @Override
    public boolean isFalse() {
        return false;
    }

    @Override
    public Set<Attribute> attributes() {
        return this.columns;
    }

    @Override
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        return new SQLExpression(SQL.replaceColumnsInExpression(this.expression, columnRenamer));
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        return "(" + SQL.quoteColumnsInExpression(this.expression, database) + ")";
    }

    @Override
    public String toString() {
        return "SQL(" + this.expression + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SQLExpression)) {
            return false;
        }
        SQLExpression otherExpression = (SQLExpression) other;
        return this.expression.equals(otherExpression.expression);
    }

    @Override
    public int hashCode() {
        return this.expression.hashCode();
    }

    public String getExpression() {
        return expression;
    }


}
