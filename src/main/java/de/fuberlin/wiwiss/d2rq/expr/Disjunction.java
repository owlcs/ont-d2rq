package de.fuberlin.wiwiss.d2rq.expr;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.*;

public class Disjunction extends Expression {

    public static Expression create(Collection<Expression> expressions) {
        Set<Expression> elements = new HashSet<>(expressions.size());
        for (Expression expression : expressions) {
            if (expression.isTrue()) {
                return Expression.TRUE;
            }
            if (expression.isFalse()) {
                continue;
            }
            if (expression instanceof Disjunction) {
                elements.addAll(((Disjunction) expression).expressions);
            } else {
                elements.add(expression);
            }
        }
        if (elements.isEmpty()) {
            return Expression.FALSE;
        }
        if (elements.size() == 1) {
            return elements.iterator().next();
        }
        return new Disjunction(elements);
    }

    private Set<Expression> expressions;
    private Set<Attribute> attributes = new HashSet<>();

    private Disjunction(Set<Expression> expressions) {
        this.expressions = expressions;
        for (Expression expression : expressions) {
            this.attributes.addAll(expression.attributes());
        }
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
        return this.attributes;
    }

    @Override
    public Expression renameAttributes(ColumnRenamer columnRenamer) {
        Set<Expression> renamedExpressions = new HashSet<>();
        for (Expression expression : expressions) {
            renamedExpressions.add(expression.renameAttributes(columnRenamer));
        }
        return Disjunction.create(renamedExpressions);
    }

    @Override
    public String toSQL(ConnectedDB database, AliasMap aliases) {
        List<String> fragments = new ArrayList<>(expressions.size());
        for (Expression expression : expressions) {
            fragments.add(expression.toSQL(database, aliases));
        }
        Collections.sort(fragments);
        StringBuilder result = new StringBuilder("(");
        Iterator<String> it = fragments.iterator();
        while (it.hasNext()) {
            String fragment = it.next();
            result.append(fragment);
            if (it.hasNext()) {
                result.append(" OR ");
            }
        }
        result.append(")");
        return result.toString();
    }

    @Override
    public String toString() {
        List<String> fragments = new ArrayList<>(expressions.size());
        for (Expression expression : expressions) {
            fragments.add(expression.toString());
        }
        Collections.sort(fragments);
        StringBuilder result = new StringBuilder("Disjunction(");
        Iterator<String> it = fragments.iterator();
        while (it.hasNext()) {
            String fragment = it.next();
            result.append(fragment);
            if (it.hasNext()) {
                result.append(", ");
            }
        }
        result.append(")");
        return result.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Disjunction)) {
            return false;
        }
        Disjunction otherConjunction = (Disjunction) other;
        return this.expressions.equals(otherConjunction.expressions);
    }

    @Override
    public int hashCode() {
        return this.expressions.hashCode();
    }
}