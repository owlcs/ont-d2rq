package de.fuberlin.wiwiss.d2rq.nodes;

import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.RelationalOperators;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;
import org.apache.jena.graph.Node;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A specification for creating RDF nodes out of a database relation.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public interface NodeMaker {

    NodeMaker EMPTY = new NodeMaker() {
        @Override
        public boolean isUnique() {
            return true;
        }

        @Override
        public Node makeNode(ResultRow tuple) {
            return null;
        }

        @Override
        public void describeSelf(NodeSetFilter c) {
            c.limitToEmptySet();
        }

        @Override
        public Set<ProjectionSpec> projectionSpecs() {
            return Collections.emptySet();
        }

        @Override
        public NodeMaker selectNode(Node node, RelationalOperators sideEffects) {
            return this;
        }

        @Override
        public NodeMaker renameAttributes(ColumnRenamer renamer) {
            return this;
        }

        @Override
        public List<OrderSpec> orderSpecs(boolean ascending) {
            return Collections.emptyList();
        }
    };

    Set<ProjectionSpec> projectionSpecs();

    boolean isUnique();

    void describeSelf(NodeSetFilter c);

    Node makeNode(ResultRow tuple);

    NodeMaker selectNode(Node node, RelationalOperators sideEffects);

    NodeMaker renameAttributes(ColumnRenamer renamer);

    /**
     * Returns expressions (with possible ASC/DESC marker) that re necessary
     * for ordering a relation by the nodes in this NodeMaker. Uses SPARQL semantics for ordering.
     *
     * @param ascending boolean
     * @return List of {@link OrderSpec}s
     */
    List<OrderSpec> orderSpecs(boolean ascending);
}
