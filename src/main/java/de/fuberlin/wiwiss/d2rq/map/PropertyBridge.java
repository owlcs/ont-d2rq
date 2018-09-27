package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.Collection;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface PropertyBridge extends MapObject {

    ClassMap refersToClassMap();

    void addProperty(Resource r);

    Collection<Resource> properties();

    ClassMap getRefersToClassMap();

    void setRefersToClassMap(ClassMap c);

    ClassMap getBelongsToClassMap();

    void setBelongsToClassMap(ClassMap c);

    String getColumn();

    void setColumn(String s);

    void addCondition(String s);

    void setConstantValue(RDFNode v);

    void addAlias(String s);

    void addJoin(String j);

    String getDatatype();

}
