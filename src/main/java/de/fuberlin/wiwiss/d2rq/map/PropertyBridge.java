package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Resource;

import java.util.Collection;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface PropertyBridge extends MapObject, HasURI<PropertyBridge>, HasSQLAddition<PropertyBridge> {

    void addProperty(Resource r);

    Collection<Resource> properties();

    ClassMap getRefersToClassMap();

    void setRefersToClassMap(ClassMap c);

    ClassMap getBelongsToClassMap();

    void setBelongsToClassMap(ClassMap c);

    String getColumn();

    void setColumn(String s);

    String getDatatype();

    /**
     * Sets the given literal as {@code d2rq:constantValue}.
     *
     * @param literal {@link Literal}, not {@code null}
     * @return this object to allow cascading calls
     * @see HasURI#setConstantValue(String)
     */
    PropertyBridge setConstantValue(Literal literal);

    /**
     * Produces a blank node as {@code d2rq:constantValue}.
     *
     * @return this object to allow cascading calls
     * @see HasURI#setConstantValue(String)
     */
    PropertyBridge setConstantValue();

}
