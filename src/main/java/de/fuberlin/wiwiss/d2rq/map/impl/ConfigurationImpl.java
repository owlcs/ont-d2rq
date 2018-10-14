package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.Configuration;
import de.fuberlin.wiwiss.d2rq.vocab.AVC;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Resource;

import java.util.stream.Stream;


/**
 * Representation of a d2rq:Configuration from the mapping file.
 *
 * @author Christian Becker &lt;http://beckr.org#chris&gt;
 */
public class ConfigurationImpl extends MapObjectImpl implements Configuration {

    public ConfigurationImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public boolean getServeVocabulary() {
        return getBoolean(D2RQ.serveVocabulary, true);
    }

    @Override
    public ConfigurationImpl setServeVocabulary(boolean serveVocabulary) {
        return setBoolean(D2RQ.serveVocabulary, serveVocabulary);
    }

    @Override
    public boolean getUseAllOptimizations() {
        return getBoolean(D2RQ.useAllOptimizations, false);
    }

    @Override
    public ConfigurationImpl setUseAllOptimizations(boolean useAllOptimizations) {
        return setBoolean(D2RQ.useAllOptimizations, useAllOptimizations);
    }

    @Override
    public Configuration setControlOWL(boolean controlOWL) {
        return setBoolean(AVC.controlOWL, controlOWL);
    }

    @Override
    public boolean getControlOWL() {
        return getBoolean(AVC.controlOWL, false);
    }

    @Override
    public String toString() {
        return "d2rq:Configuration " + super.toString();
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        Stream.of(D2RQ.serveVocabulary, D2RQ.useAllOptimizations)
                .map(v::forProperty)
                .filter(Validator.ForProperty::exists)
                .forEach(p -> p.requireHasNoDuplicates(D2RQException.UNSPECIFIED)
                        .requireIsBooleanLiteral(D2RQException.UNSPECIFIED));
    }
}