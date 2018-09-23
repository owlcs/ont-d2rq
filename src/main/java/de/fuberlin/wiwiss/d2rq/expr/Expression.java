package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


/**
 * A SQL expression.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public abstract class Expression {
    public static final Expression TRUE = new Expression() {
        @Override
        public Set<Attribute> attributes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isFalse() {
            return false;
        }

        @Override
        public boolean isTrue() {
            return true;
        }

        @Override
        public Expression renameAttributes(ColumnRenamer columnRenamer) {
            return this;
        }

        @Override
        public String toSQL(ConnectedDB database, AliasMap aliases) {
            return "1";
        }

        @Override
        public String toString() {
            return "TRUE";
        }
    };

    public static final Expression FALSE = new Expression() {
        @Override
        public Set<Attribute> attributes() {
            return Collections.emptySet();
        }

        @Override
        public boolean isFalse() {
            return true;
        }

        @Override
        public boolean isTrue() {
            return false;
        }

        @Override
        public Expression renameAttributes(ColumnRenamer columnRenamer) {
            return this;
        }

        @Override
        public String toSQL(ConnectedDB database, AliasMap aliases) {
            return "0";
        }

        @Override
        public String toString() {
            return "FALSE";
        }
    };

    public abstract boolean isTrue();

    public abstract boolean isFalse();

    public abstract Set<Attribute> attributes();

    public abstract Expression renameAttributes(ColumnRenamer columnRenamer);

    public abstract String toSQL(ConnectedDB database, AliasMap aliases);

    public Expression and(Expression other) {
        List<Expression> list = new ArrayList<>(2);
        list.add(this);
        list.add(other);
        return Conjunction.create(list);
    }

    public Expression or(Expression other) {
        List<Expression> list = new ArrayList<>(2);
        list.add(this);
        list.add(other);
        return Disjunction.create(list);
    }
}
