package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;

/**
 * Represents {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#AdditionalProperty d2rq:AdditionalProperty}.
 * Although {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#additionalProperty d2rq:additionalProperty} is deprecated
 * (see <a href='http://d2rq.org/d2rq-language#additionalproperty_deprecated'>12.2 d2rq:additionalProperty</a>),
 * it is still used with
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#additionalClassDefinitionProperty d2rq:additionalClassDefinitionProperty} and
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#additionalPropertyDefinitionProperty d2rq:additionalPropertyDefinitionProperty}.
 * <p>
 * Created by @szz on 02.10.2018.
 *
 * @see <a href='http://d2rq.org/d2rq-language#additionalproperty'>9.2 AdditionalProperty</a>
 */
public interface AdditionalProperty extends MapObject {

    /**
     * Sets the given uri as an object in a statement with the {@code d2rq:propertyName} predicate.
     *
     * @param uri String not {@code null}
     * @return this instance
     */
    AdditionalProperty setName(String uri);

    /**
     * Returns an uri attached to the graph on the {@code d2rq:propertyName} predicate.
     *
     * @return {@link Property} or {@code null}
     */
    Property getName();

    /**
     * Sets {@code d2rq:propertyValue} property value, which can be any rdf-node.
     *
     * @param value {@link RDFNode} not {@code null}
     * @return this instance
     */
    AdditionalProperty setValue(RDFNode value);

    /**
     * Gets {@code d2rq:propertyValue} property value.
     *
     * @return {@link RDFNode} or {@code null}
     */
    RDFNode getValue();
}
