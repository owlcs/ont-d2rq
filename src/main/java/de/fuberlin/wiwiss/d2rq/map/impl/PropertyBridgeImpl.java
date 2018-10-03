package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.expr.AttributeExpr;
import de.fuberlin.wiwiss.d2rq.map.AdditionalProperty;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.PropertyBridge;
import de.fuberlin.wiwiss.d2rq.map.TranslationTable;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

import java.util.*;

@SuppressWarnings("WeakerAccess")
public class PropertyBridgeImpl extends ResourceMap implements PropertyBridge {
    private ClassMap belongsToClassMap = null;
    private Set<Resource> properties = new HashSet<>();
    private Set<String> dynamicPropertyPatterns = new HashSet<>();

    private Integer limit = null;
    private Integer limitInverse = null;
    private String order = null;
    private Boolean orderDesc = null;

    public PropertyBridgeImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public PropertyBridgeImpl setURIPattern(String pattern) {
        return (PropertyBridgeImpl) super.setURIPattern(pattern);
    }

    @Override
    public PropertyBridgeImpl addAdditionalProperty(AdditionalProperty property) {
        return (PropertyBridgeImpl) super.addAdditionalProperty(property);
    }

    @Override
    public PropertyBridgeImpl addComment(Literal value) {
        return (PropertyBridgeImpl) super.addComment(value);
    }

    @Override
    public PropertyBridgeImpl addLabel(Literal value) {
        return (PropertyBridgeImpl) super.addLabel(value);
    }

    @Override
    Property definitionLabelPredicate() {
        return D2RQ.propertyDefinitionLabel;
    }

    @Override
    Property definitionCommentPredicate() {
        return D2RQ.propertyDefinitionComment;
    }

    @Override
    Property additionalPropertyPredicate() {
        return D2RQ.additionalPropertyDefinitionProperty;
    }

    @Override
    public PropertyBridgeImpl setURIColumn(String pattern) {
        return (PropertyBridgeImpl) super.setURIColumn(pattern);
    }

    @Override
    public PropertyBridgeImpl setConstantValue(String uri) {
        return (PropertyBridgeImpl) super.setConstantValue(uri);
    }

    @Override
    public PropertyBridgeImpl setConstantValue(Literal literal) {
        return (PropertyBridgeImpl) super.setConstantValue(literal);
    }

    @Override
    public PropertyBridgeImpl setConstantValue() {
        return (PropertyBridgeImpl) super.setConstantValue(mapping.asModel().createResource());
    }

    @Override
    public PropertyBridgeImpl setUriSQLExpression(String uriSqlExpression) {
        return (PropertyBridgeImpl) super.setUriSQLExpression(uriSqlExpression);
    }

    @Override
    public PropertyBridgeImpl addJoin(String join) {
        return (PropertyBridgeImpl) super.addJoin(join);
    }

    @Override
    public PropertyBridgeImpl addCondition(String condition) {
        return (PropertyBridgeImpl) super.addCondition(condition);
    }

    @Override
    public PropertyBridgeImpl addAlias(String alias) {
        return (PropertyBridgeImpl) super.addAlias(alias);
    }

    @Override
    public PropertyBridgeImpl setBNodeIdColumns(String columns) {
        return (PropertyBridgeImpl) super.setBNodeIdColumns(columns);
    }

    @Override
    public PropertyBridgeImpl addValueRegex(String regex) {
        return (PropertyBridgeImpl) super.addValueRegex(regex);
    }

    @Override
    public PropertyBridgeImpl addValueContains(String contains) {
        return (PropertyBridgeImpl) super.addValueContains(contains);
    }

    @Override
    public PropertyBridgeImpl setValueMaxLength(int maxLength) {
        return (PropertyBridgeImpl) super.setValueMaxLength(maxLength);
    }

    @Override
    public PropertyBridgeImpl setTranslateWith(TranslationTable table) {
        return (PropertyBridgeImpl) super.setTranslateWith(table);
    }

    @Override
    public Collection<Resource> properties() {
        return this.properties;
    }

    @Override
    public ClassMap getBelongsToClassMap() {
        return belongsToClassMap;
    }

