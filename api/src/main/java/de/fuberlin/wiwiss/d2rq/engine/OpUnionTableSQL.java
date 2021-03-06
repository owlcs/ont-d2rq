package de.fuberlin.wiwiss.d2rq.engine;

import de.fuberlin.wiwiss.d2rq.algebra.CompatibleRelationGroup;
import de.fuberlin.wiwiss.d2rq.algebra.NodeRelation;
import org.apache.jena.atlas.io.IndentedWriter;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.OpExt;
import org.apache.jena.sparql.algebra.op.OpNull;
import org.apache.jena.sparql.algebra.op.OpTable;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.apache.jena.sparql.engine.QueryIterator;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.iterator.QueryIterConcat;
import org.apache.jena.sparql.engine.iterator.QueryIterRepeatApply;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.sse.writers.WriterOp;
import org.apache.jena.sparql.util.NodeIsomorphismMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An {@link Op} that wraps a union of multiple {@link NodeRelation}s.
 * <p>
 * This is typically, but not necessarily, the result of matching a BGP against a D2RQ-mapped database.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class OpUnionTableSQL extends OpExt {

    /**
     * Creates a new instance from a collection of
     * {@link NodeRelation}s, or a simpler equivalent Op
     * if optimizations are possible.
     * @param tables Collection of {@link NodeRelation}
     * @return {@link Op}
     */
    public static Op create(Collection<NodeRelation> tables) {
        Collection<OpTableSQL> nonEmpty = new ArrayList<>();
        for (NodeRelation table : tables) {
            if (table.baseRelation().condition().isFalse()) continue;
            nonEmpty.add(new OpTableSQL(table));
        }
        if (nonEmpty.isEmpty()) {
            return OpNull.create();
        }
        return new OpUnionTableSQL(nonEmpty);
    }

    private final List<OpTableSQL> tableOps;
    private final Op effectiveOp;

    public OpUnionTableSQL(Collection<OpTableSQL> tableOps) {
        this(tableOps, OpTable.unit());
    }

    public OpUnionTableSQL(Collection<OpTableSQL> tableOps, Op effectiveOp) {
        super("sqlunion");
        this.tableOps = new ArrayList<>(tableOps);
        this.effectiveOp = effectiveOp;
    }

    @Override
    public QueryIterator eval(QueryIterator input, final ExecutionContext execCxt) {
        return new QueryIterRepeatApply(input, execCxt) {
            @Override
            protected QueryIterator nextStage(Binding binding) {
                QueryIterConcat resultIt = new QueryIterConcat(execCxt);
                Collection<NodeRelation> tables = new ArrayList<>();
                for (OpTableSQL tableOp : tableOps) {
                    tables.add(tableOp.table().extendWith(binding));
                }
                for (CompatibleRelationGroup group : CompatibleRelationGroup.groupNodeRelations(tables)) {
                    resultIt.add(QueryIterTableSQL.create(group.baseRelation(), group.bindingMakers(), execCxt));
                }
                return resultIt;
            }
        };
    }

    @Override
    public Op effectiveOp() {
        return effectiveOp;
    }

    @Override
    public void outputArgs(IndentedWriter out, SerializationContext sCxt) {
        out.println();
        for (OpTableSQL table : tableOps) {
            WriterOp.output(out, table, sCxt);
        }
    }

    @Override
    public int hashCode() {
        return 72345644 ^ tableOps.hashCode();
    }

    @Override
    public boolean equalTo(Op other, NodeIsomorphismMap labelMap) {
        return other instanceof OpUnionTableSQL && ((OpUnionTableSQL) other).tableOps.equals(tableOps);
    }
}
