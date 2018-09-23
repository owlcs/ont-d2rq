package de.fuberlin.wiwiss.d2rq.nodes;

import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.RelationalOperators;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;
import org.apache.jena.graph.Node;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FixedNodeMaker implements NodeMaker {
    private Node node;
    private boolean isUnique;

    public FixedNodeMaker(Node node, boolean isUnique) {
        this.node = node;
        this.isUnique = isUnique;
    }

    @Override
    public boolean isUnique() {
        return this.isUnique;
    }

    @Override
    public Node makeNode(ResultRow tuple) {
        return this.node;
    }

    @Override
    public void describeSelf(NodeSetFilter c) {
        c.limitTo(this.node);
    }

    @Override
    public Set<ProjectionSpec> projectionSpecs() {
        return Collections.emptySet();
    }

    @Override
    public NodeMaker selectNode(Node n, RelationalOperators sideEffects) {
        if (n.equals(this.node) || n.equals(Node.ANY) || n.isVariable()) {
            return this;
        }
        sideEffects.select(Expression.FALSE);
        return NodeMaker.EMPTY;
    }

    @Override
    public NodeMaker renameAttributes(ColumnRenamer renamer) {
        return new FixedNodeMaker(node, this.isUnique);
    }

    @Override
    public String toString() {
        return "Fixed(" + PrettyPrinter.toString(this.node) + ")";
    }

    @Override
    public List<OrderSpec> orderSpecs(boolean ascending) {
        return Collections.emptyList();
    }
}
