package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Literal;

import java.util.stream.Stream;

/**
 * Created by @szz on 02.10.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasProperties<R extends MapObject> {

    R addAdditionalProperty(AdditionalProperty property);

    Stream<AdditionalProperty> additionalProperties();

    R addComment(Literal value);

    Stream<Literal> comments();

    R addLabel(Literal value);

    Stream<Literal> labels();
}
