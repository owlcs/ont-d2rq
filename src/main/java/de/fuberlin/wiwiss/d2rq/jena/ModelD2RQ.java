package de.fuberlin.wiwiss.d2rq.jena;

import org.apache.jena.enhanced.BuiltinPersonalities;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.impl.ModelCom;

/**
 * <p>A D2RQ read-only Jena model backed by a D2RQ-mapped non-RDF database.</p>
 * <p>
 * <p>D2RQ is a declarative mapping language for describing mappings between ontologies and relational data models.
 * More information about D2RQ is found at: http://www4.wiwiss.fu-berlin.de/bizer/d2rq/</p>
 * <p>
 * <p>This class is a thin wrapper around a {@link GraphD2RQ} and provides only
 * convenience constructors.</p>
 *
 * @author Chris Bizer chris@bizer.de
 * @author Richard Cyganiak (richard@cyganiak.de)
 * @see de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ
 */
public class ModelD2RQ extends ModelCom implements Model {

    public ModelD2RQ(GraphD2RQ graph) {
        super(graph, BuiltinPersonalities.model);
    }

    /**
     * @return The underlying {@link GraphD2RQ}
     */
    @Override
    public GraphD2RQ getGraph() {
        return (GraphD2RQ) super.getGraph();
    }

}