package de.fuberlin.wiwiss.d2rq.mapgen;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

@SuppressWarnings("WeakerAccess")
public abstract class Filter {

    public static final Filter ALL = new Filter() {
        @Override
        public boolean matchesSchema(String schema) {
            return true;
        }

        @Override
        public boolean matchesTable(String schema, String table) {
            return true;
        }

        @Override
        public boolean matchesColumn(String schema, String table, String column) {
            return true;
        }

        @Override
        public String getSingleSchema() {
            return null;
        }

        @Override
        public String toString() {
            return "all";
        }
    };

    public static final Filter NOTHING = new Filter() {
        @Override
        public boolean matchesSchema(String schema) {
            return false;
        }

        @Override
        public boolean matchesTable(String schema, String table) {
            return false;
        }

        @Override
        public boolean matchesColumn(String schema, String table, String column) {
            return false;
        }

        @Override
        public String getSingleSchema() {
            return null;
        }

        @Override
        public String toString() {
            return "none";
        }
    };

    public abstract boolean matchesSchema(String schema);

    public abstract boolean matchesTable(String schema, String table);

    public abstract boolean matchesColumn(String schema, String table, String column);

    /**
     * @return If the filter matches only a single schema, then its name; otherwise, <code>null</code>
     */
    public abstract String getSingleSchema();

    public boolean matches(RelationName table) {
        return matchesTable(table.schemaName(), table.tableName());
    }

    public boolean matches(Attribute column) {
        return matchesColumn(column.schemaName(), column.tableName(), column.attributeName());
    }

    public boolean matchesAll(Collection<Attribute> columns) {
        for (Attribute column : columns) {
            if (!matches(column)) return false;
        }
        return true;
    }

    protected boolean sameSchema(String schema1, String schema2) {
        return Objects.equals(schema1, schema2);
    }

    public interface IdentifierMatcher {
        boolean matches(String identifier);

        String getSingleString();
    }

    public static final IdentifierMatcher NULL_MATCHER = new IdentifierMatcher() {
        @Override
        public boolean matches(String identifier) {
            return identifier == null;
        }

        @Override
        public String getSingleString() {
            return null;
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    public static IdentifierMatcher createStringMatcher(final String s) {
        return new IdentifierMatcher() {
            @Override
            public boolean matches(String identifier) {
                return s.equals(identifier);
            }

            @Override
            public String getSingleString() {
                return s;
            }

            @Override
            public String toString() {
                return "'" + s + "'";
            }
        };
    }

    public static IdentifierMatcher createPatternMatcher(final Pattern pattern) {
        return new IdentifierMatcher() {
            @Override
            public boolean matches(String identifier) {
                return identifier != null && pattern.matcher(identifier).matches();
            }

            @Override
            public String toString() {
                return "/" + pattern.pattern() + "/" + pattern.flags();
            }

            @Override
            public String getSingleString() {
                return null;
            }
        };
    }
}