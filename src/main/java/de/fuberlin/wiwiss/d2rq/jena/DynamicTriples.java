package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.util.iterator.NullIterator;

/**
 * Created by @ssz on 22.09.2018.
 */
public interface DynamicTriples {
    DynamicTriples EMPTY = (g, t) -> NullIterator.instance();

    default boolean test(Triple m) {
        return true;
    }

    ExtendedIterator<Triple> list(Graph g, Triple m);

    default ExtendedIterator<Triple> find(Graph g, Triple m) {
        return test(m) ? list(g, m) : NullIterator.instance();
    }

    default DynamicTriples andThen(DynamicTriples right) {
        return concat(this, right);
    }

    static DynamicTriples concat(DynamicTriples left, DynamicTriples right) {
        if (left == right) return left;
        if (left == EMPTY) {
            return right;
        }
        if (right == EMPTY) {
            return left;
        }
        return (g, t) -> left.find(g, t).andThen(right.find(g, t));
    }
}
