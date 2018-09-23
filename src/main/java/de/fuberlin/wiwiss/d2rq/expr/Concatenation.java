package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.*;

public class Concatenation extends Expression {

    public static Expression create(List<Expression> expressions) {
        List<Expression> nonEmpty = new ArrayList<>(expressions.size());
        for (Expression expression : expressions) {
            if (expression instanceof Constant
                    && "".equals(((Constant) expression).value())) {
                continue;
            }
            nonEmpty.add(expression);
        }
        if (nonEmpty.isEmpty()) {
            return new Constant("");
        }
        if (nonEmpty.size() == 1) {
            return nonEmpty.get(0);
        }
        return new Concatenation(nonEmpty);
    }

    private final List<Expression> parts;
    private final Set<Attribute> attributes = new HashSet<>();

    private Concatenation(List<Expression> parts) {
        this.parts = parts;
        for (Expression expression : parts) {
            attributes.addAll(expression.attributes());
        }
    }

    @Override
    public Set<Attribute> attributes() {
        return attributes;
    }

    @Override
    public boolean isFalse() {
        return false;
    }

    @Override
    public boolean isTrue() {
        return false;
    }

    @Override
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        List<Expression> renamedExpressions = new ArrayList<>(parts.size());
        for (Expression expression : parts) {
            renamedExpressions.add(columnRenamer.applyTo(expression));
        }
        return new Concatenation(renamedExpressions);
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        String[] fragments = new String[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            Expression part = parts.get(i);
            fragments[i] = part.toSQL(database, aliases);
        }
        return database.vendor().getConcatenationExpression(fragments);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Concatenation)) return false;
        return parts.equals(((Concatenation) other).parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode() ^ 234645;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Concatenation(");
        Iterator<Expression> it = parts.iterator();
        while (it.hasNext()) {
            result.append(it.next());
            if (it.hasNext()) {
                result.append(", ");
            }
        }
        result.append(")");
        return result.toString();
    }
}
