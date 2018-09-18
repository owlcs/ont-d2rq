package de.fuberlin.wiwiss.d2rq.mapgen;


public class FilterMatchColumn extends Filter {
    private final IdentifierMatcher schema;
    private final IdentifierMatcher table;
    private final IdentifierMatcher column;
    private boolean matchParents;

    public FilterMatchColumn(IdentifierMatcher schema, IdentifierMatcher table, IdentifierMatcher column, boolean matchParents) {
        this.schema = schema;
        this.table = table;
        this.column = column;
        this.matchParents = matchParents;
    }

    @Override
    public boolean matchesSchema(String schema) {
        if (!matchParents) return false;
        return this.schema.matches(schema);
    }

    @Override
    public boolean matchesTable(String schema, String table) {
        if (!matchParents) return false;
        return this.schema.matches(schema) && this.table.matches(table);
    }

    @Override
    public boolean matchesColumn(String schema, String table, String column) {
        return this.schema.matches(schema) && this.table.matches(table)
                && this.column.matches(column);
    }

    @Override
    public String getSingleSchema() {
        return schema.getSingleString();
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("column(");
        if (schema != Filter.NULL_MATCHER) {
            result.append(schema).append(".");
        }
        if (table != Filter.NULL_MATCHER) {
            result.append(table).append(".");
        }
        result.append(column);
        result.append(")");
        return result.toString();
    }
}