package de.fuberlin.wiwiss.d2rq.engine;

import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpVisitor;
import org.apache.jena.sparql.algebra.TransformCopy;
import org.apache.jena.sparql.algebra.op.*;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;


/**
 * Visitor for traversing the operator-tree, moving down any
 * filter conditions as far as possible.
 *
 * @author Herwig Leimer
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class PushDownOpFilterVisitor implements OpVisitor {
//	private static final Log log = LogFactory.getLog(PushDownOpFilterVisitor.class);

    public static Op transform(Op op) {
        PushDownOpFilterVisitor visitor = new PushDownOpFilterVisitor();
        op.visit(visitor);
        return visitor.result();
    }

    private final TransformCopy copy = new TransformCopy(false);
    private final Stack<Op> stack = new Stack<>();
    private List<Expr> filterExpr = new ArrayList<>();

    /**
     * Returns the changed operator-tree
     *
     * @return Op - root-node of the operator-tree
     */
    public Op result() {
        if (stack.size() != 1) {
            throw new IllegalStateException("Stack is not aligned");
        }
        return stack.pop();
    }

    /**
     * When visiting an OpFilter, all its filterconditions are collected during
     * the top-down-stepping. During the bottom-up-stepping all filterconditions
     * which were moved down, are removed
     */
    @Override
    public void visit(final OpFilter opFilter) {
        List<Expr> exprs = new ArrayList<>(opFilter.getExprs().getList());
        filterExpr.addAll(exprs);
        Op subOp = null;
        if (opFilter.getSubOp() != null) {
            opFilter.getSubOp().visit(this);
            subOp = stack.pop();
        }
        exprs.removeAll(filterExpr);
        // remove the filter if it has no expressions
        if (exprs.isEmpty()) {
            stack.push(subOp);
        } else {
            stack.push(opFilter);
        }
    }

    /**
     * When visiting an OpUnion also 3 conditions for moving down the
     * filterconditions are checked. Only when a condition is satisfied, the
     * filtercondition can be moved down to the next operator in the tree.
     * Otherwise the condition will stay here and during the bottum-up-stepping
     * an OpFilter containing these remained filterconditions will be inserted
     * in the operator-tree. Conditions for moving down a filtercondition: (M1,
     * M2 are graphpatterns, F is a filtercondition) 1) Filter(Union(M1, M2),
     * F)) will become Union(Filter(M1, F), M2) when the filterexpression is
     * only referenced to M1 2) Filter(Union(M1, M2), F)) will become Union(M1,
     * Filter(M2, F)) when the filterexpression is only referenced to M2
     * <p>
     * TODO: Dubious! Shouldn't this be Union(Filter(M1,F), Filter(M2,F))? What
     * does the code actually do?
     * <p>
     * 3) Filter(Union(M1, M2), F)) will become Join(Union(M1, F), Union(M2, F))
     * when the filterexpression is referenced to M1 and M2
     */
    @Override
    public void visit(OpUnion opUnion) {
        checkMoveDownFilterExprAndVisitOpUnion(opUnion);
    }

    /**
     * When visiting an OpJoin 3 conditions for moving down the filterconditions
     * are checked. Only when a condition is satisfied, the filtercondition can
     * be moved down to the next operator in the tree. Otherwise the condition
     * will stay here and during the bottum-up-stepping an OpFilter containing
     * these remained filterconditions will be inserted in the operator-tree.
     * Conditions for moving down a filtercondition: (M1, M2 are graphpatterns,
     * F is a filtercondition) 1) Filter(Join(M1, M2), F)) will become
     * Join(Filter(M1, F), M2) when the filterexpression is only referenced to
     * M1 2) Filter(Join(M1, M2), F)) will become Join(M1, Filter(M2, F)) when
     * the filterexpression is only referenced to M2 3) Filter(Join(M1, M2), F))
     * will become Join(Filter(M1, F), Filter(M2, F)) when the filterexpression
     * is referenced to M1 and M2
     */
    @Override
    public void visit(OpJoin opJoin) {
        checkMoveDownFilterExprAndVisitOpJoin(opJoin);
    }

    /**
     * When there are some filterexpressions which belong to an OpBGP, the OpBGP
     * will be converted to an OpFilteredBGP. A OpFilteredBGP is nearly the same
     * like an OpBGP but it has a link to its parent, which is an OpFilter with
     * the coresponding filter-conditions, because in the transforming-process
     * of the OpBGPs to OpD2RQs a link to the above OpFilter is needed.
     */
    @Override
    public void visit(OpBGP op) {
        wrapInCurrentFilter(op);
    }

    /**
     * When visiting an OpDiff 3 conditions for moving down the filterconditions
     * are checked. Only when a condition is satisfied, the filtercondition can
     * be moved down to the next operator in the tree. Otherwise the condition
     * will stay here and during the bottom-up-stepping an OpFilter containing
     * these remained filterconditions will be inserted in the operator-tree.
     * Conditions for moving down a filtercondition: (M1, M2 are graphpatterns,
     * F is a filtercondition) 1) Filter(Diff(M1, M2), F)) will become
     * Diff(Filter(M1, F), M2) when the filterexpression is only referenced to
     * M1 2) Filter(Diff(M1, M2), F)) will become Diff(M1, Filter(M2, F)) when
     * the filterexpression is only referenced to M2 3) Filter(Diff(M1, M2), F))
     * will become Diff(Filter(M1, F), Filter(M2, F)) when the filterexpression
     * is referenced to M1 and M2
     */
    @Override
    public void visit(OpDiff opDiff) {
        // TODO: Regel nochmal ueberdenken !!!
        checkMoveDownFilterExprAndVisitOpDiff(opDiff);
    }

    @Override
    public void visit(OpConditional opCondition) {
        // TODO moving down / improvements possible!! Check this
        wrapInCurrentFilterAndRecurse(opCondition);
    }

    @Override
    public void visit(OpProcedure opProc) {
        // TODO What is this?
        wrapInCurrentFilterAndRecurse(opProc);
    }

    @Override
    public void visit(OpPropFunc opPropFunc) {
        // TODO What is this?
        wrapInCurrentFilterAndRecurse(opPropFunc);
    }

    @Override
    public void visit(OpTable opTable) {
        wrapInCurrentFilter(opTable);
    }

    @Override
    public void visit(OpQuadPattern quadPattern) {
        wrapInCurrentFilter(quadPattern);
    }

    @Override
    public void visit(OpQuadBlock quadBlock) {
        wrapInCurrentFilter(quadBlock);
    }

    @Override
    public void visit(OpPath opPath) {
        wrapInCurrentFilter(opPath);
    }

    @Override
    public void visit(OpTriple opTriple) {
        wrapInCurrentFilter(opTriple);
    }

    @Override
    public void visit(OpDatasetNames dsNames) {
        wrapInCurrentFilter(dsNames);
    }

    @Override
    public void visit(OpSequence opSequence) {
        // TODO What is this?
        wrapInCurrentFilterAndRecurse(opSequence);
    }

    /**
     * When visiting an OpJoin 2 conditions for moving down the filterconditions
     * are checked. Only when a condition is satisfied, the filtercondition can
     * be moved down to the next operator in the tree. Otherwise the condition
     * will stay here and during the bottum-up-stepping an OpFilter containing
     * these remained filterconditions will be inserted in the operator-tree.
     * Conditions for moving down a filtercondition: (M1, M2 are graphpatterns,
     * F is a filtercondition) 1) Filter(LeftJoin(M1, M2), F)) will become
     * LeftJoin(Filter(M1, F), M2) when the filterexpression is only referenced
     * to M1 2) Filter(LeftJoin(M1, M2), F)) will become LeftJoin(Filter(M1, F),
     * Filter(M2, F)) when the filterexpression is referenced to M1 and M2
     */
    @Override
    public void visit(OpLeftJoin opLeftJoin) {
        checkMoveDownFilterExprAndVisitOpLeftJoin(opLeftJoin);
    }

    @Override
    public void visit(OpGraph opGraph) {
        // TODO Pushing down might be possible
        wrapInCurrentFilterAndRecurse(opGraph);
    }

    @Override
    public void visit(OpService opService) {
        // Don't recurse into the OpService, just return it
        // (with any filters that were pushed down to us applied)
        wrapInCurrentFilter(opService);
    }

    @Override
    public void visit(OpExt opExt) {
        wrapInCurrentFilter(opExt);
    }

    @Override
    public void visit(OpNull opNull) {
        wrapInCurrentFilter(opNull);
    }

    @Override
    public void visit(OpLabel opLabel) {
        moveFilterPast(opLabel);
    }

    @Override
    public void visit(OpList opList) {
        // TODO Might be able to move down filter past the op
        wrapInCurrentFilterAndRecurse(opList);
    }

    @Override
    public void visit(OpOrder opOrder) {
        // TODO Might be able to move down filter past the op
        wrapInCurrentFilterAndRecurse(opOrder);
    }

    @Override
    public void visit(OpProject opProject) {
        // TODO Might be able to move down filter past the op
        wrapInCurrentFilterAndRecurse(opProject);
    }

    @Override
    public void visit(OpDistinct opDistinct) {
        wrapInCurrentFilterAndRecurse(opDistinct);
    }

    @Override
    public void visit(OpReduced opReduced) {
        wrapInCurrentFilterAndRecurse(opReduced);
    }

    @Override
    public void visit(OpAssign opAssign) {
        // TODO Might be able to move down filter past the op
        wrapInCurrentFilterAndRecurse(opAssign);
    }

    @Override
    public void visit(OpSlice opSlice) {
        wrapInCurrentFilterAndRecurse(opSlice);
    }

    @Override
    public void visit(OpGroup opGroup) {
        wrapInCurrentFilterAndRecurse(opGroup);
    }

    @Override
    public void visit(OpExtend opExtend) {
        // TODO Might be able to move down filter past the OpExtend
        wrapInCurrentFilterAndRecurse(opExtend);
    }

    /**
     * TODO I have no clue if this actually works
     * <p>
     * Filter(A-B,e) = Filter(A,e)-B
     */
    @Override
    public void visit(OpMinus opMinus) {
        // Walk into A subtree
        opMinus.getLeft().visit(this);
        Op leftWithFilter = stack.pop();

        // Walk into B subtree with empty expression list
        List<Expr> tmp = filterExpr;
        filterExpr = new ArrayList<>();
        opMinus.getRight().visit(this);
        Op right = stack.pop();

        // Remove the entire filter expression on the way up
        filterExpr = tmp;

        stack.push(OpMinus.create(leftWithFilter, right));
    }

    @Override
    public void visit(OpDisjunction opDisjunction) {
        // TODO What the heck is an OpDisjunction anyway?
        wrapInCurrentFilterAndRecurse(opDisjunction);
    }

    @Override
    public void visit(OpTopN opTop) {
        // TODO Might be able to move down filter past the OpTopN
        wrapInCurrentFilterAndRecurse(opTop);
    }

    @Override
    public void visit(OpQuad opQuad) {
        wrapInCurrentFilter(opQuad);
    }

    private void wrapInCurrentFilterAndRecurse(Op1 op1) {
        List<Expr> retainedFilterExpr = new ArrayList<>(filterExpr);
        filterExpr.clear();
        Op subOp = null;
        if (op1.getSubOp() != null) {
            op1.getSubOp().visit(this);
            subOp = stack.pop();
        }
        wrapInFilter(op1.apply(copy, subOp), retainedFilterExpr);
        filterExpr = retainedFilterExpr;
    }

    private void wrapInCurrentFilterAndRecurse(Op2 op2) {
        List<Expr> retainedFilterExpr = new ArrayList<>(filterExpr);
        filterExpr.clear();
        Op left = null;
        if (op2.getLeft() != null) {
            op2.getLeft().visit(this);
            left = stack.pop();
        }
        Op right = null;
        if (op2.getRight() != null) {
            op2.getRight().visit(this);
            right = stack.pop();
        }
        wrapInFilter(op2.apply(copy, left, right), retainedFilterExpr);
        filterExpr = retainedFilterExpr;
    }

    private void wrapInCurrentFilterAndRecurse(OpN opN) {
        List<Expr> retainedFilterExpr = new ArrayList<>(filterExpr);
        filterExpr.clear();
        List<Op> children = new ArrayList<>();
        for (Op child : opN.getElements()) {
            child.visit(this);
            children.add(stack.pop());
        }
        wrapInFilter(opN.apply(copy, children), retainedFilterExpr);
        filterExpr = retainedFilterExpr;
    }

    private void wrapInCurrentFilter(Op op) {
        wrapInFilter(op, filterExpr);
    }

    private void wrapInFilter(Op op, List<Expr> filters) {
        if (filterExpr.isEmpty()) {
            stack.push(op);
        } else {
            stack.push(OpFilter.filterBy(new ExprList(filters), op));
        }
    }

    private void moveFilterPast(Op1 op1) {
        op1.getSubOp().visit(this);
    }

    /**
     * Calculates the set of valid filterexpressions as a subset from the whole
     * set of possibilities.
     *
     * @param candidates - the whole set
     * @param op         {@link Op}
     * @return List - the subset from the set of possiblities
     */
    private List<Expr> calcValidFilterExpr(List<Expr> candidates, Op op) {
        Set<Var> mentionedVars = VarCollector.mentionedVars(op);
        List<Expr> result = new ArrayList<>();
        for (Expr expr : candidates) {
            if (mentionedVars.containsAll(expr.getVarsMentioned())) {
                result.add(expr);
            }
        }
        return result;
    }

    /**
     * Checks first if a filterexpression can be moved down. And after visits
     * the operator
     */
    private void checkMoveDownFilterExprAndVisitOpUnion(OpUnion opUnion) {
        Op left;
        Op right;
        Op newOp;
        List<Expr> filterExprBeforeOpUnion, filterExprAfterOpUnion, notMoveableFilterExpr;

        // contains the filterexpressions that are valid before this op2
        filterExprBeforeOpUnion = new ArrayList<>(this.filterExpr);
        // contains the filterexpressions that are valid after this op2
        // this is needed because during the bottom-up-stepping all
        // filterexpressions
        // which could not be transformed down, must be inserted by means of an
        // OpFilter
        // above this op2
        filterExprAfterOpUnion = new ArrayList<>();

        // check left subtree
        if ((left = opUnion.getLeft()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // left subtree
            this.filterExpr = calcValidFilterExpr(filterExprBeforeOpUnion, left);
            filterExprAfterOpUnion.addAll(this.filterExpr);
            // step down
            opUnion.getLeft().visit(this);
            left = stack.pop();
        }

        // check the right subtree
        if ((right = opUnion.getRight()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // right subtree
            this.filterExpr = calcValidFilterExpr(filterExprBeforeOpUnion, right);
            filterExprAfterOpUnion.addAll(this.filterExpr);
            // step down
            opUnion.getRight().visit(this);
            right = stack.pop();
        }

        // note: filterExprAfterOpUnion contains now all filterexpressions which
        // could
        // be moved down
        // now calculate all filterexpressions which were not moveable
        notMoveableFilterExpr = new ArrayList<>(filterExprBeforeOpUnion);
        notMoveableFilterExpr.removeAll(filterExprAfterOpUnion);

        // if there are some filterexpressions which could not be moved down,
        // an opFilter must be inserted that contains this filterexpressions
        if (!notMoveableFilterExpr.isEmpty()) {
            // create the filter
            newOp = OpFilter.ensureFilter(OpUnion.create(left, right));
            // add the conditions
            ((OpFilter) newOp).getExprs().getList().addAll(notMoveableFilterExpr);
        } else {
            newOp = opUnion;
        }

        // restore filterexpressions
        this.filterExpr = filterExprBeforeOpUnion;

        this.stack.push(newOp);
    }

    /**
     * Checks first if a filterexpression can be moved down. And after visits
     * the operator
     */
    private void checkMoveDownFilterExprAndVisitOpJoin(OpJoin opJoin) {
        Op left;
        Op right;
        Op newOp;
        List<Expr> filterExprBeforeOpJoin, filterExprAfterOpJoin, notMoveableFilterExpr;

        // contains the filterexpressions that are valid before this op2
        filterExprBeforeOpJoin = new ArrayList<>(this.filterExpr);
        // contains the filterexpressions that are valid after this op2
        // this is needed because during the bottom-up-stepping all
        // filterexpressions
        // which could not be transformed down, must be inserted by means of an
        // OpFilter
        // above this op2
        filterExprAfterOpJoin = new ArrayList<>();

        // check left subtree
        if ((left = opJoin.getLeft()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // left subtree
            this.filterExpr = calcValidFilterExpr(filterExprBeforeOpJoin, left);
            filterExprAfterOpJoin.addAll(this.filterExpr);
            // step down
            opJoin.getLeft().visit(this);
            left = stack.pop();
        }

        // check the right subtree
        if ((right = opJoin.getRight()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // right subtree
            this.filterExpr = calcValidFilterExpr(filterExprBeforeOpJoin, right);
            filterExprAfterOpJoin.addAll(this.filterExpr);
            // step down
            opJoin.getRight().visit(this);
            right = stack.pop();
        }

        // note: filterExprAfterOpUnion contains now all filterexpressions which
        // could
        // be moved down
        // now calculate all filterexpressions which were not moveable
        notMoveableFilterExpr = new ArrayList<>(filterExprBeforeOpJoin);
        notMoveableFilterExpr.removeAll(filterExprAfterOpJoin);

        // if there are some filterexpressions which could not be moved down,
        // an opFilter must be inserted that contains this filterexpressions
        if (!notMoveableFilterExpr.isEmpty()) {
            // create the filter
            newOp = OpFilter.ensureFilter(OpJoin.create(left, right));
            // add the conditions
            ((OpFilter) newOp).getExprs().getList().addAll(notMoveableFilterExpr);
        } else {
            // nothing must be done
            newOp = opJoin;
        }

        // restore filterexpressions
        this.filterExpr = filterExprBeforeOpJoin;

        this.stack.push(newOp);
    }

    /**
     * Checks first if a filterexpression can be moved down. And after visits
     * the operator
     */
    private void checkMoveDownFilterExprAndVisitOpLeftJoin(OpLeftJoin opLeftJoin) {
        Op left;
        Op right;
        Op newOp;
        List<Expr> filterExprBeforeOpLeftJoin, filterExprAfterOpLeftJoin,
                notMoveableFilterExpr, filterExprRightSide, validFilterExprRightSide;

        // contains the filterexpressions that are valid before this op2
        filterExprBeforeOpLeftJoin = new ArrayList<>(this.filterExpr);
        // contains the filterexpressions that are valid after this op2
        // this is needed because during the bottom-up-stepping all
        // filterexpressions
        // which could not be transformed down, must be inserted by means of an
        // OpFilter
        // above this op2
        filterExprAfterOpLeftJoin = new ArrayList<>();

        // check left subtree
        if ((left = opLeftJoin.getLeft()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // left subtree
            this.filterExpr = calcValidFilterExpr(filterExprBeforeOpLeftJoin, left);
            filterExprAfterOpLeftJoin.addAll(this.filterExpr);
            // step down
            opLeftJoin.getLeft().visit(this);
            left = stack.pop();
        }

        // check the right subtree
        if ((right = opLeftJoin.getRight()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // right subtree

            filterExprRightSide = calcValidFilterExpr(filterExprBeforeOpLeftJoin, right);
            validFilterExprRightSide = new ArrayList<>();

            // now check for expr that are vaild for left-side and right-side
            // only when an expr is vaid for left-side, it can be vaild for
            // right-side
            for (Expr expr : filterExprRightSide) {
                if (this.filterExpr.contains(expr)) {
                    // valid
                    validFilterExprRightSide.add(expr);
                }
            }

            this.filterExpr = validFilterExprRightSide;
            filterExprAfterOpLeftJoin.addAll(this.filterExpr);

            // step down
            opLeftJoin.getRight().visit(this);
            right = stack.pop();
        }

        // note: filterExprAfterOpLeftJoin contains now all filterexpressions
        // which could
        // be moved down
        // now calculate all filterexpressions which were not moveable
        notMoveableFilterExpr = new ArrayList<>(filterExprBeforeOpLeftJoin);
        notMoveableFilterExpr.removeAll(filterExprAfterOpLeftJoin);

        // if there are some filterexpressions which could not be moved down,
        // an opFilter must be inserted that contains this filterexpressions
        if (!notMoveableFilterExpr.isEmpty()) {
            // create the filter for an opleftjoin
            newOp = OpFilter.ensureFilter(OpLeftJoin.create(left, right, opLeftJoin.getExprs()));
            // add the conditions
            ((OpFilter) newOp).getExprs().getList().addAll(notMoveableFilterExpr);
        } else {
            // nothing must be done
            newOp = opLeftJoin;
        }

        // restore filterexpressions
        this.filterExpr = filterExprBeforeOpLeftJoin;

        this.stack.push(newOp);
    }

    /**
     * Checks first if a filterexpression can be moved down. And after visits
     * the operator
     */
    private void checkMoveDownFilterExprAndVisitOpDiff(Op2 opDiff) {
        Op left;
        Op right;
        Op newOp;
        List<Expr> filterExprBeforeOpUnionOpJoin, filterExprAfterOpUnionOpJoin, notMoveableFilterExpr;

        // contains the filterexpressions that are valid before this op2
        filterExprBeforeOpUnionOpJoin = new ArrayList<>(this.filterExpr);
        // contains the filterexpressions that are valid after this op2
        // this is needed because during the bottom-up-stepping all
        // filterexpressions
        // which could not be transformed down, must be inserted by means of an
        // OpFilter
        // above this op2
        filterExprAfterOpUnionOpJoin = new ArrayList<>();

        // check left subtree
        if ((left = opDiff.getLeft()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // left subtree
            this.filterExpr = calcValidFilterExpr(
                    filterExprBeforeOpUnionOpJoin, left);
            filterExprAfterOpUnionOpJoin.addAll(this.filterExpr);
            // step down
            opDiff.getLeft().visit(this);
            left = stack.pop();
        }

        // check the right subtree
        if ((right = opDiff.getRight()) != null) {
            // calculate the set of filterexpressions that are also valid for
            // the
            // right subtree
            this.filterExpr = calcValidFilterExpr(filterExprBeforeOpUnionOpJoin, right);
            filterExprAfterOpUnionOpJoin.addAll(this.filterExpr);
            // step down
            opDiff.getRight().visit(this);
            right = stack.pop();
        }

        // note: filterExprAfterOpUnion contains now all filterexpressions which
        // could
        // be moved down
        // now calculate all filterexpressions which were not moveable
        notMoveableFilterExpr = new ArrayList<>(filterExprBeforeOpUnionOpJoin);
        notMoveableFilterExpr.removeAll(filterExprAfterOpUnionOpJoin);

        // if there are some filterexpressions which could not be moved down,
        // an opFilter must be inserted that contains this filterexpressions
        if (!notMoveableFilterExpr.isEmpty()) {
            // create the filter
            newOp = OpFilter.ensureFilter(OpDiff.create(left, right));
            // add the conditions
            ((OpFilter) newOp).getExprs().getList().addAll(notMoveableFilterExpr);
        } else {
            // nothing must be done
            newOp = opDiff;
        }

        // restore filterexpressions
        this.filterExpr = filterExprBeforeOpUnionOpJoin;

        this.stack.push(newOp);
    }
}