package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RelationImpl extends Relation {
    private final ConnectedDB database;
    private final AliasMap aliases;
    private final Expression condition;
    private final Expression softCondition;
    private final Set<Join> joinConditions;
    private final Set<ProjectionSpec> projections;
    private final boolean isUnique;
    private final List<OrderSpec> orderSpecs;
    private int limit;
    private int limitInverse;

    public RelationImpl(ConnectedDB database, AliasMap aliases,
                        Expression condition, Expression softCondition,
                        Set<Join> joinConditions, Set<ProjectionSpec> projections,
                        boolean isUnique, List<OrderSpec> orderSpecs, int limit, int limitInverse) {
        this.database = database;
        this.aliases = aliases;
        this.condition = condition;
        this.softCondition = softCondition;
        this.joinConditions = joinConditions;
        this.projections = projections;
        this.isUnique = isUnique;
        this.orderSpecs = orderSpecs;
        this.limit = limit;
        this.limitInverse = limitInverse;
    }

    @Override
    public ConnectedDB database() {
        return this.database;
    }

    @Override
    public AliasMap aliases() {
        return this.aliases;
    }

    @Override
    public Expression condition() {
        return this.condition;
    }

    @Override
    public Expression softCondition() {
        return softCondition;
    }

    @Override
    public Set<Join> joinConditions() {
        return this.joinConditions;
    }

    @Override
    public Set<ProjectionSpec> projections() {
        return projections;
    }

    @Override
    public boolean isUnique() {
        return isUnique;
    }

    @Override
    public int limit() {
        return limit;
    }

    @Override
    public int limitInverse() {
        return limitInverse;
    }

    @Override
    public List<OrderSpec> orderSpecs() {
        return orderSpecs;
    }

    @Override
    public Relation select(Expression selectCondition) {
        if (selectCondition.isTrue()) {
            return this;
        }
        if (selectCondition.isFalse()) {
            return Relation.EMPTY;
        }
        return new RelationImpl(database, aliases,
                condition.and(selectCondition), softCondition, joinConditions,
                projections, isUnique, orderSpecs, limit, limitInverse);
    }

    @Override
    public Relation renameColumns(ColumnRenamer renames) {
        return new RelationImpl(database, renames.applyTo(aliases),
                renames.applyTo(condition), renames.applyTo(softCondition),
                renames.applyToJoinSet(joinConditions),
                renames.applyToProjectionSet(projections), isUnique, renames.applyTo(orderSpecs), limit, limitInverse);
    }

    @Override
    public Relation project(Set<? extends ProjectionSpec> projectionSpecs) {
        Set<ProjectionSpec> newProjections = new HashSet<>(projectionSpecs);
        newProjections.retainAll(projections);
        return new RelationImpl(database, aliases, condition, softCondition, joinConditions,
                newProjections, isUnique, orderSpecs, limit, limitInverse);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("RelationImpl(");
        if (isUnique) {
            result.append("[unique]");
        }
        result.append("\n");
        result.append("    project: ");
        result.append(projections);
        result.append("\n");
        if (!joinConditions.isEmpty()) {
            result.append("    joins: ");
            result.append(joinConditions);
            result.append("\n");
        }
        if (!condition.isTrue()) {
            result.append("    condition: ");
            result.append(condition);
            result.append("\n");
        }
        if (!softCondition.isTrue()) {
            result.append("    softCondition: ");
            result.append(softCondition);
            result.append("\n");
        }
        if (!aliases.equals(AliasMap.NO_ALIASES)) {
            result.append("    aliases: ");
            result.append(aliases);
            result.append("\n");
        }
        if (!orderSpecs.isEmpty()) {
            result.append("    order: ");
            result.append(orderSpecs);
            result.append("\n");
        }
        if (limit != -1) {
            result.append("    limit: ");
            result.append(limit);
            result.append("\n");
        }
        if (limitInverse != -1) {
            result.append("    limitInverse: ");
            result.append(limitInverse);
            result.append("\n");
        }
        result.append(")");
        return result.toString();
    }
}
