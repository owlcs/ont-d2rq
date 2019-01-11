package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.rdf.model.Resource;

/**
 * Abstraction for any D2RQ mapping entity.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 * Created by @ssz on 26.09.2018.
 */
public interface MapObject {

    /**
     * Answer the {@code Resource} associated with this map object.
     *
     * @return {@link Resource}, not {@code null}
     */
    Resource asResource();

    /**
     * Returns the mapping model associated with this map-object.
     *
     * @return {@link Mapping}, not null
     */
    Mapping getMapping();

    /**
     * Validates RDF structure of this map object.
     *
     * @throws D2RQException in case the object is not complete and cannot be used to build relations
     */
    void validate() throws D2RQException;

}
