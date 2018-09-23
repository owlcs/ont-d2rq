package de.fuberlin.wiwiss.d2rq.nodes;

import de.fuberlin.wiwiss.d2rq.algebra.ColumnRenamer;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.RelationalOperators;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ResultRow;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;

import java.util.List;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public class TypedNodeMaker implements NodeMaker {
    public final static NodeType URI = new URINodeType();
    public final static NodeType BLANK = new BlankNodeType();
    public final static NodeType PLAIN_LITERAL = new LiteralNodeType("", null);
    public final static NodeType XSD_DATE = new DateLiteralNodeType();
    public final static NodeType XSD_TIME = new TimeLiteralNodeType();
    public final static NodeType XSD_DATETIME = new DateTimeLiteralNodeType();
    public final static NodeType XSD_BOOLEAN = new BooleanLiteralNodeType();

    public static NodeType languageLiteral(String language) {
        return new LiteralNodeType(language, null);
    }

    public static NodeType typedLiteral(RDFDatatype datatype) {
        // TODO These subclasses can be abolished; just introduce an RDFDatatypeValidator instead
        if (datatype.equals(XSDDatatype.XSDdate)) {
            return XSD_DATE;
        }
        if (datatype.equals(XSDDatatype.XSDtime)) {
            return XSD_TIME;
        }
        if (datatype.equals(XSDDatatype.XSDdateTime)) {
            return XSD_DATETIME;
        }
        if (datatype.equals(XSDDatatype.XSDboolean)) {
            return XSD_BOOLEAN;
        }
        return new LiteralNodeType("", datatype);
    }

    private NodeType nodeType;
    private ValueMaker valueMaker;
    private boolean isUnique;

    public TypedNodeMaker(NodeType nodeType, ValueMaker valueMaker, boolean isUnique) {
        this.nodeType = nodeType;
        this.valueMaker = valueMaker;
        this.isUnique = isUnique;
    }

    @Override
    public Set<ProjectionSpec> projectionSpecs() {
        return this.valueMaker.projectionSpecs();
    }

    @Override
    public boolean isUnique() {
        return this.isUnique;
    }

    @Override
    public void describeSelf(NodeSetFilter c) {
        this.nodeType.matchConstraint(c);
        this.valueMaker.describeSelf(c);
    }

    public ValueMaker valueMaker() {
        return this.valueMaker;
    }

    @Override
    public Node makeNode(ResultRow tuple) {
        String value = this.valueMaker.makeValue(tuple);
        if (value == null) {
            return null;
        }
        return this.nodeType.makeNode(value);
    }

    @Override
    public NodeMaker selectNode(Node node, RelationalOperators sideEffects) {
        if (node.equals(Node.ANY) || node.isVariable()) {
            return this;
        }
        if (!this.nodeType.matches(node)) {
            return NodeMaker.EMPTY;
        }
        String value = this.nodeType.extractValue(node);
        if (value == null) {
            return NodeMaker.EMPTY;
        }
        Expression expr = valueMaker.valueExpression(value);
        if (expr.isFalse()) {
            sideEffects.select(Expression.FALSE);
            return NodeMaker.EMPTY;
        }
        sideEffects.select(expr);
        return new FixedNodeMaker(node, isUnique());
    }

    @Override
    public NodeMaker renameAttributes(ColumnRenamer renamer) {
        return new TypedNodeMaker(this.nodeType, this.valueMaker.renameAttributes(renamer), this.isUnique);
    }

    @Override
    public List<OrderSpec> orderSpecs(boolean ascending) {
        // TODO: Consider the node type (e.g., RDF datatype) in ordering,
        //       rather than just deferring to the underlying value maker
        return valueMaker.orderSpecs(ascending);
    }

    @Override
    public String toString() {
        return this.nodeType.toString() + "(" + this.valueMaker + ")";
    }

    public interface NodeType {
        String extractValue(Node node);

        Node makeNode(String value);

        void matchConstraint(NodeSetFilter c);

        boolean matches(Node node);
    }

    private static class URINodeType implements NodeType {
        @Override
        public String extractValue(Node node) {
            return node.getURI();
        }

        @Override
        public Node makeNode(String value) {
            return NodeFactory.createURI(value);
        }

        @Override
        public void matchConstraint(NodeSetFilter c) {
            c.limitToURIs();
        }

        @Override
        public boolean matches(Node node) {
            return node.isURI();
        }

        @Override
        public String toString() {
            return "URI";
        }
    }

    private static class BlankNodeType implements NodeType {
        @Override
        public String extractValue(Node node) {
            return node.getBlankNodeLabel();
        }

        @Override
        public Node makeNode(String value) {
            return NodeFactory.createBlankNode(value);
        }

        @Override
        public void matchConstraint(NodeSetFilter c) {
            c.limitToBlankNodes();
        }

        @Override
        public boolean matches(Node node) {
            return node.isBlank();
        }

        @Override
        public String toString() {
            return "Blank";
        }
    }

    private static class LiteralNodeType implements NodeType {
        private String language;
        private RDFDatatype datatype;

        LiteralNodeType(String language, RDFDatatype datatype) {
            this.language = language == null ? "" : language;
            this.datatype = datatype; // null datatype means any literal.
        }

        @Override
        public String extractValue(Node node) {
            return node.getLiteralLexicalForm();
        }

        @Override
        public Node makeNode(String value) {
            return NodeFactory.createLiteral(value, this.language, this.datatype);
        }

        @Override
        public void matchConstraint(NodeSetFilter c) {
            c.limitToLiterals(this.language, this.datatype);
        }

        @Override
        public boolean matches(Node node) {
            return node.isLiteral()
                    && language.equals(node.getLiteralLanguage())
                    && (datatype == null || datatype.equals(node.getLiteralDatatype()));
            //&& ((this.datatype == null && node.getLiteralDatatype() == null)
            //|| (this.datatype != null && this.datatype.equals(node.getLiteralDatatype())));
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("Literal");
            if (!"".equals(this.language)) {
                result.append("@").append(this.language);
            }
            if (this.datatype != null) {
                result.append("^^").append(PrettyPrinter.toString(this.datatype));
            }
            return result.toString();
        }
    }

    private static class DateLiteralNodeType extends LiteralNodeType {
        DateLiteralNodeType() {
            super("", XSDDatatype.XSDdate);
        }

        @Override
        public boolean matches(Node node) {
            return super.matches(node) && XSDDatatype.XSDdate.isValid(node.getLiteralLexicalForm());
        }

        @Override
        public Node makeNode(String value) {
            if (!XSDDatatype.XSDdate.isValid(value)) return null;
            return NodeFactory.createLiteral(value, null, XSDDatatype.XSDdate);
        }
    }

    private static class TimeLiteralNodeType extends LiteralNodeType {
        TimeLiteralNodeType() {
            super("", XSDDatatype.XSDtime);
        }

        @Override
        public boolean matches(Node node) {
            return super.matches(node) && XSDDatatype.XSDtime.isValid(node.getLiteralLexicalForm());
        }

        @Override
        public Node makeNode(String value) {
            if (!XSDDatatype.XSDtime.isValid(value)) return null;
            return NodeFactory.createLiteral(value, null, XSDDatatype.XSDtime);
        }
    }

    private static class DateTimeLiteralNodeType extends LiteralNodeType {
        DateTimeLiteralNodeType() {
            super("", XSDDatatype.XSDdateTime);
        }

        @Override
        public boolean matches(Node node) {
            return super.matches(node) && XSDDatatype.XSDdateTime.isValid(node.getLiteralLexicalForm());
        }

        @Override
        public Node makeNode(String value) {
            if (!XSDDatatype.XSDdateTime.isValid(value)) return null;
            return NodeFactory.createLiteral(value, null, XSDDatatype.XSDdateTime);
        }
    }

    private static class BooleanLiteralNodeType extends LiteralNodeType {
        private final static Node TRUE = NodeFactory.createLiteral("true", null, XSDDatatype.XSDboolean);
        private final static Node FALSE = NodeFactory.createLiteral("false", null, XSDDatatype.XSDboolean);

        BooleanLiteralNodeType() {
            super("", XSDDatatype.XSDboolean);
        }

        @Override
        public boolean matches(Node node) {
            return super.matches(node) && XSDDatatype.XSDboolean.isValid(node.getLiteralLexicalForm());
        }

        @Override
        public Node makeNode(String value) {
            if ("0".equals(value) || "false".equals(value)) return FALSE;
            if ("1".equals(value) || "true".equals(value)) return TRUE;
            return null;
        }
    }
}
