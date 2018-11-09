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
@SuppressWarnings("WeakerAccess")
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
    public boolean getControlOWL() {
        return getBoolean(AVC.controlOWL, false);
    }

    @Override
    public Configuration setControlOWL(boolean controlOWL) {
        return setBoolean(AVC.controlOWL, controlOWL);
    }

    @Override
    public boolean getWithCache() {
        return getBoolean(AVC.withCache, false);
    }

    @Override
    public ConfigurationImpl setWithCache(boolean useCache) {
        return setBoolean(AVC.withCache, useCache);
    }

    @Override
    public int getCacheMaxSize() {
        return getInteger(AVC.cacheMaxSize, 10_000);
    }

    @Override
    public ConfigurationImpl setCacheMaxSize(int size) {
        return setInteger(AVC.cacheMaxSize, size);
    }

    @Override
    public long getCacheLengthLimit() {
        return findFirst(AVC.cacheLengthLimit, s -> s.getLiteral().getLong()).orElse(30_000_000L);
    }

    @Override
    public ConfigurationImpl setCacheLengthLimit(long length) {
        return setInteger(AVC.cacheLengthLimit, String.valueOf(length));
    }

    @Override
    public String toString() {
        return "d2rq:Configuration " + super.toString();
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        Stream.of(D2RQ.serveVocabulary, D2RQ.useAllOptimizations, AVC.controlOWL, AVC.withCache)
                .map(v::forProperty)
                .filter(Validator.ForProperty::exists)
                .forEach(p -> p.requireHasNoDuplicates(D2RQException.UNSPECIFIED)
                        .requireIsBooleanLiteral(D2RQException.UNSPECIFIED));
        Stream.of(AVC.cacheLengthLimit, AVC.cacheMaxSize)
                .map(v::forProperty)
                .filter(Validator.ForProperty::exists)
                .forEach(p -> p.requireHasNoDuplicates(D2RQException.UNSPECIFIED)
                        .requireIsIntegerLiteral(D2RQException.UNSPECIFIED));
    }

}