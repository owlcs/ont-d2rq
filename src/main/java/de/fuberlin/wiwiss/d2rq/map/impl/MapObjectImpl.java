package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.MapObject;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

/**
 * Abstract base class for classes that represent things in
 * the mapping file.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public abstract class MapObjectImpl implements MapObject {
    protected final Resource resource;
    protected final MappingImpl mapping;

    public MapObjectImpl(Resource resource, MappingImpl mapping) {
        this.resource = resource;
        this.mapping = mapping;
    }

    @Override
    public Resource asResource() {
        return this.resource;
    }

    @Override
    public MappingImpl getMapping() {
        return mapping;
    }

    @Override
    public abstract void validate() throws D2RQException;

    @Override
    public String toString() {
        return PrettyPrinter.toString(this.resource);
    }

    protected void assertNotYetDefined(Object object, Property property, int errorCode) {
        if (object == null) {
            return;
        }
        throw new D2RQException("Duplicate " + PrettyPrinter.toString(property) + " for " + this, errorCode);
    }

    protected void assertHasBeenDefined(Object object, Property property, int errorCode) {
        if (object != null) {
            return;
        }
        throw new D2RQException("Missing " + PrettyPrinter.toString(property) + " for " + this, errorCode);
    }

    protected void assertArgumentNotNull(Object object, Property property, int errorCode) {
        if (object != null) {
            return;
        }
        throw new D2RQException("Object for " + PrettyPrinter.toString(property) + " not found at " + this, errorCode);
    }
}
