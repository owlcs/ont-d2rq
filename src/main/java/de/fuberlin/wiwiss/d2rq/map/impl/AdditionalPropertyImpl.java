package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.AdditionalProperty;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;

/**
 * Created by @szz on 02.10.2018.
 */
public class AdditionalPropertyImpl extends MapObjectImpl implements AdditionalProperty {

    public AdditionalPropertyImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public AdditionalProperty setName(String uri) {
        return setURI(D2RQ.propertyName, uri);
    }

    @Override
    public String getName() {
        return findURI(D2RQ.propertyName).orElse(null);
    }

    @Override
    public AdditionalProperty setValue(RDFNode value) {
        return setRDFNode(D2RQ.propertyValue, value);
    }

    @Override
    public RDFNode getValue() {
        return findFirst(D2RQ.propertyValue, Statement::getObject).orElse(null);
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        v.forProperty(D2RQ.propertyName)
                .requireExists(D2RQException.UNSPECIFIED)
                .requireHasNoDuplicates(D2RQException.UNSPECIFIED)
                .requireIsURI(D2RQException.UNSPECIFIED);
        v.forProperty(D2RQ.propertyValue)
                .requireExists(D2RQException.UNSPECIFIED)
                .requireHasNoDuplicates(D2RQException.UNSPECIFIED);
    }
}
