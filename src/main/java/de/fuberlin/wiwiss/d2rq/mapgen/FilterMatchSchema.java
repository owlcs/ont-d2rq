package de.fuberlin.wiwiss.d2rq.mapgen;


public class FilterMatchSchema extends Filter {
    private final IdentifierMatcher schema;

    public FilterMatchSchema(IdentifierMatcher schema) {
        this.schema = schema;
    }

    @Override
    public boolean matchesSchema(String schema) {
        return this.schema.matches(schema);
    }

    @Override
    public boolean matchesTable(String schema, String table) {
        return matchesSchema(schema);
    }

    @Override
    public boolean matchesColumn(String schema, String table, String column) {
        return matchesSchema(schema);
    }

    @Override
    public String getSingleSchema() {
        return schema.getSingleString();
    }

    @Override
    public String toString() {
        return String.format("schema(%s)", schema);
    }
}
