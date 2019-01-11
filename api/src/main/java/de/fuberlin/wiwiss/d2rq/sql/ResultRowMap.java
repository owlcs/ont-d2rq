package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * A result row returned by a database query, presented as a
 * map from SELECT clause entries to string values.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ResultRowMap implements ResultRow {

    public static ResultRowMap fromResultSet(ResultSet resultSet,
                                             List<ProjectionSpec> projectionSpecs,
                                             ConnectedDB database) throws SQLException {
        Map<ProjectionSpec, String> result = new HashMap<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int i = 0; i < projectionSpecs.size(); i++) {
            ProjectionSpec key = projectionSpecs.get(i);
            int jdbcType = metaData == null ? Integer.MIN_VALUE : metaData.getColumnType(i + 1);
            String name = metaData == null ? "UNKNOWN" : metaData.getColumnTypeName(i + 1);
            result.put(key, database.vendor().getDataType(jdbcType, name.toUpperCase(), -1).value(resultSet, i + 1));
        }
        return new ResultRowMap(result);
    }

    private final Map<ProjectionSpec, String> projectionsToValues;

    public ResultRowMap(Map<ProjectionSpec, String> projectionsToValues) {
        this.projectionsToValues = projectionsToValues;
    }

    @Override
    public String get(ProjectionSpec projection) {
        return this.projectionsToValues.get(projection);
    }

    @Override
    public String toString() {
        List<ProjectionSpec> columns = new ArrayList<>(this.projectionsToValues.keySet());
        Collections.sort(columns);
        StringBuilder result = new StringBuilder("{");
        Iterator<ProjectionSpec> it = columns.iterator();
        while (it.hasNext()) {
            ProjectionSpec projection = it.next();
            result.append(projection.toString());
            result.append(" => '");
            result.append(this.projectionsToValues.get(projection));
            result.append("'");
            if (it.hasNext()) {
                result.append(", ");
            }
        }
        result.append("}");
        return result.toString();
    }
}