    @Override
    public void setBelongsToClassMap(ClassMap classMap) {
        assertNotYetDefined(getBelongsToClassMap(), D2RQ.belongsToClassMap, D2RQException.PROPERTYBRIDGE_DUPLICATE_BELONGSTOCLASSMAP);
        assertArgumentNotNull(classMap, D2RQ.belongsToClassMap, D2RQException.PROPERTYBRIDGE_INVALID_BELONGSTOCLASSMAP);
        this.belongsToClassMap = classMap;
    }

    @Override
    public PropertyBridgeImpl setColumn(String column) {
        return setLiteral(D2RQ.column, column);
    }

    @Override
    public PropertyBridgeImpl setPattern(String pattern) {
        return setLiteral(D2RQ.pattern, pattern);
    }

    @Override
    public PropertyBridgeImpl setSQLExpression(String sqlExpression) {
        return setLiteral(D2RQ.sqlExpression, sqlExpression);
    }

    @Override
    public PropertyBridgeImpl setDatatype(String uri) {
        return setURI(D2RQ.datatype, uri);
    }

    @Override
    public PropertyBridge setLang(String lang) {
        return setLiteral(D2RQ.lang, lang);
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        assertNotYetDefined(getLimit(), D2RQ.limit, D2RQException.PROPERTYBRIDGE_DUPLICATE_LIMIT);
        this.limit = limit;
    }

    public Integer getLimitInverse() {
        return limitInverse;
    }

    public void setLimitInverse(int limit) {
        assertNotYetDefined(getLimitInverse(), D2RQ.limitInverse, D2RQException.PROPERTYBRIDGE_DUPLICATE_LIMITINVERSE);
        this.limitInverse = limit;
    }

    public void setOrder(String column, boolean desc) {
        assertNotYetDefined(getOrderColumn(), (desc ? D2RQ.orderDesc : D2RQ.orderAsc), D2RQException.PROPERTYBRIDGE_DUPLICATE_ORDER);
        this.order = column;
        this.orderDesc = desc;
    }

    public String getOrderColumn() {
        return order;
    }

    public Boolean getOrderDesc() {
        return orderDesc;
    }

    @Override
    public void setRefersToClassMap(ClassMap classMap) {
        assertNotYetDefined(getRefersToClassMap(), D2RQ.refersToClassMap, D2RQException.PROPERTYBRIDGE_DUPLICATE_REFERSTOCLASSMAP);
        assertArgumentNotNull(classMap, D2RQ.refersToClassMap, D2RQException.PROPERTYBRIDGE_INVALID_REFERSTOCLASSMAP);
        this.refersToClassMap = classMap;
    }

    /**
     * Note: the return value is never {@code false} unless it is specified in the graph for this property bridge.
     * I am not sure any property bridge may contain this parameter,
     * but this is an original logic, that I dare not touch yet.
     *
     * @return boolean, usually {@code false}
     */
    @Override
    public boolean isContainsDuplicates() {
        return getBoolean(D2RQ.containsDuplicates, true);
    }

    @Override
    public void addProperty(Resource property) {
        this.properties.add(property);
    }

    public Set<Resource> getProperties() {
        return properties;
    }

    public void addDynamicProperty(String dynamicPropertyPattern) {
        this.dynamicPropertyPatterns.add(dynamicPropertyPattern);
    }

