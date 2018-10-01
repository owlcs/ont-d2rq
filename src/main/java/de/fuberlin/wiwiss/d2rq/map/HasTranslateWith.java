package de.fuberlin.wiwiss.d2rq.map;

/**
 * Created by @ssz on 01.10.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasTranslateWith<R extends MapObject> {

    R setTranslateWith(TranslationTable table);

    TranslationTable getTranslateWith();
}
