package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.jena.GraphD2RQ;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;

import java.util.Collection;
import java.util.stream.Stream;

/**
 * TODO: introduced as part of bulky refactoring. everything can be changed.
 * Created by @ssz on 25.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#database'>The D2RQ Mapping Language</a>
 */
public interface Mapping extends AutoCloseable {

    PrefixMapping getPrefixMapping();

    Model getMappingModel();

    Model getVocabularyModel();

    Model getDataModel();

    GraphD2RQ getDataGraph();

    Configuration configuration();

    Database createDatabase(Resource r);

    Collection<Database> databases();

    void addDatabase(Database database);

    Database database(Resource name);

    Stream<ClassMap> classMaps();

    void addClassMap(ClassMap classMap);

    Collection<Resource> classMapResources();

    ClassMap classMap(Resource name);

    TranslationTable translationTable(Resource name);

    Collection<Resource> downloadMapResources();

    DownloadMap downloadMap(Resource name);

    Collection<TripleRelation> compiledPropertyBridges();

    void validate();

    void connect();

    void close();
}
