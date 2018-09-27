package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Resource;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface MapObject {

    Resource asResource();

    Mapping getMapping();

    void validate();
}
