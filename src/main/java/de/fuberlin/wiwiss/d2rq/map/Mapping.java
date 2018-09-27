package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * TODO: introduced as part of bulky refactoring. everything can be changed.
 * Created by @ssz on 25.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#database'>The D2RQ Mapping Language</a>
 */
public interface Mapping extends AutoCloseable {

    // todo: hide from the interface
    PrefixMapping getPrefixMapping();

    Model asModel();

    Graph getSchema();

    Graph getData();

    Configuration getConfiguration();

    // todo: should accept String, not Resource
    Database createDatabase(Resource r);

    Stream<Database> listDatabases();

    // todo: change or remove
    void addDatabase(Database database);

    // todo: should accept String, not Resource
    ClassMap createClassMap(Resource r);

    Stream<ClassMap> listClassMaps();

    // todo: change or remove
    void addClassMap(ClassMap classMap);

    // todo: should accept String, not Resource
    PropertyBridge createPropertyBridge(Resource r);

    // todo: should accept String, not Resource
    TranslationTable createTranslationTable(Resource r);

    Stream<TranslationTable> listTranslationTables();

    // todo: should accept String, not Resource
    DownloadMap createDownloadMap(Resource r);

    Stream<DownloadMap> listDownloadMaps();

    // todo: hide from the interface
    Collection<TripleRelation> compiledPropertyBridges();

    void validate();

    // todo: hide from the interface
    void connect();

    void close();

    default Database findDatabase(Resource name) {
        return listDatabases().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default ClassMap findClassMap(Resource name) {
        return listClassMaps().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default DownloadMap findDownloadMap(Resource name) {
        return listDownloadMaps().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default TranslationTable findTranslationTable(Resource name) {
        return listTranslationTables().filter(c -> Objects.equals(c.asResource(), name)).findFirst().orElse(null);
    }

    default Model getDataModel() {
        return ModelFactory.createModelForGraph(getData());
    }

    default Model getVocabularyModel() {
        return ModelFactory.createModelForGraph(getSchema());
    }
}
