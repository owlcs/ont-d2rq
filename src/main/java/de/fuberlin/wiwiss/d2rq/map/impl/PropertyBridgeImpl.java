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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

@SuppressWarnings("WeakerAccess")
public class PropertyBridgeImpl extends ResourceMap implements PropertyBridge {
    private ClassMap belongsToClassMap = null;
    private Collection<Resource> properties = new HashSet<>();
    private Collection<String> dynamicPropertyPatterns = new HashSet<>();

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
        assertNotYetDefined(this.belongsToClassMap, D2RQ.belongsToClassMap, D2RQException.PROPERTYBRIDGE_DUPLICATE_BELONGSTOCLASSMAP);
        assertArgumentNotNull(classMap, D2RQ.belongsToClassMap, D2RQException.PROPERTYBRIDGE_INVALID_BELONGSTOCLASSMAP);
        this.belongsToClassMap = classMap;
    }

    @Override
    public String getColumn() {
        return column;
    }

    @Override
    public void setColumn(String column) {
        assertNotYetDefined(this.column, D2RQ.column, D2RQException.PROPERTYBRIDGE_DUPLICATE_COLUMN);
        this.column = column;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        assertNotYetDefined(this.pattern, D2RQ.pattern, D2RQException.PROPERTYBRIDGE_DUPLICATE_PATTERN);
        this.pattern = pattern;
    }

    public String getSQLExpression() {
        return sqlExpression;
    }

    public void setSQLExpression(String sqlExpression) {
        assertNotYetDefined(this.column, D2RQ.sqlExpression, D2RQException.PROPERTYBRIDGE_DUPLICATE_SQL_EXPRESSION);
        this.sqlExpression = sqlExpression;
    }

    public String getUriSQLExpression() {
        return uriSqlExpression;
    }

    @Override
    public String getDatatype() {
        return datatype;
    }

    public void setDatatype(String datatype) {
        assertNotYetDefined(this.datatype, D2RQ.datatype, D2RQException.PROPERTYBRIDGE_DUPLICATE_DATATYPE);
        this.datatype = datatype;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        assertNotYetDefined(this.lang, D2RQ.lang, D2RQException.PROPERTYBRIDGE_DUPLICATE_LANG);
        this.lang = lang;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        assertNotYetDefined(this.limit, D2RQ.limit, D2RQException.PROPERTYBRIDGE_DUPLICATE_LIMIT);
        this.limit = limit;
    }

    public int getLimitInverse() {
        return limitInverse;
    }

    public void setLimitInverse(int limit) {
        assertNotYetDefined(this.limitInverse, D2RQ.limitInverse, D2RQException.PROPERTYBRIDGE_DUPLICATE_LIMITINVERSE);
        this.limitInverse = limit;
    }

    public void setOrder(String column, boolean desc) {
        assertNotYetDefined(this.order, (desc ? D2RQ.orderDesc : D2RQ.orderAsc), D2RQException.PROPERTYBRIDGE_DUPLICATE_ORDER);
        this.order = column;
        this.orderDesc = desc;
    }

    @Override
    public ClassMap getRefersToClassMap() {
        return refersToClassMap;
    }

    @Override
    public void setRefersToClassMap(ClassMap classMap) {
        assertNotYetDefined(this.refersToClassMap, D2RQ.refersToClassMap, D2RQException.PROPERTYBRIDGE_DUPLICATE_REFERSTOCLASSMAP);
        assertArgumentNotNull(classMap, D2RQ.refersToClassMap, D2RQException.PROPERTYBRIDGE_INVALID_REFERSTOCLASSMAP);
        this.refersToClassMap = classMap;
    }

    @Override
    public ClassMap refersToClassMap() {
        return refersToClassMap;
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

    public void addDynamicProperty(String dynamicPropertyPattern) {
        this.dynamicPropertyPatterns.add(dynamicPropertyPattern);
    }

    @Override
    public void validate() throws D2RQException {
        if (this.refersToClassMap != null) {
            if (!this.refersToClassMap.getDatabase().equals(this.belongsToClassMap.getDatabase())) {
                throw new D2RQException(toString() +
                        " links two d2rq:ClassMaps with different d2rq:dataStorages",
                        D2RQException.PROPERTYBRIDGE_CONFLICTING_DATABASES);
            }
            // TODO refersToClassMap cannot be combined w/ value constraints or translation tables
        }
        if (properties.isEmpty() && dynamicPropertyPatterns.isEmpty()) {
            throw new D2RQException(toString() + " needs a d2rq:property or d2rq:dynamicProperty",
                    D2RQException.PROPERTYBRIDGE_MISSING_PREDICATESPEC);
        }
        assertHasPrimarySpec(new Property[]{
                D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.bNodeIdColumns,
                D2RQ.column, D2RQ.pattern, D2RQ.sqlExpression, D2RQ.uriSqlExpression, D2RQ.constantValue,
                D2RQ.refersToClassMap
        });
        if (this.datatype != null && this.lang != null) {
            throw new D2RQException(toString() + " has both d2rq:datatype and d2rq:lang",
                    D2RQException.PROPERTYBRIDGE_LANG_AND_DATATYPE);
        }
        if (this.datatype != null && this.column == null && this.pattern == null
                && this.sqlExpression == null) {
            throw new D2RQException("d2rq:datatype can only be used with d2rq:column, d2rq:pattern " +
                    "or d2rq:sqlExpression at " + this,
                    D2RQException.PROPERTYBRIDGE_NONLITERAL_WITH_DATATYPE);
        }
        if (this.lang != null && this.column == null && this.pattern == null
                && this.sqlExpression == null) {
            throw new D2RQException("d2rq:lang can only be used with d2rq:column, d2rq:pattern " +
                    "or d2rq:sqlExpression at " + this,
                    D2RQException.PROPERTYBRIDGE_NONLITERAL_WITH_LANG);
        }
    }

    @Override
    protected Relation buildRelation() {
        ConnectedDB database = mapping.getConnectedDB((DatabaseImpl) belongsToClassMap.getDatabase());
        RelationBuilder builder = belongsToClassMap.relationBuilder(database);
        builder.addOther(relationBuilder(database));
        if (this.refersToClassMap != null) {
            builder.addAliased(this.refersToClassMap.relationBuilder(database));
        }
        for (String pattern : dynamicPropertyPatterns) {
            builder.addOther(new PropertyMap(pattern).relationBuilder(database));
        }
        if (this.limit != null) {
            builder.setLimit(this.limit);
        }
        if (this.limitInverse != null) {
            builder.setLimitInverse(this.limitInverse);
        }
        if (this.order != null) {
            builder.setOrderSpecs(Collections.singletonList(
                    new OrderSpec(new AttributeExpr(SQL.parseAttribute(this.order)), this.orderDesc)));
        }
        return builder.buildRelation();
    }

    public Collection<TripleRelation> toTripleRelations() {
        this.validate();
        Collection<TripleRelation> results = new ArrayList<>();
        NodeMaker s = this.belongsToClassMap.nodeMaker();
        NodeMaker o = this.nodeMaker();
        for (Resource property : properties) {
            results.add(new TripleRelation(buildRelation(), s, new FixedNodeMaker(property.asNode(), false), o));
        }
        for (String pattern : dynamicPropertyPatterns) {
            results.add(new TripleRelation(buildRelation(), s, new PropertyMap(pattern).nodeMaker(), o));
        }
        return results;
    }

    @Override
    public String toString() {
        return String.format("d2rq:PropertyBridge %s", PrettyPrinter.toString(this.resource));
    }
}