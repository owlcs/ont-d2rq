package de.fuberlin.wiwiss.d2rq.nodes;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.values.BlankNodeID;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.values.Translator;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.core.Var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DetermineNodeType implements NodeSetFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetermineNodeType.class);

    private boolean limitedToURIs = false;
    private boolean limitedToBlankNodes = false;
    private boolean limitedToLiterals = false;

    private RDFDatatype datatype = null;
    private String language = null;

    public boolean isLimittedToURIs() {
        return limitedToURIs;
    }

    public RDFDatatype getDatatype() {
        return datatype;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isLimittedToBlankNodes() {
        return limitedToBlankNodes;
    }

    public boolean isLimittedToLiterals() {
        return limitedToLiterals;
    }

    @Override
    public void limitTo(Node node) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("limitting to {}", node);
        if (node.isURI())
            limitedToURIs = true;
        else if (node.isLiteral())
            limitedToLiterals = true;
        else if (Var.isBlankNodeVar(node))
            limitedToBlankNodes = true;
    }

    @Override
    public void limitToBlankNodes() {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("limitting to blank nodes");
        limitedToBlankNodes = true;
    }

    @Override
    public void limitToEmptySet() {
        LOGGER.warn("TODO DetermineNodeType.limitToEmptySet()");
    }

    @Override
    public void limitToLiterals(String language, RDFDatatype datatype) {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("limitting to literals");
        limitedToLiterals = true;
        this.datatype = datatype;
        this.language = language;
    }

    @Override
    public void limitToURIs() {
        if (LOGGER.isDebugEnabled())
            LOGGER.debug("limitting to URIs");
        limitedToURIs = true;
    }

    // FIXME Implement!
    @Override
    public void limitValues(String constant) {
        LOGGER.warn("TODO DetermineNodeType.limitValues() {}", constant);
    }

    // FIXME Implement!
    @Override
    public void limitValuesToAttribute(Attribute attribute) {
        LOGGER.warn("TODO DetermineNodeType.limitValuesToAttribute() {}", attribute);
    }

    // FIXME Implement!
    @Override
    public void limitValuesToBlankNodeID(BlankNodeID id) {
        LOGGER.warn("TODO DetermineNodeType.limitValuesToBlankNodeID() {}", id);
    }

    // FIXME Implement!
    @Override
    public void limitValuesToExpression(Expression expression) {
        LOGGER.warn("TODO DetermineNodeType.limitValuesToExpression() {}", expression);
    }

    // FIXME Implement!
    @Override
    public void limitValuesToPattern(Pattern pattern) {
        LOGGER.warn("TODO DetermineNodeType.limitValuesToPattern() {}", pattern);
    }

    // FIXME Implement!
    @Override
    public void setUsesTranslator(Translator translator) {
        if (translator != Translator.IDENTITY) {
            LOGGER.warn("TODO DetermineNodeType.setUsesTranslator() {}", translator);
        }
    }
}
