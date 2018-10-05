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

}
