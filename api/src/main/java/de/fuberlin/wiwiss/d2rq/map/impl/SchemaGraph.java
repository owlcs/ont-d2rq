package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.jena.MappingGraph;

/**
 * Created by @ssz on 04.01.2019.
 */
@SuppressWarnings("WeakerAccess")
public abstract class SchemaGraph extends MappingGraph {

    /**
     * Creates an instance.
     *
     * @param impl {@link MappingImpl}, not {@code null}
     */
    protected SchemaGraph(MappingImpl impl) {
        super(impl);
    }

    @Override
    public MappingImpl getMapping() {
        return (MappingImpl) mapping;
    }

    @Override
    public String toString() {
        // do not allow printing of all data content
        return String.format("Schema[%s]", mapping);
    }
}
