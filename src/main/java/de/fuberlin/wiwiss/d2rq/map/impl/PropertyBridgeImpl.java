package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.OrderSpec;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.expr.AttributeExpr;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.PropertyBridge;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
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
    public void setColumn(String column) {
        assertNotYetDefined(getColumn(), D2RQ.column, D2RQException.PROPERTYBRIDGE_DUPLICATE_COLUMN);
        this.column = column;
    }

    public void setPattern(String pattern) {
        assertNotYetDefined(getPattern(), D2RQ.pattern, D2RQException.PROPERTYBRIDGE_DUPLICATE_PATTERN);
        this.pattern = pattern;
    }

    public void setSQLExpression(String sqlExpression) {
        assertNotYetDefined(getSQLExpression(), D2RQ.sqlExpression, D2RQException.PROPERTYBRIDGE_DUPLICATE_SQL_EXPRESSION);
        this.sqlExpression = sqlExpression;
    }

    public void setDatatype(String datatype) {
        assertNotYetDefined(getDatatype(), D2RQ.datatype, D2RQException.PROPERTYBRIDGE_DUPLICATE_DATATYPE);
        this.datatype = datatype;
    }

    public void setLang(String lang) {
        assertNotYetDefined(getLang(), D2RQ.lang, D2RQException.PROPERTYBRIDGE_DUPLICATE_LANG);
        this.lang = lang;
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
     * Note: the return value is always {@code false} unless it is specified in the graph for this property bridge.
     * I am not sure any property bridge may contain this parameter,
     * but this is an original logic, that i dare not touch yet.
     *
     * @return boolean, usually {@code false}
     */
    @Override
    public boolean getContainsDuplicates() {
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
        assertHasPrimarySpec(new Property[]{
                D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.bNodeIdColumns,
                D2RQ.column, D2RQ.pattern, D2RQ.sqlExpression, D2RQ.uriSqlExpression, D2RQ.constantValue,
                D2RQ.refersToClassMap
        });
        String datatype = getDatatype();
        String lang = getLang();
        if (datatype != null && lang != null) {
            throw new D2RQException(toString() + " has both d2rq:datatype and d2rq:lang",
                    D2RQException.PROPERTYBRIDGE_LANG_AND_DATATYPE);
        }

        if (getColumn() == null && getPattern() == null && getSQLExpression() == null) {
            if (datatype != null) {
                throw new D2RQException("d2rq:datatype can only be used with d2rq:column, d2rq:pattern " +
                        "or d2rq:sqlExpression at " + this,
                        D2RQException.PROPERTYBRIDGE_NONLITERAL_WITH_DATATYPE);
            }
            if (lang != null) {
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