    public Set<String> getDynamicProperties() {
        return this.dynamicPropertyPatterns;
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        Validator.ForProperty column = v.forProperty(D2RQ.column);
        if (column.exists()) {
            column.requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_COLUMN)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        Validator.ForProperty pattern = v.forProperty(D2RQ.pattern);
        if (pattern.exists()) {
            pattern.requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_PATTERN)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        Validator.ForProperty sqlExpression = v.forProperty(D2RQ.sqlExpression);
        if (sqlExpression.exists()) {
            sqlExpression.requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_SQL_EXPRESSION)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        Validator.ForProperty datatype = v.forProperty(D2RQ.datatype);
        if (datatype.exists()) {
            datatype.requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_DATATYPE)
                    .requireIsURI(D2RQException.UNSPECIFIED);
        }
        Validator.ForProperty lang = v.forProperty(D2RQ.lang);
        if (lang.exists()) {
            lang.requireHasNoDuplicates(D2RQException.PROPERTYBRIDGE_DUPLICATE_LANG)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        ClassMap refersToClassMap = getRefersToClassMap();
        ClassMap belongsToClassMap = getBelongsToClassMap();
        if (refersToClassMap != null) {
            if (!refersToClassMap.getDatabase().equals(belongsToClassMap.getDatabase())) {
                throw new D2RQException(toString() +
                        " links two d2rq:ClassMaps with different d2rq:dataStorages",
                        D2RQException.PROPERTYBRIDGE_CONFLICTING_DATABASES);
            }
            // TODO refersToClassMap cannot be combined w/ value constraints or translation tables
        }
        if (getProperties().isEmpty() && getDynamicProperties().isEmpty()) {
            throw new D2RQException(toString() + " needs a d2rq:property or d2rq:dynamicProperty",
                    D2RQException.PROPERTYBRIDGE_MISSING_PREDICATESPEC);
        }
        commonValidateURI();
        commonValidateSQLAdditions();
        commonValidateUnclassifiedAdditions();
        assertHasPrimarySpec(D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.bNodeIdColumns,
                D2RQ.column, D2RQ.pattern, D2RQ.sqlExpression, D2RQ.uriSqlExpression, D2RQ.constantValue,
                D2RQ.refersToClassMap);

        if (datatype.exists() && lang.exists()) {
            throw new D2RQException(toString() + " has both d2rq:datatype and d2rq:lang",
                    D2RQException.PROPERTYBRIDGE_LANG_AND_DATATYPE);
        }
        if (!column.exists() && !pattern.exists() && !sqlExpression.exists()) {
            if (datatype.exists()) {
                throw new D2RQException("d2rq:datatype can only be used with d2rq:column, d2rq:pattern " +
                        "or d2rq:sqlExpression at " + this,
                        D2RQException.PROPERTYBRIDGE_NONLITERAL_WITH_DATATYPE);
            }
            if (lang.exists()) {
                throw new D2RQException("d2rq:lang can only be used with d2rq:column, d2rq:pattern " +
                        "or d2rq:sqlExpression at " + this,
                        D2RQException.PROPERTYBRIDGE_NONLITERAL_WITH_LANG);
            }
        }
    }

    @Override
    protected Relation buildRelation() {
        ClassMapImpl belongsToClassMap = (ClassMapImpl) getBelongsToClassMap();
        ClassMapImpl refersToClassMap = (ClassMapImpl) getRefersToClassMap();
        ConnectedDB database = mapping.getConnectedDB(belongsToClassMap.getDatabase());
        RelationBuilder builder = belongsToClassMap.relationBuilder(database);
        builder.addOther(relationBuilder(database));
        if (refersToClassMap != null) {
            builder.addAliased(refersToClassMap.relationBuilder(database));
        }
        for (String pattern : getDynamicProperties()) {
            builder.addOther(new PropertyMap(pattern).relationBuilder(database));
        }
        Integer limit = getLimit();
        if (limit != null) {
            builder.setLimit(limit);
        }
        Integer limitInverse = getLimitInverse();
        if (limitInverse != null) {
            builder.setLimitInverse(limitInverse);
        }
        String order = getOrderColumn();
        if (order != null) {
            builder.setOrderSpecs(Collections.singletonList(new OrderSpec(new AttributeExpr(SQL.parseAttribute(order)), getOrderDesc())));
        }
        return builder.buildRelation();
    }

    public Collection<TripleRelation> toTripleRelations() {
        this.validate();
        Collection<TripleRelation> results = new ArrayList<>();
        NodeMaker s = getBelongsToClassMap().nodeMaker();
        NodeMaker o = this.nodeMaker();
        for (Resource property : getProperties()) {
            results.add(new TripleRelation(buildRelation(), s, new FixedNodeMaker(property.asNode(), false), o));
        }
        for (String pattern : getDynamicProperties()) {
            results.add(new TripleRelation(buildRelation(), s, new PropertyMap(pattern).nodeMaker(), o));
        }
        return results;
    }

    @Override
    public String toString() {
        return String.format("d2rq:PropertyBridge %s", PrettyPrinter.toString(this.resource));
    }
}