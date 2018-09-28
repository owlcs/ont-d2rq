package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.impl.RelationBuilder;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.Collection;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface ClassMap extends MapObject, HasDatabase<ClassMap> {

    // todo: remove from interface
    NodeMaker nodeMaker();

    // todo: remove from interface
    RelationBuilder relationBuilder(ConnectedDB database);

    boolean hasProperties();

    Collection<Resource> getClasses();

    void setURIPattern(String pattern);

    void setConstantValue(RDFNode constantValue);

    void setContainsDuplicates(boolean b);

    void addAlias(String alias);

    void addJoin(String join);

    void addCondition(String cond);

    void addPropertyBridge(PropertyBridge p);

    Collection<PropertyBridge> propertyBridges();
}
