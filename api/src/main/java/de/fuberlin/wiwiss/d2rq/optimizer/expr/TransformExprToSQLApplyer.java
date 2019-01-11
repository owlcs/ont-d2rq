package de.fuberlin.wiwiss.d2rq.optimizer.expr;

import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.expr.*;
import de.fuberlin.wiwiss.d2rq.nodes.*;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.*;
import org.apache.jena.sparql.expr.nodevalue.NodeFunctions;
import org.apache.jena.sparql.expr.nodevalue.NodeValueBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Attempts to transform a SPARQL FILTER Expr to a SQL Expression
 * <p>
 * Notes:
 * <ul>
 * <li>Literals using d2rq:pattern cannot be compared.</li>
 * <li>No XSD type checking/conversion or constructor functions yet.</li>
 * </ul>
 *
 * @author Herwig Leimer
 * @author Giovanni Mels
 */
@SuppressWarnings("WeakerAccess")
public final class TransformExprToSQLApplyer implements ExprVisitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformExprToSQLApplyer.class);

    /**
     * Converts a SPARQL filter expression to an SQL expression
     *
     * @param expr         The root node of an {@link Expr Expr} tree, contains the SPARQL filter.
     * @param nodeRelation The relation supplying the values to apply the filter on.
     * @return The root node of an {@link Expression Expression} tree, if conversion was successful, <code>null</code> otherwise.
     */
    public static Expression convert(final Expr expr, final NodeRelation nodeRelation) {
        TransformExprToSQLApplyer transformer = new TransformExprToSQLApplyer(nodeRelation);
        expr.visit(transformer);
        return transformer.result();
    }

    // TODO Expression.FALSE and Expression.TRUE are not constants
    private static final Expression CONSTANT_FALSE = new ConstantEx("false", NodeValueBoolean.FALSE.asNode());
    private static final Expression CONSTANT_TRUE = new ConstantEx("true", NodeValueBoolean.TRUE.asNode());

    private final NodeRelation nodeRelation;
    private final Stack<Expression> expression = new Stack<>();

    private boolean convertable;     // flag if converting was possible
    private String reason;   // reason why converting failed

    /**
     * Creates an expression transformer.
     *
     * @param nodeRelation {@link NodeRelation}
     */
    public TransformExprToSQLApplyer(NodeRelation nodeRelation) {
        this.convertable = true;
        this.nodeRelation = nodeRelation;
    }

    /**
     * Returns the sql Expression
     *
     * @return the transformed sql expression if converting was possible, otherwise null.
     */
    public Expression result() {
        if (!convertable) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("filter conversion failed: {}", reason);
            return null;
        }

        if (expression.size() != 1)
            throw new IllegalStateException("something is seriously wrong");

        Expression result = expression.pop();
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("Resulting filter = {}", result);
        return result;
    }

    @Override
    public void visit(ExprFunction0 func) {
        visitExprFunction(func);
    }

    @Override
    public void visit(ExprFunction1 function) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("visit ExprFunction {}", function);

        if (!convertable) {
            expression.push(Expression.FALSE); // prevent stack empty exceptions when conversion
            return;                            // fails in the middle of a multi-arg operator conversion
        }
        convertFunction(function);
    }

    @Override
    public void visit(ExprFunction2 function) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("visit ExprFunction {}", function);

        if (!convertable) {
            expression.push(Expression.FALSE); // prevent stack empty exceptions when conversion
            return;                            // fails in the middle of a multi-arg operator conversion
        }
        convertFunction(function);
    }

    @Override
    public void visit(ExprFunction3 func) {
        visitExprFunction(func);
    }

    @Override
    public void visit(ExprFunctionN func) {
        visitExprFunction(func);
    }

    @Override
    public void visit(ExprFunctionOp funcOp) {
        visitExprFunction(funcOp);
    }

    @Override
    public void visit(ExprAggregator eAgg) {
        conversionFailed(eAgg);
    }

    @Override
    public void visit(ExprNone exprNone) {
        conversionFailed(exprNone);
    }

    public void visit(ExprVar var) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("visit ExprVar {}", var);

        if (!convertable) {
            expression.push(Expression.FALSE); // prevent stack empty exceptions when conversion
            return;                            // fails in the middle of a multi-arg operator conversion
        }

        String varName = var.getVarName();

        // if expression contains a blank node, no conversion to sql can be done
        if (Var.isBlankNodeVarName(varName)) {
            conversionFailed("blank nodes not supported", var);
            return;
        }

        List<Expression> expressions = toExpression(var);
        if (expressions.size() == 1) {
            expression.push(expressions.get(0));
        } else {
            // no single sql-column for sparql-var does exist break up conversion
            // (the case for Pattern ValueMakers)
            conversionFailed("multi column pattern valuemakers not supported", var);
        }
    }

    @Override
    public void visit(NodeValue value) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("visit NodeValue {}", value);

        if (!convertable) {
            expression.push(Expression.FALSE); // prevent stack empty exceptions when conversion
            return;                            // fails in the middle of a multi-arg operator conversion
        }

        if (value.isDecimal() || value.isDouble() || value.isFloat() || value.isInteger() || value.isNumber()) {
            expression.push(new ConstantEx(value.asString(), value.asNode()));
        } else if (value.isDateTime()) {
            // Convert xsd:dateTime: CCYY-MM-DDThh:mm:ss
            // to SQL-92 TIMESTAMP: CCYY-MM-DD hh:mm:ss[.fraction]
            // TODO support time zones (WITH TIME ZONE columns)
            expression.push(new ConstantEx(value.asString().replace("T", " "), value.asNode()));
        } else {
            expression.push(new ConstantEx(value.asString(), value.asNode()));
        }
    }

    private void visitExprFunction(ExprFunction function) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("visit ExprFunction {}", function);

        if (!convertable) {
            expression.push(Expression.FALSE); // prevent stack empty exceptions when conversion
            return;                            // fails in the middle of a multi-arg operator conversion
        }
        if (!extensionSupports(function)) {
            conversionFailed(function);
            return;
        }

        for (int i = 0; i < function.numArgs(); i++)
            function.getArg(i + 1).visit(this);
        List<Expression> args = new ArrayList<>(function.numArgs());

        for (int i = 0; i < function.numArgs(); i++)
            args.add(expression.pop());
        Collections.reverse(args);
        extensionConvert(function, args);
    }

    /**
     * Delivers the corresponding sql-expression for a sparql-var
     *
     * @param exprVar - a sparql-expr-var
     * @return List<Expression> - the equivalent sql-expressions
     */
    private List<Expression> toExpression(ExprVar exprVar) {
        ArrayList<Expression> result = new ArrayList<>();

        if (this.nodeRelation == null || exprVar == null) {
            return result;
        }
        // get the nodemaker for the expr-var
        NodeMaker nodeMaker = nodeRelation.nodeMaker(exprVar.asVar());
        if (nodeMaker instanceof TypedNodeMaker) {
            TypedNodeMaker typedNodeMaker = (TypedNodeMaker) nodeMaker;
            Iterator<ProjectionSpec> it = typedNodeMaker.projectionSpecs().iterator();
            if (!it.hasNext()) {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("no projection spec for {}, assuming constant", exprVar);
                Node node = typedNodeMaker.makeNode(null);
                result.add(new ConstantEx(NodeValue.makeNode(node).asString(), node));
            }
            while (it.hasNext()) {
                ProjectionSpec projectionSpec = it.next();

                if (projectionSpec == null)
                    return Collections.emptyList();

                if (projectionSpec instanceof Attribute) {
                    result.add(new AttributeExprEx((Attribute) projectionSpec, nodeMaker));
                } else {
                    // projectionSpec is a ExpressionProjectionSpec
                    ExpressionProjectionSpec expressionProjectionSpec = (ExpressionProjectionSpec) projectionSpec;
                    Expression expression = expressionProjectionSpec.toExpression();
                    if (expression instanceof SQLExpression)
                        result.add(expression);
                    else
                        return Collections.emptyList();
                }
            }
        } else if (nodeMaker instanceof FixedNodeMaker) {
            FixedNodeMaker fixedNodeMaker = (FixedNodeMaker) nodeMaker;
            Node node = fixedNodeMaker.makeNode(null);
            result.add(new ConstantEx(NodeValue.makeNode(node).asString(), node));
        }

        return result;
    }

    private void convertFunction(ExprFunction1 expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertFunction {}", expr);

        if (expr instanceof E_Str) {
            convertStr((E_Str) expr);
        } else if (expr instanceof E_IsIRI) {
            convertIsIRI((E_IsIRI) expr);
        } else if (expr instanceof E_IsBlank) {
            convertIsBlank((E_IsBlank) expr);
        } else if (expr instanceof E_IsLiteral) {
            convertIsLiteral((E_IsLiteral) expr);
        } else if (expr instanceof E_Datatype) {
            convertDataType((E_Datatype) expr);
        } else if (expr instanceof E_Lang) {
            convertLang((E_Lang) expr);
        } else if (expr instanceof E_LogicalNot) {
            convertLogicalNot((E_LogicalNot) expr);
        } else if (expr instanceof E_UnaryPlus) {
            convert((E_UnaryPlus) expr);
        } else if (expr instanceof E_UnaryMinus) {
            convert((E_UnaryMinus) expr);
        } else if (extensionSupports(expr)) {
            expr.getArg(1).visit(this);
            Expression e1 = expression.pop();
            List<Expression> args = Collections.singletonList(e1);
            extensionConvert(expr, args);
        } else {
            conversionFailed(expr);
        }
    }

    private void convertFunction(ExprFunction2 expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertFunction {}", expr);

        if (expr instanceof E_LogicalOr) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(e1.or(e2));
        } else if (expr instanceof E_LessThan) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new LessThan(e1, e2));
        } else if (expr instanceof E_LessThanOrEqual) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new LessThanOrEqual(e1, e2));
        } else if (expr instanceof E_GreaterThan) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new GreaterThan(e1, e2));
        } else if (expr instanceof E_GreaterThanOrEqual) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new GreaterThanOrEqual(e1, e2));
        } else if (expr instanceof E_Add) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new Add(e1, e2));
        } else if (expr instanceof E_Subtract) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new Subtract(e1, e2));
        } else if (expr instanceof E_Multiply) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new Multiply(e1, e2));
        } else if (expr instanceof E_Divide) {
            expr.getArg1().visit(this);
            expr.getArg2().visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();
            expression.push(new Divide(e1, e2));
        } else if (expr instanceof E_Equals) {
            convertEquals((E_Equals) expr);
        } else if (expr instanceof E_NotEquals) {
            convertNotEquals((E_NotEquals) expr);
        } else if (expr instanceof E_LangMatches) {
            convertLangMatches((E_LangMatches) expr);
        } else if (expr instanceof E_SameTerm) {
            convertSameTerm((E_SameTerm) expr);
        } else if (extensionSupports(expr)) {
            expr.getArg(1).visit(this);
            expr.getArg(2).visit(this);
            Expression e2 = expression.pop();
            Expression e1 = expression.pop();

            List<Expression> args = new ArrayList<>(2);

            args.add(e1);
            args.add(e2);

            extensionConvert(expr, args);
        } else {
            conversionFailed(expr);
        }
    }

    private void convertEquals(E_Equals expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertEquals {}", expr);

        convertEquality(expr);
    }

    private void convertEquality(ExprFunction2 expr) {
        expr.getArg1().visit(this);
        expr.getArg2().visit(this);
        Expression e2 = expression.pop();
        Expression e1 = expression.pop();

        // TODO Expression.FALSE and Expression.TRUE are not constants
        if (e1.equals(Expression.FALSE))
            e1 = CONSTANT_FALSE;
        else if (e1.equals(Expression.TRUE))
            e1 = CONSTANT_TRUE;
        if (e2.equals(Expression.FALSE))
            e2 = CONSTANT_FALSE;
        else if (e2.equals(Expression.TRUE))
            e2 = CONSTANT_TRUE;

        if (e1 instanceof AttributeExprEx && e2 instanceof Constant || e2 instanceof AttributeExprEx && e1 instanceof Constant) {

            AttributeExprEx variable;
            ConstantEx constant;

            if (e1 instanceof AttributeExprEx) {
                variable = (AttributeExprEx) e1;
                constant = (ConstantEx) e2;
            } else {
                variable = (AttributeExprEx) e2;
                constant = (ConstantEx) e1;
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isEqual({}, {})", variable, constant);

            NodeMaker nm = variable.getNodeMaker();

            if (nm instanceof TypedNodeMaker) {
                ValueMaker vm = ((TypedNodeMaker) nm).valueMaker();
                Node node = constant.getNode();
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("checking {} with {}", node, nm);

                if (XSD.isNumeric(node)) {
                    DetermineNodeType filter = new DetermineNodeType();
                    nm.describeSelf(filter);
                    RDFDatatype datatype = filter.getDatatype();
                    if (datatype != null && XSD.isNumeric(datatype)) {
                        RDFDatatype numericType = XSD.getNumericType(datatype, node.getLiteralDatatype());
                        nm = cast(nm, numericType);
                        node = XSD.cast(node, numericType);
                    }
                }

                boolean empty = nm.selectNode(node, RelationalOperators.DUMMY).equals(NodeMaker.EMPTY);
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("result {}", empty);
                if (!empty) {
                    if (node.isURI())
                        expression.push(vm.valueExpression(node.getURI()));
                    else if (node.isLiteral()) {
                        if (XSD.isSupported(node.getLiteralDatatype()))
                            expression.push(vm.valueExpression(constant.value()));
                        else
                            conversionFailed("cannot compare values of type " + node.getLiteralDatatypeURI(), expr);
                    } else
                        conversionFailed(expr); // TODO blank nodes?
                    return;
                } else {
                    expression.push(Expression.FALSE);
                    return;
                }
            } else {
                LOGGER.warn("nm is not a TypedNodemaker");
            }
        } else if (e1 instanceof ConstantEx && e2 instanceof ConstantEx) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isEqual({}, {})", e1, e2);
            Node c1 = ((ConstantEx) e1).getNode();
            Node c2 = ((ConstantEx) e2).getNode();
            boolean equals;
            if (XSD.isNumeric(c1) && XSD.isNumeric(c2)) {
                RDFDatatype datatype = XSD.getNumericType(c1.getLiteralDatatype(), c2.getLiteralDatatype());
                equals = XSD.cast(c1, datatype).equals(XSD.cast(c2, datatype));
            } else if (isSimpleLiteral(c1) && isSimpleLiteral(c2)) {
                equals = c1.getLiteralValue().equals(c2.getLiteralValue());
            } else if (XSD.isString(c1) && XSD.isString(c2)) {
                equals = c1.getLiteralValue().equals(c2.getLiteralValue());
            } else {
                try {
                    equals = NodeFunctions.rdfTermEquals(c1, c2);
                } catch (ExprEvalException e) {
                    equals = false;
                }
            }
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("constants equal? {}", equals);
            expression.push(equals ? Expression.TRUE : Expression.FALSE);
            return;
        } else if (e1 instanceof AttributeExprEx && e2 instanceof AttributeExprEx) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isEqual({}, {})", e1, e2);
            AttributeExprEx variable1 = (AttributeExprEx) e1;
            AttributeExprEx variable2 = (AttributeExprEx) e2;

            NodeMaker nm1 = variable1.getNodeMaker();
            NodeMaker nm2 = variable2.getNodeMaker();

            DetermineNodeType filter1 = new DetermineNodeType();
            nm1.describeSelf(filter1);
            RDFDatatype datatype1 = filter1.getDatatype();

            DetermineNodeType filter2 = new DetermineNodeType();
            nm2.describeSelf(filter2);
            RDFDatatype datatype2 = filter2.getDatatype();

            if (datatype1 != null && XSD.isNumeric(datatype1) && datatype2 != null && XSD.isNumeric(datatype2)) {
                RDFDatatype numericType = XSD.getNumericType(filter1.getDatatype(), filter2.getDatatype());
                nm1 = cast(nm1, numericType);
                nm2 = cast(nm2, numericType);
            }
            NodeSetConstraintBuilder nodeSet = new NodeSetConstraintBuilder();
            nm1.describeSelf(nodeSet);
            nm2.describeSelf(nodeSet);

            if (nodeSet.isEmpty()) {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("nodes {} & {} incompatible", nm1, nm2);
                expression.push(Expression.FALSE);
                return;
            }
        }

        expression.push(Equality.create(e1, e2));
    }

    private void convertNotEquals(E_NotEquals expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertNotEquals {}", expr);

        expr.getArg1().visit(this);
        expr.getArg2().visit(this);
        Expression e2 = expression.pop();
        Expression e1 = expression.pop();

        // TODO Expression.FALSE and Expression.TRUE are not constants
        if (e1.equals(Expression.FALSE))
            e1 = CONSTANT_FALSE;
        else if (e1.equals(Expression.TRUE))
            e1 = CONSTANT_TRUE;
        if (e2.equals(Expression.FALSE))
            e2 = CONSTANT_FALSE;
        else if (e2.equals(Expression.TRUE))
            e2 = CONSTANT_TRUE;

        if (e1 instanceof AttributeExprEx && e2 instanceof Constant || e2 instanceof AttributeExprEx && e1 instanceof Constant) {
            AttributeExprEx variable;
            ConstantEx constant;

            if (e1 instanceof AttributeExprEx) {
                variable = (AttributeExprEx) e1;
                constant = (ConstantEx) e2;
            } else {
                variable = (AttributeExprEx) e2;
                constant = (ConstantEx) e1;
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isNotEqual({}, {})", variable, constant);

            NodeMaker nm = variable.getNodeMaker();

            if (nm instanceof TypedNodeMaker) {
                ValueMaker vm = ((TypedNodeMaker) nm).valueMaker();
                Node node = constant.getNode();
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("checking {} with {}", node, nm);
                if (XSD.isNumeric(node)) {
                    DetermineNodeType filter = new DetermineNodeType();
                    nm.describeSelf(filter);
                    RDFDatatype datatype = filter.getDatatype();
                    if (datatype != null && XSD.isNumeric(datatype)) {
                        RDFDatatype numericType = XSD.getNumericType(datatype, node.getLiteralDatatype());
                        nm = cast(nm, numericType);
                        node = XSD.cast(node, numericType);
                    }
                }
                boolean empty = nm.selectNode(node, RelationalOperators.DUMMY).equals(NodeMaker.EMPTY);
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("result {}", empty);
                if (!empty) {
                    if (node.isURI())
                        expression.push(new Negation(vm.valueExpression(node.getURI())));
                    else if (node.isLiteral()) {
                        if (XSD.isSupported(node.getLiteralDatatype()))
                            expression.push(new Negation(vm.valueExpression(constant.value())));
                        else // type = boolean or an unknown type
                            conversionFailed("cannot compare values of type " + node.getLiteralDatatypeURI(), expr);
                    } else
                        conversionFailed(expr); // TODO blank nodes?
                    return;
                } else {
                    expression.push(Expression.TRUE);
                    return;
                }
            }
        } else if (e1 instanceof ConstantEx && e2 instanceof ConstantEx) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isNotEqual({}, {})", e1, e2);
            Node c1 = ((ConstantEx) e1).getNode();
            Node c2 = ((ConstantEx) e2).getNode();
            boolean equals;
            if (XSD.isNumeric(c1) && XSD.isNumeric(c2)) {
                RDFDatatype datatype = XSD.getNumericType(c1.getLiteralDatatype(), c2.getLiteralDatatype());
                equals = XSD.cast(c1, datatype).equals(XSD.cast(c2, datatype));
            } else if (isSimpleLiteral(c1) && isSimpleLiteral(c2)) {
                equals = c1.getLiteralValue().equals(c2.getLiteralValue());
            } else if (XSD.isString(c1) && XSD.isString(c2)) {
                equals = c1.getLiteralValue().equals(c2.getLiteralValue());
            } else {
                try {
                    equals = NodeFunctions.rdfTermEquals(c1, c2);
                } catch (ExprEvalException e) {
                    equals = false;
                }
            }
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("constants equal? {}", equals);
            expression.push(equals ? Expression.FALSE : Expression.TRUE);
            return;
        } else if (e1 instanceof AttributeExprEx && e2 instanceof AttributeExprEx) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isNotEqual({}, {})", e1, e2);
            AttributeExprEx variable1 = (AttributeExprEx) e1;
            AttributeExprEx variable2 = (AttributeExprEx) e2;

            NodeMaker nm1 = variable1.getNodeMaker();
            NodeMaker nm2 = variable2.getNodeMaker();

            DetermineNodeType filter1 = new DetermineNodeType();
            nm1.describeSelf(filter1);
            RDFDatatype datatype1 = filter1.getDatatype();

            DetermineNodeType filter2 = new DetermineNodeType();
            nm2.describeSelf(filter2);
            RDFDatatype datatype2 = filter2.getDatatype();

            if (datatype1 != null && XSD.isNumeric(datatype1) && datatype2 != null && XSD.isNumeric(datatype2)) {
                RDFDatatype numericType = XSD.getNumericType(filter1.getDatatype(), filter2.getDatatype());
                nm1 = cast(nm1, numericType);
                nm2 = cast(nm2, numericType);
            }

            NodeSetConstraintBuilder nodeSet = new NodeSetConstraintBuilder();
            nm1.describeSelf(nodeSet);
            nm2.describeSelf(nodeSet);

            if (nodeSet.isEmpty()) {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("nodes {} & {} incompatible", nm1, nm2);
                expression.push(Expression.TRUE);
                return;
            }
        }

        expression.push(new Negation(Equality.create(e1, e2)));
    }

    private void convertLogicalNot(E_LogicalNot expr) {
        expr.getArg().visit(this);
        Expression e1 = expression.pop();
        if (e1 instanceof Negation)
            expression.push(((Negation) e1).getBase());
        else
            expression.push(new Negation(e1));
    }

    private void convert(E_UnaryPlus expr) {
        expr.getArg().visit(this);
    }

    private void convert(E_UnaryMinus expr) {
        expr.getArg().visit(this);
        Expression e1 = expression.pop();
        if (e1 instanceof UnaryMinus)
            expression.push(((UnaryMinus) e1).getBase());
        else
            expression.push(new UnaryMinus(e1));
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.2
     *
     * "(ISIRI) Returns true if term is an IRI. Returns false otherwise."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertIsIRI(E_IsIRI expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertIsIRI {}", expr);

        expr.getArg().visit(this);

        Expression arg = expression.pop();

        if (arg instanceof AttributeExprEx) {
            AttributeExprEx variable = (AttributeExprEx) arg;
            NodeMaker nm = variable.getNodeMaker();
            DetermineNodeType filter = new DetermineNodeType();
            nm.describeSelf(filter);
            expression.push(filter.isLimittedToURIs() ? Expression.TRUE : Expression.FALSE);
        } else if (arg instanceof ConstantEx) {
            ConstantEx constant = (ConstantEx) arg;
            Node node = constant.getNode();
            expression.push(node.isURI() ? Expression.TRUE : Expression.FALSE);
        } else {
            conversionFailed(expr);
        }
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.3
     *
     * "(ISBLANK) Returns true if term is a blank node. Returns false otherwise."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertIsBlank(E_IsBlank expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertIsBlank {}", expr);

        expr.getArg().visit(this);

        Expression arg = expression.pop();

        if (arg instanceof AttributeExprEx) {
            AttributeExprEx variable = (AttributeExprEx) arg;
            NodeMaker nm = variable.getNodeMaker();
            DetermineNodeType filter = new DetermineNodeType();
            nm.describeSelf(filter);
            expression.push(filter.isLimittedToBlankNodes() ? Expression.TRUE : Expression.FALSE);
        } else if (arg instanceof ConstantEx) {
            ConstantEx constant = (ConstantEx) arg;
            Node node = constant.getNode();
            expression.push(node.isBlank() ? Expression.TRUE : Expression.FALSE);
        } else {
            conversionFailed(expr);
        }
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.4
     *
     * "(ISLITERAL) Returns true if term is a literal. Returns false otherwise."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertIsLiteral(E_IsLiteral expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertIsLiteral {}", expr);

        expr.getArg().visit(this);

        Expression arg = expression.pop();

        if (LOGGER.isDebugEnabled())
            LOGGER.debug("arg {}", arg);

        if (arg instanceof AttributeExprEx) {
            AttributeExprEx variable = (AttributeExprEx) arg;
            NodeMaker nm = variable.getNodeMaker();

            DetermineNodeType filter = new DetermineNodeType();
            nm.describeSelf(filter);
            expression.push(filter.isLimittedToLiterals() ? Expression.TRUE : Expression.FALSE);
        } else if (arg instanceof ConstantEx) {
            ConstantEx constant = (ConstantEx) arg;
            Node node = constant.getNode();
            expression.push(node.isLiteral() ? Expression.TRUE : Expression.FALSE);
        } else {
            conversionFailed(expr);
        }
    }


    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.4
     *
     * "(STR) Returns the lexical form of a literal; returns the codepoint representation of an IRI."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertStr(E_Str expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertStr {}", expr);

        expr.getArg().visit(this);

        Expression arg = expression.pop();

        if (arg instanceof AttributeExprEx) {
            // make a new AttributeExprEx with changed NodeMaker, which returns plain literal
            // TODO this seems to work, but needs more testing.
            AttributeExprEx attribute = (AttributeExprEx) arg;
            TypedNodeMaker nodeMaker = (TypedNodeMaker) attribute.getNodeMaker();
            TypedNodeMaker newNodeMaker = new TypedNodeMaker(TypedNodeMaker.PLAIN_LITERAL, nodeMaker.valueMaker(), nodeMaker.isUnique());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("changing nodemaker {} to {}", nodeMaker, newNodeMaker);
            expression.push(new AttributeExprEx(attribute.attributes().iterator().next(), newNodeMaker));
        } else if (arg instanceof ConstantEx) {
            ConstantEx constant = (ConstantEx) arg;
            Node node = constant.getNode();
            String lexicalForm = node.getLiteral().getLexicalForm();
            node = NodeFactory.createLiteral(lexicalForm);
            ConstantEx constantEx = new ConstantEx(NodeValue.makeNode(node).asString(), node);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("pushing {}", constantEx);
            expression.push(constantEx);
        } else {
            conversionFailed(expr);
        }
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.6
     *
     * "(LANG) Returns the language tag of ltrl, if it has one. It returns "" if ltrl has no language tag."
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertLang(E_Lang expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertLang {}", expr);

        expr.getArg().visit(this);

        Expression arg = expression.pop();

        if (arg instanceof AttributeExprEx) {
            AttributeExprEx variable = (AttributeExprEx) arg;
            NodeMaker nm = variable.getNodeMaker();
            DetermineNodeType filter = new DetermineNodeType();
            nm.describeSelf(filter);
            String lang = filter.getLanguage();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("lang {}", lang);
            if (lang == null)
                lang = "";

            // NodeValue.makeString(lang); TODO better?
            Node node = NodeFactory.createLiteral(lang);

            ConstantEx constantEx = new ConstantEx(NodeValue.makeNode(node).asString(), node);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("pushing {}", constantEx);
            expression.push(constantEx);
        } else if (arg instanceof ConstantEx) {
            ConstantEx constant = (ConstantEx) arg;
            Node node = constant.getNode();
            if (!node.isLiteral()) {
                // type error, return false?
                LOGGER.warn("type error: {} is not a literal, returning FALSE", node);
                expression.push(Expression.FALSE);
                return;
            }
            String lang = node.getLiteralLanguage();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("lang {}", lang);
            if (lang == null)
                lang = "";

            node = NodeFactory.createLiteral(lang);

            ConstantEx constantEx = new ConstantEx(NodeValue.makeNode(node).asString(), node);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("pushing {}", constantEx);
            expression.push(constantEx);
        } else {
            conversionFailed(expr);
        }
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.7
     *
     * "(DATATYPE) Returns the datatype IRI of typedLit; returns xsd:string if the parameter is a simple literal."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertDataType(E_Datatype expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertDataType {}", expr);

        expr.getArg().visit(this);

        Expression arg = expression.pop();

        if (arg instanceof AttributeExprEx) {
            AttributeExprEx variable = (AttributeExprEx) arg;
            NodeMaker nm = variable.getNodeMaker();
            DetermineNodeType filter = new DetermineNodeType();
            nm.describeSelf(filter);
            if (!filter.isLimittedToLiterals()) {
                // type error, return false?
                LOGGER.warn("type error: {} is not a literal, returning FALSE", variable);
                expression.push(Expression.FALSE);
                return;
            }
            RDFDatatype datatype = filter.getDatatype();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("datatype {}", datatype);

            Node node = NodeFactory.createURI((datatype != null) ? datatype.getURI() : XSDDatatype.XSDstring.getURI());

            ConstantEx constantEx = new ConstantEx(NodeValue.makeNode(node).asString(), node);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("pushing {}", constantEx);
            expression.push(constantEx);
        } else if (arg instanceof ConstantEx) {
            ConstantEx constant = (ConstantEx) arg;
            Node node = constant.getNode();
            if (!node.isLiteral()) {
                // type error, return false?
                LOGGER.warn("type error: {} is not a literal, returning FALSE", node);
                expression.push(Expression.FALSE);
                return;
            }
            RDFDatatype datatype = node.getLiteralDatatype();
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("datatype {}", datatype);
            node = NodeFactory.createURI((datatype != null) ? datatype.getURI() : XSDDatatype.XSDstring.getURI());
            ConstantEx constantEx = new ConstantEx(NodeValue.makeNode(node).asString(), node);
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("pushing {}", constantEx);
            expression.push(constantEx);
        } else {
            conversionFailed(expr);
        }
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.11
     *
     * "(SAMETERM) Returns TRUE if term1 and term2 are the same RDF term as defined
     * in Resource Description Framework (RDF): Concepts and Abstract Syntax [CONCEPTS]; returns FALSE otherwise."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     * @see http://www.w3.org/TR/rdf-concepts/
     */
    private void convertSameTerm(E_SameTerm expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertSameTerm {}", expr);

        expr.getArg1().visit(this);
        expr.getArg2().visit(this);
        Expression e2 = expression.pop();
        Expression e1 = expression.pop();

        // TODO Expression.FALSE and Expression.TRUE are not constants
        if (e1.equals(Expression.FALSE))
            e1 = CONSTANT_FALSE;
        else if (e1.equals(Expression.TRUE))
            e1 = CONSTANT_TRUE;
        if (e2.equals(Expression.FALSE))
            e2 = CONSTANT_FALSE;
        else if (e2.equals(Expression.TRUE))
            e2 = CONSTANT_TRUE;

        if (e1 instanceof AttributeExprEx && e2 instanceof Constant || e2 instanceof AttributeExprEx && e1 instanceof Constant) {

            AttributeExprEx variable;
            ConstantEx constant;

            if (e1 instanceof AttributeExprEx) {
                variable = (AttributeExprEx) e1;
                constant = (ConstantEx) e2;
            } else {
                variable = (AttributeExprEx) e2;
                constant = (ConstantEx) e1;
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isEqual({}, {})", variable, constant);

            NodeMaker nm = variable.getNodeMaker();

            if (nm instanceof TypedNodeMaker) {
                ValueMaker vm = ((TypedNodeMaker) nm).valueMaker();
                Node node = constant.getNode();
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("checking {} with {}", node, nm);

                boolean empty = nm.selectNode(node, RelationalOperators.DUMMY).equals(NodeMaker.EMPTY);
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("result {}", empty);
                if (!empty) {
                    if (node.isURI())
                        expression.push(vm.valueExpression(node.getURI()));
                    else if (node.isLiteral())
                        expression.push(vm.valueExpression(constant.value()));
                    else
                        conversionFailed(expr); // TODO blank nodes?
                    return;
                } else {
                    expression.push(Expression.FALSE);
                    return;
                }
            } else {
                LOGGER.warn("nm is not a TypedNodemaker");
            }
        } else if (e1 instanceof ConstantEx && e2 instanceof ConstantEx) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isEqual({}, {})", e1, e2);
            ConstantEx constant1 = (ConstantEx) e1;
            ConstantEx constant2 = (ConstantEx) e2;
            boolean equals = NodeFunctions.sameTerm(constant1.getNode(), constant2.getNode());
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("constants same? {}", equals);
            expression.push(equals ? Expression.TRUE : Expression.FALSE);
            return;
        } else if (e1 instanceof AttributeExprEx && e2 instanceof AttributeExprEx) {
            if (LOGGER.isDebugEnabled())
                LOGGER.debug("isEqual({}, {})", e1, e2);
            AttributeExprEx variable1 = (AttributeExprEx) e1;
            AttributeExprEx variable2 = (AttributeExprEx) e2;

            NodeMaker nm1 = variable1.getNodeMaker();
            NodeMaker nm2 = variable2.getNodeMaker();

            NodeSetConstraintBuilder nodeSet = new NodeSetConstraintBuilder();
            nm1.describeSelf(nodeSet);
            nm2.describeSelf(nodeSet);

            if (nodeSet.isEmpty()) {
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("nodes {} & {}", nm1, nm2);
                expression.push(Expression.FALSE);
                return;
            }
        }

        expression.push(Equality.create(e1, e2));
    }

    /*
     * See http://www.w3.org/TR/rdf-sparql-query paragraph 11.4.12
     *
     * "(LANGMATCHES) returns true if language-tag (first argument) matches language-range (second argument)
     * per the basic filtering scheme defined in [RFC4647] section 3.3.1. language-range is a basic language
     * range per Matching of Language Tags [RFC4647] section 2.1. A language-range of "*" matches any non-empty
     * language-tag string."
     *
     * @see http://www.w3.org/TR/rdf-sparql-query
     */
    private void convertLangMatches(E_LangMatches expr) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("convertLangMatches {}", expr);

        expr.getArg1().visit(this);
        expr.getArg2().visit(this);
        Expression e2 = expression.pop();
        Expression e1 = expression.pop();

        if (e1 instanceof ConstantEx && e2 instanceof ConstantEx) {
            ConstantEx lang1 = (ConstantEx) e1;
            ConstantEx lang2 = (ConstantEx) e2;
            NodeValue nv1 = NodeValue.makeString(lang1.getNode().getLiteral().getLexicalForm());
            NodeValue nv2 = NodeValue.makeString(lang2.getNode().getLiteral().getLexicalForm());
            NodeValue match = NodeFunctions.langMatches(nv1, nv2);
            expression.push(match.equals(NodeValue.TRUE) ? Expression.TRUE : Expression.FALSE);
        } else {
            expression.push(Expression.FALSE);
        }
    }


    private void conversionFailed(Expr unconvertableExpr) {
        // prevent stack empty exceptions when conversion fails in the middle of a multi-arg operator conversion
        expression.push(Expression.FALSE);
        convertable = false;
        if (reason == null)
            reason = "cannot convert " + unconvertableExpr.toString();
    }

    private void conversionFailed(String message, Expr unconvertableExpr) {
        // prevent stack empty exceptions when conversion fails in the middle of a multi-arg operator conversion
        expression.push(Expression.FALSE);
        convertable = false;
        if (reason == null)
            reason = "cannot convert " + unconvertableExpr.toString() + ": " + message;
    }


    static NodeMaker cast(NodeMaker nodeMaker, RDFDatatype datatype) {
        if (nodeMaker instanceof TypedNodeMaker)
            return new TypedNodeMaker(TypedNodeMaker.typedLiteral(datatype), ((TypedNodeMaker) nodeMaker).valueMaker(), nodeMaker.isUnique());

        if (nodeMaker instanceof FixedNodeMaker) {
            Node node = nodeMaker.makeNode(null);
            return new FixedNodeMaker(XSD.cast(node, datatype), nodeMaker.isUnique());
        }

        throw new RuntimeException("unknown nodeMaker type");
    }

    static boolean isSimpleLiteral(Node node) {
        return node.isLiteral() && node.getLiteralDatatype() == null && "".equals(node.getLiteralLanguage());

    }

    // extension mechanism

    protected boolean extensionSupports(ExprFunction function) {
        return false;
    }

    protected void extensionConvert(ExprFunction function, List<Expression> args) {
    }

}
