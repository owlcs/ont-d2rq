package de.fuberlin.wiwiss.d2rq.engine;

import de.fuberlin.wiwiss.d2rq.algebra.NodeRelation;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;
import de.fuberlin.wiwiss.d2rq.sql.SQLIterator;
import de.fuberlin.wiwiss.d2rq.sql.SelectStatementBuilder;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.iterator.QueryIter;
import org.apache.jena.sparql.engine.iterator.QueryIterNullIterator;
import org.apache.jena.sparql.engine.iterator.QueryIterPlainWrapper;
import org.apache.jena.sparql.engine.iterator.QueryIterSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * A {@link QueryIterator} over the bindings produced by a
 * {@link Relation}. Works by running the underlying SQL
 * query using a {@link SQLIterator}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class QueryIterTableSQL extends QueryIter {
    private final static Logger LOGGER = LoggerFactory.getLogger(QueryIterTableSQL.class);

    /**
     * Creates an instance, or a simpler QueryIterator
     * if optimization is possible (e.g., the relation is empty).
     *
     * @return A query iterator over the contents of the relation
     */
    public static QueryIterator create(Relation relation,
                                       Collection<BindingMaker> bindingMakers, ExecutionContext execCxt) {
        if (relation.equals(Relation.EMPTY) || relation.condition().isFalse() || bindingMakers.isEmpty()) {
            return new QueryIterNullIterator(execCxt);
        }
        if (relation.isTrivial()) {
            ArrayList<Binding> bindingList = new ArrayList<Binding>();
            for (BindingMaker bindingMaker : bindingMakers) {
                Binding t = bindingMaker.makeBinding(ResultRow.NO_ATTRIBUTES);
                if (t == null) continue;
                bindingList.add(t);
            }
            return new QueryIterPlainWrapper(bindingList.iterator(), execCxt);
        }
        return new QueryIterTableSQL(relation, bindingMakers, execCxt);
    }

    /**
     * Creates an instance, or a simpler QueryIterator
     * if optimization is possible (e.g., the relation is empty).
     *
     * @return A query iterator over the contents of the node relation
     */
    public static QueryIterator create(NodeRelation table, ExecutionContext execCxt) {
        if (table.baseRelation().condition().isFalse()) {
            return new QueryIterNullIterator(execCxt);
        }
        if (table.baseRelation().isTrivial()) {
            return QueryIterSingleton.create(BindingMaker.createFor(table).makeBinding(ResultRow.NO_ATTRIBUTES), execCxt);
        }
        return new QueryIterTableSQL(table.baseRelation(),
                Collections.singleton(BindingMaker.createFor(table)), execCxt);
    }

    private final SQLIterator wrapped;
    private final Collection<BindingMaker> bindingMakers;
    private final LinkedList<Binding> queue = new LinkedList<Binding>();

    private QueryIterTableSQL(Relation relation,
                              Collection<BindingMaker> bindingMakers, ExecutionContext execCxt) {
        super(execCxt);
        this.bindingMakers = bindingMakers;
        SelectStatementBuilder builder = new SelectStatementBuilder(relation);
        wrapped = new SQLIterator(
                builder.getSQLStatement(), builder.getColumnSpecs(), relation.database());
    }

    @Override
    protected boolean hasNextBinding() {
        while (queue.isEmpty() && wrapped.hasNext()) {
            enqueueBindings(wrapped.next());
        }
        return !queue.isEmpty();
    }

    @Override
    protected Binding moveToNextBinding() {
        return queue.removeFirst();
    }

    @Override
    protected void closeIterator() {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("closeIterator() called ...");
        wrapped.close();
    }

    @Override
    protected void requestCancel() {
        LOGGER.info("requestCancel() called ...");
        wrapped.cancel();
    }

    /**
     * Create bindings from one database result row and put
     * them onto the queue
     */
    private void enqueueBindings(ResultRow row) {
        for (BindingMaker bindingMaker : bindingMakers) {
            Binding binding = bindingMaker.makeBinding(row);
            if (binding == null) continue;
            queue.add(binding);
        }
    }
}