package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.map.impl.MappingImpl;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.JDBC;
import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sys.JenaSystem;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A factory to create and load {@link Mapping D2RQ Mapping}s.
 * <p>
 * Created by szuev on 22.02.2017.
 */
@SuppressWarnings("WeakerAccess")
public class MappingFactory {

    static {
        JenaSystem.init();
    }

    public static final String VOCAB_PREFIX = "vocab";
    public static final String MAP_PREFIX = "map";
    public static final String D2RQ_PREFIX = "d2rq";
    public static final String JDBC_PREFIX = "jdbc";
    private static final PrefixMapping COMMON = PrefixMapping.Factory.create()
            .setNsPrefix("rdf", RDF.getURI())
            .setNsPrefix("rdfs", RDFS.getURI())
            .setNsPrefix("xsd", XSD.getURI()).lock();
    public static final PrefixMapping SCHEMA = PrefixMapping.Factory.create()
            .setNsPrefixes(COMMON)
            .setNsPrefix("owl", OWL.getURI()).lock();
    public static final PrefixMapping MAPPING = PrefixMapping.Factory.create()
            .setNsPrefixes(COMMON)
            .setNsPrefix(D2RQ_PREFIX, D2RQ.getURI())
            .setNsPrefix(JDBC_PREFIX, JDBC.getURI()).lock();

    /**
     * Creates a fresh mapping.
     *
     * @return {@link Mapping}
     */
    public static Mapping create() {
        return wrap(createModel().setNsPrefixes(MAPPING));
    }

    /**
     * Creates a mapping from the given model.
     * The difference between the {@link #wrap(Model)} method and this one is that
     * the first method does not change the model, whereas this method performs some operations on the model,
     * for example it fixes legacy D2RQ instructions.
     *
     * @param model {@link Model} the mapping model contained D2RQ rules.
     * @return {@link Mapping}
     */
    public static Mapping create(Model model) {
        return create(model, null);
    }

    /**
     * Loads a mapping from the specified location.
     *
     * @param location URL of the D2RQ map to be used for this model
     * @return {@link Mapping}
     */
    public static Mapping load(String location) {
        return load(location, null, location + "#");
    }

    /**
     * Loads a mapping from the specified location.
     *
     * @param location URL of the D2RQ mapping file to be used for this model
     * @param format   the format of the map, or {@code null} for guessing based on the file extension
     * @param baseURI  Base URI for turning relative URI patterns into absolute URIs; if {@code null},
     *                 then D2RQ will pick a base URI
     * @return {@link Mapping}
     */
    public static Mapping load(String location, String format, String baseURI) {
        Model model = loadModel(location, format);
        return create(model, baseURI == null ? location + "#" : baseURI);
    }

    private static Model loadModel(String location, String format) {
        Lang lang = guessLang(location, format);
        Model res = createModel();
        try (InputStream in = open(location)) {
            RDFDataMgr.read(res, in, lang);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read <" + location + ">", e);
        }
        return res;
    }

    /**
     * Guesses the {@link Lang RDF Language}.
     *
     * @param location String, the path to the RDF document
     * @param format   String, syntax short form or mime type
     * @return {@link Lang} or {@code null}
     */
    private static Lang guessLang(String location, String format) {
        Lang res = RDFLanguages.nameToLang(format);
        if (res != null) {
            return res;
        }
        return RDFLanguages.filenameToLang(location);
    }

    /**
     * Opens an {@link InputStream} for the specified document path.
     *
     * @param location String, location
     * @return {@link InputStream}
     * @throws IllegalArgumentException in case of wrong {@code location} (not URL or file path)
     * @throws UncheckedIOException     if unable to open the document
     */
    private static InputStream open(String location) throws IllegalArgumentException, UncheckedIOException {
        URI uri;
        try {
            uri = new URI(location);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Can't get URI from <" + location + ">", e);
        }
        if (!uri.isAbsolute() || "file".equals(uri.getScheme())) {
            Path file = parseLocation(uri);
            if (file == null) {
                throw new IllegalArgumentException("Unable to determine path from <" + location + ">");
            }
            try {
                return Files.newInputStream(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Can't open <" + location + ">", e);
            }
        }
        try {
            return uri.toURL().openStream();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Can't get URL from <" + location + ">", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't open <" + location + ">", e);
        }
    }

    /**
     * Creates a {@link Path} from {@link URI}.
     *
     * @param uri {@link URI}
     * @return {@link Path}
     * @throws IllegalArgumentException can't make Path from URI
     */
    private static Path parseLocation(URI uri) throws IllegalArgumentException {
        if (!uri.isAbsolute()) {
            URL url = MappingFactory.class.getResource(uri.toString());
            if (url != null && !url.getFile().isEmpty()) {
                try {
                    return Paths.get(url.toURI());
                } catch (URISyntaxException e) {
                    // ignore
                }
            } else {
                return Paths.get(uri.toString());
            }
        }
        return uri.isOpaque() ? Paths.get(uri.getSchemeSpecificPart()) : Paths.get(uri);
    }

    /**
     * Answers a fresh standard {@link Model Jena Model} with the default personalities and in-memory graph.
     *
     * @return {@link Model}
     */
    private static Model createModel() {
        return ModelFactory.createDefaultModel();
    }

    /**
     * Creates a mapping that is backed by the specified model.
     * Performs also several preliminary actions
     * such as fixing legacy D2RQ rdf entries, inserting base uri to every {@code d2rq:uriPattern}, etc.
     *
     * @param model   {@link Model} the mapping model contained D2RQ rules.
     * @param baseURI the URL to fix relative URIs inside model. Optional.
     * @return {@link Mapping}
     */
    public static Mapping create(Model model, String baseURI) {
        MapParser.validate(model);
        baseURI = MapParser.absolutizeURI(baseURI);
        MapParser.insertBase(model, baseURI);
        MapParser.fixLegacy(model);
        return wrap(model);
    }

    /**
     * Wraps the given model as a {@link Mapping}.
     *
     * @param model {@link Model}, not {@code null}
     * @return {@link Mapping}
     */
    public static Mapping wrap(Model model) {
        return wrap(model.getGraph());
    }

    /**
     * Creates a {@link Mapping} for the specified {@link Graph}.
     *
     * @param graph {@link Graph}, not {@code null}
     * @return {@link Mapping}
     */
    public static Mapping wrap(Graph graph) {
        return new MappingImpl(graph);
    }

}
