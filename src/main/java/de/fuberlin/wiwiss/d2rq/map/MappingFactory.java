package de.fuberlin.wiwiss.d2rq.map;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.FileManager;

import java.util.Objects;

/**
 * Helper-factory to create/load {@link Mapping}.
 * <p>
 * Created by szuev on 22.02.2017.
 */
public class MappingFactory {
    protected static Inner instance = (m, b) -> new MapParser(Objects.requireNonNull(m, "Null mapping model."), b).parse();

    public static Inner getFactory() {
        return instance;
    }

    public static Inner setFactory(Inner newInstance) {
        Objects.requireNonNull(newInstance, "Null factory");
        Inner res = MappingFactory.instance;
        MappingFactory.instance = newInstance;
        return res;
    }

    /**
     * Creates an empty mapping.
     *
     * @return {@link Mapping}
     */
    public static Mapping createEmpty() {
        return instance.create(ModelFactory.createDefaultModel(), null);
    }

    /**
     * Creates a mapping based on specified model.
     *
     * @param mapModel {@link Model} the mapping model contained D2RQ rules.
     * @param baseURI  the URL to fix relative URIs inside model. Optional.
     * @return {@link Mapping}
     */
    public static Mapping create(Model mapModel, String baseURI) {
        return instance.create(mapModel, baseURI);
    }

    /**
     * creates a mapping.
     *
     * @param mapModel {@link Model} the mapping model contained D2RQ rules.
     * @return {@link Mapping}
     */
    public static Mapping create(Model mapModel) {
        return create(mapModel, null);
    }

    /**
     * Creates a non-RDF database based model. The model is created
     * from a D2RQ map that may be in "RDF/XML", "N-TRIPLES" or "TURTLE" format.
     * Initially it was a constructor inside {@code de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ}.
     *
     * @param mapURL              URL of the D2RQ map to be used for this model
     * @param serializationFormat the format of the map, or <tt>null</tt> for guessing based on the file extension
     * @param baseURIForData      Base URI for turning relative URI patterns into absolute URIs; if <tt>null</tt>, then D2RQ will pick a base URI
     * @return {@link Mapping}
     */
    public static Mapping load(String mapURL, String serializationFormat, String baseURIForData) {
        Model model = FileManager.get().loadModel(mapURL, serializationFormat);
        String base = baseURIForData == null ? mapURL + "#" : baseURIForData;
        return create(model, base);
    }

    /**
     * Creates a non-RDF database based model.
     * The model is created from a D2RQ map that will be loaded from the given URL.
     * Its serialization format will be guessed from the file extension and defaults to RDF/XML.
     * Initially it was a constructor inside {@code de.fuberlin.wiwiss.d2rq.jena.ModelD2RQ}.
     *
     * @param mapURL URL of the D2RQ map to be used for this model
     * @return {@link Mapping}
     */
    public static Mapping load(String mapURL) {
        return create(FileManager.get().loadModel(mapURL), mapURL + "#");
    }

    public interface Inner {
        Mapping create(Model model, String base);
    }
}
