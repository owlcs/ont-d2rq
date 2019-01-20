package de.fuberlin.wiwiss.d2rq.map;

/**
 * Representation of a {@code d2rq:Configuration} from the mapping file.
 *
 * @author Christian Becker &lt;http://beckr.org#chris&gt;
 * Created by @ssz on 26.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#configuration'>4. Global configuration of the mapping engine (d2rq:Configuration)</a>
 */
public interface Configuration extends MapObject {

    /**
     * Sets {@code d2rq:serveVocabulary} boolean value.
     *
     * @param b boolean
     * @return this instance
     */
    Configuration setServeVocabulary(boolean b);

    /**
     * Answers whether to serve inferred and user-supplied vocabulary data.
     * <b>It is {@code true} by default</b>.
     * This option is automatically set when using D2R Server's --fast command-line option.
     *
     * @return boolean
     */
    boolean getServeVocabulary();

    /**
     * Sets {@code d2rq:useAllOptimizations} boolean value.
     *
     * @param b boolean
     * @return this instance
     */
    Configuration setUseAllOptimizations(boolean b);

    /**
     * Answers whether to use bleeding edge optimizations.
     * <b>It is {@code false} by default</b>.
     *
     * @return boolean
     */
    boolean getUseAllOptimizations();

    /**
     * Sets {@link de.fuberlin.wiwiss.d2rq.vocab.AVC#controlOWL avc:controlOWL} boolean value.
     *
     * @param controlOWL boolean
     * @return this instance
     */
    Configuration setControlOWL(boolean controlOWL);

    /**
     * Answers whether to use {@code avc:controlOWL} settings option.
     * <b>It is {@code false} by default</b>.
     * <p>
     * If this option is specified, then the generated data (see {@link Mapping#getData()})
     * will also be supplemented with OWL2 declarations and other axioms, according to specification requirements.
     * For example, in OWL2 any named individuals must have
     * {@link ru.avicomp.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividual} declaration ({@code rdf:type}).
     * <p>
     * If this option is turned off, it still possible to have correct OWL2 view.
     * In order to achieve this need to use {@link ru.avicomp.ontapi.jena.model.OntGraphModel} model view,
     * built with the {@link ru.avicomp.d2rq.conf.D2RQModelConfig#D2RQ_PERSONALITY} inside.
     *
     * @return boolean
     * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL 2 Quick Reference Guide</a>
     */
    boolean getControlOWL();

    /**
     * Sets {@link de.fuberlin.wiwiss.d2rq.vocab.AVC#withCache avc:withCache}.
     *
     * @param useCache boolean to turn on/off cache settings
     * @return this instance
     */
    Configuration setWithCache(boolean useCache);

    /**
     * Answers whether to use the cache for the {@link Mapping#getData() D2RQ Data Graph}.
     * <b>It is {@code false} by default</b>.
     * Note: currently it is an experimental option.
     *
     * @return boolean
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#withCache
     */
    boolean getWithCache();

    /**
     * Sets the cache max size.
     *
     * @param size a positive int
     * @return this instance
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#cacheMaxSize
     */
    Configuration setCacheMaxSize(int size);

    /**
     * Gets the cache max size.
     * <b>The default value if {@code 10000}</b>
     *
     * @return int, either an encoded size or the given by default
     */
    int getCacheMaxSize();

    /**
     * Sets the cache length limit.
     *
     * @param length a positive long
     * @return this instance
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#cacheLengthLimit
     */
    Configuration setCacheLengthLimit(long length);

    /**
     * Gets the cache length limit.
     * <b>The default value if {@code 30000000}</b>
     *
     * @return long, either an encoded limit or the given by default
     */
    long getCacheLengthLimit();

}
