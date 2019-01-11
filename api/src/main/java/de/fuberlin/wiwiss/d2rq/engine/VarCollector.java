package de.fuberlin.wiwiss.d2rq.engine;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVisitorBase;
import org.apache.jena.sparql.algebra.OpWalker;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.core.Var;

import java.util.HashSet;
import java.util.Set;

/**
 * Collects the variables mentioned in an {@link Op} and its children.
 *
 * @author Herwig Leimer
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class VarCollector extends OpVisitorBase {

    /**
     * @param op {@link Op}
     * @return All variables mentioned in the op and its children
     */
    public static Set<Var> mentionedVars(Op op) {
        VarCollector collector = new VarCollector();
        OpWalker.walk(op, collector);
        return collector.mentionedVariables();
    }

    private Set<Var> variables = new HashSet<>();

    public Set<Var> mentionedVariables() {
        return variables;
    }

    @Override
    public void visit(OpBGP opBGP) {
        for (Triple triple : opBGP.getPattern()) {
            visit(triple);
        }
    }

    @Override
    public void visit(OpTriple opTriple) {
        visit(opTriple.getTriple());
    }

    @Override
    public void visit(OpQuadPattern quadPattern) {
        for (Quad quad : quadPattern.getPattern()) {
            visit(quad);
        }
    }

    @Override
    public void visit(OpQuad opQuad) {
        visit(opQuad.getQuad());
    }

    @Override
    public void visit(OpGraph opGraph) {
        visit(opGraph.getNode());
    }

    @Override
    public void visit(OpDatasetNames dsNames) {
        visit(dsNames.getGraphNode());
    }

    @Override
    public void visit(OpAssign opAssign) {
        variables.addAll(opAssign.getVarExprList().getVars());
    }

    @Override
    public void visit(OpExtend opExtend) {
        variables.addAll(opExtend.getVarExprList().getVars());
    }

    @Override
    public void visit(OpTable opTable) {
        variables.addAll(opTable.getTable().getVars());
    }

    private void visit(Triple triple) {
        visit(triple.getSubject());
        visit(triple.getPredicate());
        visit(triple.getObject());
    }

    private void visit(Quad quad) {
        visit(quad.asTriple());
        visit(quad.getGraph());
    }

    private void visit(Node node) {
        if (node == null) return;
        if (node.isVariable()) {
            variables.add((Var) node);
        }
    }

    @Override
    public void visit(OpPath opPath) {
        visit(opPath.getTriplePath().asTriple());
    }
}
