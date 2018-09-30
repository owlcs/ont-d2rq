package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.PropertyBridge;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class ClassMapImpl extends ResourceMap implements ClassMap {

    private List<Resource> classes = new ArrayList<>();
    private List<PropertyBridge> propertyBridges = new ArrayList<>();
    private Collection<TripleRelation> compiledPropertyBridges = null;

    public ClassMapImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public Collection<Resource> getClasses() {
        return classes;
    }

    @Override
    public ClassMapImpl setDatabase(Database database) {
        DatabaseImpl res = mapping.asDatabase(database.asResource()).copy(database);
        setRDFNode(D2RQ.dataStorage, res.asResource());
        return this;
    }

    @Override
    public DatabaseImpl getDatabase() {
        List<Resource> r = resource.listProperties(D2RQ.dataStorage).mapWith(Statement::getResource).toList();
        return r.size() == 1 ? mapping.asDatabase(r.get(0)) : null;
    }

    @Override
    public ClassMapImpl setURIPattern(String pattern) {
        return (ClassMapImpl) super.setURIPattern(pattern);
    }

    @Override
    public ClassMapImpl setURIColumn(String column) {
        return (ClassMapImpl) super.setURIColumn(column);
    }

    @Override
    public ClassMapImpl setConstantValue(String uri) {
        return (ClassMapImpl) super.setConstantValue(uri);
    }

    @Override
    public ClassMapImpl setConstantValue() {
        return (ClassMapImpl) super.setConstantValue(mapping.asModel().createResource());
    }

    @Override
    public ClassMapImpl setUriSQLExpression(String uriSqlExpression) {
        return (ClassMapImpl) super.setUriSQLExpression(uriSqlExpression);
    }

    @Override
    public ClassMapImpl addJoin(String join) {
        return (ClassMapImpl) super.addJoin(join);
    }

    @Override
    public ClassMapImpl addCondition(String condition) {
        return (ClassMapImpl) super.addCondition(condition);
    }

    @Override
    public ClassMapImpl addAlias(String alias) {
        return (ClassMapImpl) super.addAlias(alias);
    }

    public void addClass(Resource clazz) {
        this.classes.add(clazz);
    }

    @Override
    public void addPropertyBridge(PropertyBridge bridge) {
        this.propertyBridges.add(bridge);
    }

    @Override
    public List<PropertyBridge> getPropertyBridges() {
        return this.propertyBridges;
    }

    @Override
    public ClassMapImpl setContainsDuplicates(boolean b) {
        return setBoolean(D2RQ.containsDuplicates, b);
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        v.forProperty(D2RQ.dataStorage)
                .requireExists(D2RQException.CLASSMAP_NO_DATABASE)
                .requireHasNoDuplicates(D2RQException.CLASSMAP_DUPLICATE_DATABASE)
                .requireIsResource(D2RQException.CLASSMAP_INVALID_DATABASE);

        commonValidateURI();
        assertHasPrimarySpec(D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.uriSqlExpression, D2RQ.bNodeIdColumns, D2RQ.constantValue);
        RDFNode constantValue = getConstantValue();
        if (constantValue != null && constantValue.isLiteral()) {
            throw new D2RQException("d2rq:constantValue for class map " + toString() + " must be a URI or blank node",
                    D2RQException.CLASSMAP_INVALID_CONSTANTVALUE);
        }
        PropertyMap.validate(this);
        for (PropertyBridge bridge : getPropertyBridges()) {
            bridge.validate();
        }
        // TODO
    }

    @Override
    public boolean hasProperties() {
        return !getClasses().isEmpty() || !getPropertyBridges().isEmpty();
    }

    public Collection<TripleRelation> compiledPropertyBridges() {
        if (this.compiledPropertyBridges == null) {
            compile();
        }
        return this.compiledPropertyBridges;
    }

    private void compile() {
        this.compiledPropertyBridges = new ArrayList<>();
        for (PropertyBridge bridge : getPropertyBridges()) {
            this.compiledPropertyBridges.addAll(((PropertyBridgeImpl) bridge).toTripleRelations());
        }
        for (Resource clazz : getClasses()) {
            PropertyBridgeImpl bridge = mapping.asPropertyBridge(mapping.asModel().createResource());
            bridge.setBelongsToClassMap(this);
            bridge.addProperty(RDF.type);
            bridge.setConstantValue(clazz);
            this.compiledPropertyBridges.addAll(bridge.toTripleRelations());
        }
    }

    @Override
    protected Relation buildRelation() {
        return this.relationBuilder(mapping.getConnectedDB(getDatabase())).buildRelation();
    }

    @Override
    public String toString() {
        return "d2rq:ClassMap " + PrettyPrinter.toString(this.resource);
    }


}
