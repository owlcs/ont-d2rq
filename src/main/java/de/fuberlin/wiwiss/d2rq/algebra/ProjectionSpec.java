package de.fuberlin.wiwiss.d2rq.algebra;

import java.util.Set;

import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

/**
 * Something to be used in the SELECT clause of a SQL query, e.g.
 * a column name or an expression.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public interface ProjectionSpec extends Comparable<ProjectionSpec> {

    Set<Attribute> requiredAttributes();

    ProjectionSpec renameAttributes(ColumnRenamer renamer);

    Expression toExpression();

    String toSQL(ConnectedDB database, AliasMap aliases);

    Expression notNullExpression(ConnectedDB database, AliasMap aliases);
}
