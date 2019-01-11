package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;

/**
 * A result row returned by a database query, presented as a
 * map from columns to string values.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public interface ResultRow {
    ResultRow NO_ATTRIBUTES = attribute -> null;

    String get(ProjectionSpec column);
}
