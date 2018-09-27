package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import org.apache.jena.rdf.model.Resource;


/**
 * Representation of a d2rq:Configuration from the mapping file.
 *
 * @author Christian Becker &lt;http://beckr.org#chris&gt;
 */
public class ConfigurationImpl extends MapObjectImpl implements Configuration {
    private boolean serveVocabulary = true;
    private boolean useAllOptimizations = false;

    public ConfigurationImpl() {
        this(null);
    }

    public ConfigurationImpl(Resource resource) {
        super(resource);
    }

    @Override
    public boolean getServeVocabulary() {
        return this.serveVocabulary;
    }

    @Override
    public void setServeVocabulary(boolean serveVocabulary) {
        this.serveVocabulary = serveVocabulary;
    }

    @Override
    public boolean getUseAllOptimizations() {
        return this.useAllOptimizations;
    }

    @Override
    public void setUseAllOptimizations(boolean useAllOptimizations) {
        this.useAllOptimizations = useAllOptimizations;
    }

    @Override
    public String toString() {
        return "d2rq:Configuration " + super.toString();
    }

    @Override
    public void validate() throws D2RQException {
        /* All settings are optional */
    }
}