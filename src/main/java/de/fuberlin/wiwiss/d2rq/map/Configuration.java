package de.fuberlin.wiwiss.d2rq.map;

/**
 * Representation of a {@code d2rq:Configuration} from the mapping file.
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
     * Answers whether to serve inferred and user-supplied vocabulary data ({@code true} by default).
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
     * Answers whether to use bleeding edge optimizations ({@code false} by default).
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
     * Answers whether to use {@code avc:controlOWL} option.
     * By default it is {@code false}.
     * If this option is specified, the generated data (see {@link Mapping#getData()})
     * will also be supplemented with OWL2 declarations and other axioms.
     * For example in OWL2 named individuals must have
     * {@link ru.avicomp.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividual} {@code rdf:type}.
     *
     * @return boolean
     * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL 2 Quick Reference Guide</a>
     */
    boolean getControlOWL();

}
