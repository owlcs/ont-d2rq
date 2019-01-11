package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.expr.Expression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Something that can rename columns in various objects.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public abstract class ColumnRenamer {

    /**
     * An optimized ColumnRenamer that leaves every column unchanged
     */
    public final static ColumnRenamer NULL = new ColumnRenamer() {
        @Override
        public AliasMap applyTo(AliasMap aliases) {
            return aliases;
        }

        @Override
        public Attribute applyTo(Attribute original) {
            return original;
        }

        @Override
        public Expression applyTo(Expression original) {
            return original;
        }

        @Override
        public Join applyTo(Join original) {
            return original;
        }

        @Override
        public Set<Join> applyToJoinSet(Set<Join> joins) {
            return joins;
        }

        @Override
        public String toString() {
            return "ColumnRenamer.NULL";
        }
    };


    /**
     * @param original A column
     * @return The renamed version of that column, or the same column if the renamer
     * does not apply to this argument
     */
    public abstract Attribute applyTo(Attribute original);

    /**
     * @param original A join
     * @return A join with all columns renamed according to this Renamer
     */
    public Join applyTo(Join original) {
        return original.renameColumns(this);
    }

    /**
     * @param original An expression
     * @return An expression with all columns renamed according to this Renamer
     */
    public Expression applyTo(Expression original) {
        return original.renameAttributes(this);
    }

    public Set<Join> applyToJoinSet(Set<Join> joins) {
        Set<Join> result = new HashSet<>();
        for (Join join : joins) {
            result.add(applyTo(join));
        }
        return result;
    }

    public ProjectionSpec applyTo(ProjectionSpec original) {
        return original.renameAttributes(this);
    }

    public Set<ProjectionSpec> applyToProjectionSet(Set<ProjectionSpec> projections) {
        Set<ProjectionSpec> result = new HashSet<>();
        for (ProjectionSpec projection : projections) {
            result.add(applyTo(projection));
        }
        return result;
    }

    public List<OrderSpec> applyTo(List<OrderSpec> orderSpecs) {
        List<OrderSpec> result = new ArrayList<>(orderSpecs.size());
        for (OrderSpec spec : orderSpecs) {
            result.add(new OrderSpec(applyTo(spec.expression()), spec.isAscending()));
        }
        return result;
    }

    public abstract AliasMap applyTo(AliasMap aliases);
}
