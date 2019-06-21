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
     * @param serveVocabulary boolean
     * @return this instance
     */
    Configuration setServeVocabulary(boolean serveVocabulary);

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
     * @param useAllOptimizations boolean
     * @return this instance
     */
    Configuration setUseAllOptimizations(boolean useAllOptimizations);

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
     * If this option is specified, then the mapping will be automatically supplemented
     * with some additional D2RQ rules so that the generated data (see {@link Mapping#getData()})
     * will also include several reasonable axioms, according to the OWL2 specification requirements.
     * Also it is to handle {@link PropertyBridge#dynamicProperties()} dynamic properties}.
     * <p>
     * If this option is turned off, it still possible to have correct OWL2 schema,
     * depending on the original mapping itself.
     *
     * @return boolean
     * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL 2 Quick Reference Guide</a>
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#controlOWL
     */
    boolean getControlOWL();

    /**
     * Changes {@link #getGenerateNamedIndividuals() generatedNamedIndividuals} setting
     * depending on the specified parameter.
     * No effect in case {@link #getControlOWL() control OWL} is off.
     *
     * @param generateNamedIndividuals boolean
     * @return this instance to allow cascading calls
     */
    Configuration setGenerateNamedIndividuals(boolean generateNamedIndividuals);

    /**
     * Answers {@code true} if each individual that appears in the {@link Mapping#getData() data}
     * must have explicit {@link ru.avicomp.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividual} declaration,
     * that is optional, but desirable.
     * This setting depends on the {@link #getControlOWL()},
     * and does not work if {@link #getControlOWL() control OWL} of off.
     * <p>
     * By default it is off.
     *
     * @return boolean
     * @see de.fuberlin.wiwiss.d2rq.vocab.AVC#generateNamedIndividuals
     */
    boolean getGenerateNamedIndividuals();

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
