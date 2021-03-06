package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;

import java.util.*;

/**
 * A {@link Relation} associated with a number of named {@link NodeMaker}s.
 * <p>
 * TODO: This is really just a Relation and a BindingMaker wrapped into one. Refactor as such?
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class NodeRelation {

    public static final NodeRelation TRUE = new NodeRelation(Relation.TRUE, Collections.emptyMap());

    public static NodeRelation empty(Set<Var> variables) {
        Map<Var, NodeMaker> map = new HashMap<>();
        for (Var variable : variables) {
            map.put(variable, NodeMaker.EMPTY);
        }
        return new NodeRelation(Relation.EMPTY, map);
    }

    private final Relation base;
    private final Map<Var, NodeMaker> nodeMakers;

    public NodeRelation(Relation base, Map<Var, NodeMaker> nodeMakers) {
        this.base = base;
        this.nodeMakers = nodeMakers;
    }

    public Relation baseRelation() {
        return base;
    }

    public Set<Var> variables() {
        return nodeMakers.keySet();
    }

    public NodeMaker nodeMaker(Var variables) {
        return nodeMakers.get(variables);
    }

    public NodeRelation withPrefix(int index) {
        Collection<Alias> newAliases = new ArrayList<>();
        for (RelationName tableName : baseRelation().tables()) {
            newAliases.add(new Alias(tableName, tableName.withPrefix(index)));
        }
        AliasMap renamer = new AliasMap(newAliases);
        Map<Var, NodeMaker> renamedNodeMakers = new HashMap<>();
        for (Var variable : variables()) {
            renamedNodeMakers.put(variable, nodeMaker(variable).renameAttributes(renamer));
        }
        return new NodeRelation(baseRelation().renameColumns(renamer), renamedNodeMakers);
    }

    public NodeRelation renameSingleRelation(RelationName oldName, RelationName newName) {
        AliasMap renamer = AliasMap.create1(oldName, newName);
        Map<Var, NodeMaker> renamedNodeMakers = new HashMap<>();

        // This is only done for consistency as the NodeMakers won't be used
        for (Var variable : variables()) {
            renamedNodeMakers.put(variable, nodeMaker(variable).renameAttributes(renamer));
        }
        return new NodeRelation(baseRelation().renameColumns(renamer), renamedNodeMakers);
    }

    /**
     * Joins this NodeRelation with a Binding. Any row in this
     * NodeRelation that is incompatible with the binding will be
     * dropped, and any compatible row will be extended with
     * FixedNodeMakers whose node is taken from the binding.
     *
     * @param binding A binding to join with this NodeRelation
     * @return The joined NodeRelation
     */
    public NodeRelation extendWith(Binding binding) {
        if (binding.isEmpty()) return this;
        MutableRelation mutator = new MutableRelation(baseRelation());
        Map<Var, NodeMaker> columns = new HashMap<>();
        for (Var variable : variables()) {
            columns.put(variable, nodeMaker(variable));
        }
        for (Iterator<Var> it = binding.vars(); it.hasNext(); ) {
            Var var = it.next();
            Node value = binding.get(var);
            if (columns.containsKey(var)) {
                columns.put(var, columns.get(var).selectNode(value, mutator));
            } else {
                columns.put(var, new FixedNodeMaker(value, false));
            }
        }
        return new NodeRelation(mutator.immutableSnapshot(), columns);
    }

    // TODO: This should take an ARQ Expr as argument and transform it to an Expression
    public NodeRelation select(Expression expression) {
        MutableRelation mutator = new MutableRelation(baseRelation());
        mutator.select(expression);
        return new NodeRelation(mutator.immutableSnapshot(), nodeMakers);
    }

    public NodeRelation orderBy(Var variable, boolean ascending) {
        if (!variables().contains(variable)) return this;
        List<OrderSpec> orderSpecs = nodeMaker(variable).orderSpecs(ascending);
        if (orderSpecs.isEmpty()) return this;
        MutableRelation mutator = new MutableRelation(baseRelation());
        mutator.orderBy(orderSpecs);
        return new NodeRelation(mutator.immutableSnapshot(), nodeMakers);
    }

    public NodeRelation limit(int limit) {
        MutableRelation mutator = new MutableRelation(baseRelation());
        mutator.limit(limit);
        return new NodeRelation(mutator.immutableSnapshot(), nodeMakers);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("NodeRelation(");
        result.append(base.toString());
        result.append("\n");
        for (Var variable : variables()) {
            result.append("    ");
            result.append(variable);
            result.append(" => ");
            result.append(nodeMaker(variable).toString());
            result.append("\n");
        }
        result.append(")");
        return result.toString();
    }
}
