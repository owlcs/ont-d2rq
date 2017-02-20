package de.fuberlin.wiwiss.d2rq.engine;

import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpExt;
import org.apache.jena.sparql.algebra.op.OpNull;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.iterator.QueryIterRepeatApply;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.util.NodeIsomorphismMap;

import de.fuberlin.wiwiss.d2rq.algebra.NodeRelation;

/**
 * An {@link Op} that wraps a {@link NodeRelation}.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class OpTableSQL extends OpExt {

    /**
     * Creates a new OpTableSQL, or a simpler Op if optimizations
     * are possible.
     */
    public static Op create(NodeRelation table) {
        if (table.baseRelation().condition().isFalse()) {
            return OpNull.create();
        }
        return new OpTableSQL(table);
    }

    private final NodeRelation table;

    public OpTableSQL(NodeRelation table) {
        super("sql");
        this.table = table;
    }

    public NodeRelation table() {
        return table;
    }

    @Override
    public QueryIterator eval(QueryIterator input, final ExecutionContext execCxt) {
        return new QueryIterRepeatApply(input, execCxt) {
            @Override
            protected QueryIterator nextStage(Binding binding) {
                return QueryIterTableSQL.create(table.extendWith(binding), execCxt);
            }
        };
    }

    @Override
    public Op effectiveOp() {
        return OpTable.unit();
    }

    @Override
    public void outputArgs(IndentedWriter out, SerializationContext sCxt) {
        out.println(String.valueOf(table));
    }

    @Override
    public int hashCode() {
        return 72345643 ^ table.hashCode();
    }

    @Override
    public boolean equalTo(Op other, NodeIsomorphismMap labelMap) {
        return other instanceof OpTableSQL && ((OpTableSQL) other).table.equals(table);
    }
}
