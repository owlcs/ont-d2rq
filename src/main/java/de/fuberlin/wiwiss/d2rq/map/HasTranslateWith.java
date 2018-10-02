package de.fuberlin.wiwiss.d2rq.map;

/**
 * Created by @ssz on 01.10.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasTranslateWith<R extends MapObject> {

    /**
     * Assigns a {@code d2rq:TranslationTable} to the property bridge on the predicate {@code d2rq:translateWith}.
     * Values from the {@code d2rq:column} or {@code d2rq:pattern} will be translated by the given table.
     * See example:
     * {@code map:ColorBridge a d2rq:PropertyBridge;
     * d2rq:belongsToClassMap map:ShinyObjectMap;
     * d2rq:property :color;
     * d2rq:uriColumn "ShinyObject.Color";
     * d2rq:translateWith map:ColorTable;
     * .
     * map:ColorTable a d2rq:TranslationTable;
     * d2rq:translation [ d2rq:databaseValue "R"; d2rq:rdfValue :red; ];
     * d2rq:translation [ d2rq:databaseValue "G"; d2rq:rdfValue :green; ];
     * d2rq:translation [ d2rq:databaseValue "B"; d2rq:rdfValue :blue; ];
     * .}
     *
     * @param table {@link TranslationTable}, not {@code null}
     * @return {@link R} to allow cascading calls
     */
    R setTranslateWith(TranslationTable table);

    /**
     * Finds the {@link TranslationTable} that is attached to this mapping resource on predicate {@code d2rq:translateWith}.
     *
     * @return {@link TranslationTable} or {@code null}
     */
    TranslationTable getTranslateWith();
}
