package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.impl.RelationBuilder;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import org.apache.jena.rdf.model.Resource;

import java.util.Collection;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface ClassMap extends MapObject, HasDatabase<ClassMap>, HasURI<ClassMap>, HasSQLAddition<ClassMap> {

    // todo: remove from this interface
    NodeMaker nodeMaker();

    // todo: remove from this interface
    RelationBuilder relationBuilder(ConnectedDB database);

    boolean hasProperties();

    Collection<Resource> getClasses();

    /**
     * Sets {@code d2rq:containsDuplicates} boolean literal.
     * Must be specified if a class map uses information from tables that are not fully normalized.
     * If the {@code d2rq:containsDuplicates} property value is set to {@code true},
     * then D2RQ adds a {@code DISTINCT} clause to all queries using this classMap.
     * {@code false} is the default value, which doesn't have to be explicitly declared.
     * Adding this property to class maps based on normalized database tables degrades query performance,
     * but doesn't affect query results.
     *
     * @param b boolean
     * @return this instance to allow cascading calls
     */
    ClassMap setContainsDuplicates(boolean b);

    /**
     * Answers if the {@code d2rq:containsDuplicates} is set to {@code true}.
     *
     * @return boolean
     * @see #setContainsDuplicates(boolean)
     */
    boolean isContainsDuplicates();

    /**
     * Produces a blank node as {@code d2rq:constantValue}.
     *
     * @return this object to allow cascading calls
     * @see HasURI#setConstantValue(String)
     */
    ClassMap setConstantValue();

    void addPropertyBridge(PropertyBridge p);

    Collection<PropertyBridge> getPropertyBridges();
}
