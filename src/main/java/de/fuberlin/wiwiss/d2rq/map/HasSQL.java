package de.fuberlin.wiwiss.d2rq.map;

import java.util.stream.Stream;

/**
 * A technical interface to provide access to different sql settings.
 * Applicable for {@link ClassMap}, {@link DownloadMap} and {@link PropertyBridge}.
 * To handle {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#alias d2rq:alias},
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#condition d2rq:condition} and
 * {@link de.fuberlin.wiwiss.d2rq.vocab.D2RQ#join d2rq:join}.
 * <p>
 * Created by @ssz on 30.09.2018.
 *
 * @param <R> subtype of {@link MapObject}
 */
interface HasSQL<R extends MapObject> {

    /**
     * Adds string literal for the {d2rq:alias} predicate.
     * <p>
     * Aliases take the form {@code Table AS Alias} and are used when a table needs to be joined to itself.
     * The table can be referred to using the alias within the property bridge.
     * See example:
     * {@code map:ParentTopic a d2rq:PropertyBridge;
     * d2rq:belongsToClassMap map:Topic;
     * d2rq:property :parentTopic;
     * d2rq:refersToClassMap map:Topic;
     * d2rq:join "Topics.ParentID => ParentTopics.ID";
     * d2rq:alias "Topics AS ParentTopics";}
     *
     * @param alias String, not {@code null}
     * @return {@link R} to allow cascading calls
     */
    R addAlias(String alias);

    /**
     * Adds string literal for the {d2rq:join} predicate.
     * <p>
     * If the columns used to create the literal value or object are not from the database table(s)
     * that contains the {@link MapObject Map Object}s columns,
     * then the tables have to be joined together using one or more {@code d2rq:join} properties.
     * See example:
     * {@code map:authorName a d2rq:PropertyBridge;
     * d2rq:belongsToClassMap map:Papers;
     * d2rq:property :authorName;
     * d2rq:column "Persons.Name";
     * d2rq:join "Papers.PaperID <= Rel_Person_Paper.PaperID";
     * d2rq:join "Rel_Person_Paper.PersonID => Persons.PerID";
     * d2rq:datatype xsd:string;
     * d2rq:propertyDefinitionLabel "name"@en;
     * d2rq:propertyDefinitionComment "Name of an author."@en;
     * .}
     *
     * @param join String, not {@code null}
     * @return {@link R} to allow cascading calls
     */
    R addJoin(String join);

    /**
     * Adds string literal for the {d2rq:condition} predicate.
     * <p>
     * Specifies an SQL {@code WHERE} condition.
     * An instance of this object will only be generated for database rows that satisfy the condition.
     * Conditions can be used to hide parts of the database from D2RQ,
     * e.g. deny access to data which is older or newer than a certain date.
     *
     * @param condition String, not {@code null}
     * @return {@link R} to allow cascading calls
     * @see <a href='http://d2rq.org/d2rq-language#conditional'>10. Conditional Mappings</a>
     */
    R addCondition(String condition);

    /**
     * Lists all string literals for the {@code d2rq:alias} property.
     *
     * @return Stream of String's
     */
    Stream<String> aliases();

    /**
     * Lists all string literals for the {@code d2rq:join} property.
     *
     * @return Stream of String's
     */
    Stream<String> joins();

    /**
     * Lists all string literals for the {@code d2rq:condition} property.
     *
     * @return Stream of String's
     */
    Stream<String> conditions();
}
