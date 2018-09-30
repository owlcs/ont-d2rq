package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.algebra.AliasMap.Alias;
import de.fuberlin.wiwiss.d2rq.expr.SQLExpression;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker.NodeType;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.*;
import de.fuberlin.wiwiss.d2rq.values.ValueDecorator.ValueConstraint;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.*;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public abstract class ResourceMap extends MapObjectImpl {

    // These can be set on PropertyBridges and ClassMaps
    private String bNodeIdColumns = null;    // comma-separated list
    private String uriColumn = null; // for ClassMap, PropertyBridge and DownloadMap
    private String uriPattern = null; // for ClassMap, PropertyBridge and DownloadMap
    private RDFNode constantValue = null; /// for ClassMap, PropertyBridge and DownloadMap
    private List<String> valueRegexes = new ArrayList<>();
    private List<String> valueContainses = new ArrayList<>();
    private int valueMaxLength = Integer.MAX_VALUE;
    private List<String> joins = new ArrayList<>(); // for ClassMap, PropertyBridge and DownloadMap
    private List<String> conditions = new ArrayList<>(); // for ClassMap, PropertyBridge and DownloadMap
    private List<String> aliases = new ArrayList<>(); // for ClassMap, PropertyBridge and DownloadMap
    private TranslationTable translateWith = null;

    // These can be set only on a PropertyBridge
    protected String column = null;
    protected String pattern = null;
    protected String sqlExpression = null;
    private String uriSqlExpression = null; // also for all 2 other ... hate this monkey code.
    protected String datatype = null;
    protected String lang = null;
    protected ClassMap refersToClassMap = null;

    Collection<Literal> definitionLabels = new ArrayList<>();
    Collection<Literal> definitionComments = new ArrayList<>();

    /**
     * List of D2RQ.AdditionalProperty
     */
    Collection<Resource> additionalDefinitionProperties = new ArrayList<>();

    public ResourceMap(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    public void setBNodeIdColumns(String columns) {
        assertNotYetDefined(getBNodeIdColumns(), D2RQ.bNodeIdColumns, D2RQException.RESOURCEMAP_DUPLICATE_BNODEIDCOLUMNS);
        this.bNodeIdColumns = columns;
    }

    public String getBNodeIdColumns() {
        return bNodeIdColumns;
    }

    public void setURIColumn(String column) {
        assertNotYetDefined(getURIColumn(), D2RQ.uriColumn, D2RQException.RESOURCEMAP_DUPLICATE_URICOLUMN);
        this.uriColumn = column;
    }

    public String getURIColumn() {
        return uriColumn;
    }

    public void setURIPattern(String pattern) {
        assertNotYetDefined(getURIPattern(), D2RQ.uriPattern, D2RQException.RESOURCEMAP_DUPLICATE_URIPATTERN);
        this.uriPattern = pattern;
    }

    public String getURIPattern() {
        return uriPattern;
    }

    public void setUriSQLExpression(String uriSqlExpression) {
        assertNotYetDefined(getURISQLExpression(), D2RQ.uriSqlExpression, D2RQException.PROPERTYBRIDGE_DUPLICATE_URI_SQL_EXPRESSION);
        this.uriSqlExpression = uriSqlExpression;
    }

    public String getURISQLExpression() {
        return uriSqlExpression;
    }

    public void setConstantValue(RDFNode constantValue) {
        assertNotYetDefined(getConstantValue(), D2RQ.constantValue, D2RQException.RESOURCEMAP_DUPLICATE_CONSTANTVALUE);
        this.constantValue = constantValue;
    }

    public RDFNode getConstantValue() {
        return this.constantValue;
    }

    public void addValueRegex(String regex) {
        this.valueRegexes.add(regex);
    }

    public List<String> getValueRegexList() {
        return this.valueRegexes;
    }

    public void addValueContains(String contains) {
        this.valueContainses.add(contains);
    }

    public List<String> getValueContainsList() {
        return valueContainses;
    }

    public void setValueMaxLength(int maxLength) {
        if (getValueMaxLength() != Integer.MAX_VALUE) {
            // always fails
            assertNotYetDefined(this, D2RQ.valueMaxLength,
                    D2RQException.PROPERTYBRIDGE_DUPLICATE_VALUEMAXLENGTH);
        }
        this.valueMaxLength = maxLength;
    }

    public int getValueMaxLength() {
        return valueMaxLength;
    }

    public void setTranslateWith(TranslationTable table) {
        assertNotYetDefined(getTranslateWith(), D2RQ.translateWith,
                D2RQException.RESOURCEMAP_DUPLICATE_TRANSLATEWITH);
        assertArgumentNotNull(table, D2RQ.translateWith, D2RQException.RESOURCEMAP_INVALID_TRANSLATEWITH);
        this.translateWith = table;
    }

    public TranslationTable getTranslateWith() {
        return translateWith;
    }

    public void addJoin(String join) {
        this.joins.add(join);
    }

    public List<String> getJoinList() {
        return joins;
    }

    public void addCondition(String condition) {
        this.conditions.add(condition);
    }

    public List<String> getConditionList() {
        return conditions;
    }

    public void addAlias(String alias) {
        this.aliases.add(alias);
    }

    public List<String> getAliasList() {
        return aliases;
    }

    public String getColumn() {
        return column;
    }

    public String getPattern() {
        return pattern;
    }

    public String getSQLExpression() {
        return sqlExpression;
    }

    public String getDatatype() {
        return datatype;
    }

    public String getLang() {
        return lang;
    }

    public boolean getContainsDuplicates() {
        return getBoolean(D2RQ.containsDuplicates, false);
    }

    protected Collection<Alias> aliases() {
        Set<Alias> parsedAliases = new HashSet<>();
        for (String alias : getAliasList()) {
            parsedAliases.add(SQL.parseAlias(alias));
        }
        return parsedAliases;
    }

    public ClassMap getRefersToClassMap() {
        return refersToClassMap;
    }

    public RelationBuilder relationBuilder(ConnectedDB cd) {
        return relationBuilder(cd, getContainsDuplicates());
    }

    public RelationBuilder relationBuilder(ConnectedDB cd, boolean containsDuplicates) {
        RelationBuilder res = new RelationBuilder(cd);
        for (Join join : SQL.parseJoins(getJoinList())) {
            res.addJoinCondition(join);
        }
        for (String condition : getConditionList()) {
            res.addCondition(condition);
        }
        res.addAliases(aliases());
        for (ProjectionSpec projection : nodeMaker().projectionSpecs()) {
            res.addProjection(projection);
        }
        if (!containsDuplicates) {
            res.setIsUnique(true);
        }
        return res;
    }

    public Relation getRelation() {
        return buildRelation();
    }

    protected abstract Relation buildRelation();

    public NodeMaker nodeMaker() {
        boolean isUnique = !getContainsDuplicates();
        RDFNode constantValue = getConstantValue();
        if (constantValue != null) {
            return new FixedNodeMaker(constantValue.asNode(), isUnique);
        }
        ClassMap refersToClassMap = getRefersToClassMap();
        if (refersToClassMap == null) {
            return buildNodeMaker(wrapValueSource(buildValueSourceBase()), isUnique);
        }
        return ((ClassMapImpl) refersToClassMap).buildAliasedNodeMaker(new AliasMap(aliases()), isUnique);
    }

    public NodeMaker buildAliasedNodeMaker(AliasMap aliases, boolean unique) {
        ValueMaker values = wrapValueSource(buildValueSourceBase()).renameAttributes(aliases);
        return buildNodeMaker(values, unique);
    }

    protected ValueMaker buildValueSourceBase() {
        String bNodeIdColumns = getBNodeIdColumns();
        if (bNodeIdColumns != null) {
            return new BlankNodeID(PrettyPrinter.toString(this.asResource()), parseColumnList(bNodeIdColumns));
        }
        String uriColumn = getURIColumn();
        if (uriColumn != null) {
            return new Column(SQL.parseAttribute(uriColumn));
        }
        String uriPattern = getURIPattern();
        if (uriPattern != null) {
            return new PropertyMap(uriPattern).buildValueSourceBase();
        }
        String column = getColumn();
        if (column != null) {
            return new Column(SQL.parseAttribute(column));
        }
        String pattern = getPattern();
        if (pattern != null) {
            return new Pattern(pattern);
        }
        String sqlExpression = getSQLExpression();
        if (sqlExpression != null) {
            return new SQLExpressionValueMaker(SQLExpression.create(sqlExpression));
        }
        String uriSqlExpression = getURISQLExpression();
        if (uriSqlExpression != null) {
            return new SQLExpressionValueMaker(SQLExpression.create(uriSqlExpression));
        }
        throw new D2RQException(this + " needs a column/pattern/bNodeID specification");
    }

    public ValueMaker wrapValueSource(ValueMaker values) {
        List<ValueConstraint> constraints = new ArrayList<>();
        int valueMaxLength = getValueMaxLength();
        if (valueMaxLength != Integer.MAX_VALUE) {
            constraints.add(ValueDecorator.maxLengthConstraint(valueMaxLength));
        }
        for (String contains : getValueContainsList()) {
            constraints.add(ValueDecorator.containsConstraint(contains));
        }
        for (String regex : getValueRegexList()) {
            constraints.add(ValueDecorator.regexConstraint(regex));
        }
        TranslationTable translateWith = getTranslateWith();
        if (translateWith == null) {
            if (constraints.isEmpty()) {
                return values;
            }
            return new ValueDecorator(values, constraints);
        }
        return new ValueDecorator(values, constraints, translateWith.translator());
    }

    protected NodeMaker buildNodeMaker(ValueMaker values, boolean isUnique) {
        return new TypedNodeMaker(nodeType(), values, isUnique);
    }

    protected NodeType nodeType() {
        if (getBNodeIdColumns() != null) {
            return TypedNodeMaker.BLANK;
        }
        if (getURIColumn() != null || getURIPattern() != null) {
            return TypedNodeMaker.URI;
        }
        if (getURISQLExpression() != null) {
            return TypedNodeMaker.URI;
        }
        // literals
        if (getColumn() == null && getPattern() == null && getSQLExpression() == null) {
            throw new D2RQException(this + " needs a column/pattern/bNodeID/sqlExpression/uriSqlExpression specification");
        }
        String datatype = getDatatype();
        String lang = getLang();
        if (datatype != null && lang != null) {
            throw new D2RQException(this + " has both d2rq:lang and d2rq:datatype");
        }
        if (datatype != null) {
            return TypedNodeMaker.typedLiteral(buildDatatype(datatype));
        }
        if (lang != null) {
            return TypedNodeMaker.languageLiteral(lang);
        }
        return TypedNodeMaker.PLAIN_LITERAL;
    }

    private RDFDatatype buildDatatype(String datatypeURI) {
        return TypeMapper.getInstance().getSafeTypeByName(datatypeURI);
    }

    private List<Attribute> parseColumnList(String commaSeperated) {
        List<Attribute> result = new ArrayList<>();
        for (String attr : commaSeperated.split(",")) {
            result.add(SQL.parseAttribute(attr));
        }
        return result;
    }

    protected void assertHasPrimarySpec(Property[] allowedSpecs) {
        List<Property> definedSpecs = new ArrayList<>();
        for (Property allowedProperty : allowedSpecs) {
            if (hasPrimarySpec(allowedProperty)) {
                definedSpecs.add(allowedProperty);
            }
        }
        if (definedSpecs.isEmpty()) {
            StringBuilder error = new StringBuilder(toString());
            error.append(" needs one of ");
            for (int i = 0; i < allowedSpecs.length; i++) {
                if (i > 0) {
                    error.append(", ");
                }
                error.append(PrettyPrinter.toString(allowedSpecs[i]));
            }
            throw new D2RQException(error.toString(), D2RQException.RESOURCEMAP_MISSING_PRIMARYSPEC);
        }
        if (definedSpecs.size() > 1) {
            throw new D2RQException(toString() + " can't have both " +
                    PrettyPrinter.toString(definedSpecs.get(0)) +
                    " and " +
                    PrettyPrinter.toString(definedSpecs.get(1)));
        }
    }

    private boolean hasPrimarySpec(Property property) {
        if (property.equals(D2RQ.bNodeIdColumns)) return getBNodeIdColumns() != null;
        if (property.equals(D2RQ.uriColumn)) return getURIColumn() != null;
        if (property.equals(D2RQ.uriPattern)) return getURIPattern() != null;
        if (property.equals(D2RQ.column)) return getColumn() != null;
        if (property.equals(D2RQ.pattern)) return getPattern() != null;
        if (property.equals(D2RQ.sqlExpression)) return getSQLExpression() != null;
        if (property.equals(D2RQ.uriSqlExpression)) return getURISQLExpression() != null;
        if (property.equals(D2RQ.refersToClassMap)) return getRefersToClassMap() != null;
        if (property.equals(D2RQ.constantValue)) return getConstantValue() != null;
        throw new D2RQException("No primary spec: " + property);
    }

    public Collection<Literal> getDefinitionLabels() {
        return definitionLabels;
    }

    public Collection<Literal> getDefinitionComments() {
        return definitionComments;
    }

    public Collection<Resource> getAdditionalDefinitionProperties() {
        return additionalDefinitionProperties;
    }

    public void addDefinitionLabel(Literal definitionLabel) {
        definitionLabels.add(definitionLabel);
    }

    public void addDefinitionComment(Literal definitionComment) {
        definitionComments.add(definitionComment);
    }

    public void addDefinitionProperty(Resource additionalProperty) {
        additionalDefinitionProperties.add(additionalProperty);
    }
}