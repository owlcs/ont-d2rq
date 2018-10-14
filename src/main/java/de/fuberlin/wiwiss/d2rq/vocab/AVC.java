package de.fuberlin.wiwiss.d2rq.vocab;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * An addition to the {@link D2RQ standard D2RQ} vocabulary.
 * Created by @ssz on 13.10.2018.
 */
@SuppressWarnings("WeakerAccess")
public class AVC {
    public static final String BASE_URI = "http://avc.ru/d2rq";
    public static final String NS = BASE_URI + "#";

    public static String getURI() {
        return BASE_URI + "/";
    }

    /**
     * To indicate that something in the auto-generated mapping is suspicious.
     */
    public static final Property warning = property("warning");

    /**
     * An addition configuration property to control building OWL2 Schema from a D2RQ Mapping.
     * If turn on, then mapping should contains also instructions
     * ({@link de.fuberlin.wiwiss.d2rq.map.ClassMap}s and {@link de.fuberlin.wiwiss.d2rq.map.PropertyBridge}s)
     * to generate some OWL declaration statements
     * (e.g. for {@link ru.avicomp.ontapi.jena.vocabulary.OWL#NamedIndividual owl:NamedIndividual}).
     *
     * @see D2RQ#Configuration
     * @see D2RQ#useAllOptimizations
     * @see D2RQ#serveVocabulary
     */
    public static final Property controlOWL = property("controlOWL");

    /**
     * Makes a fresh uri-resource with local name {@code Unknown}-{@code suffix},
     * where {@code suffix} is a specified string.
     * Used to generate missed classes if the corresponding {@link de.fuberlin.wiwiss.d2rq.map.ClassMap}
     * is an anonymous resource.
     *
     * @param suffix String, not {@code null}
     * @return {@link Resource}
     */
    public static Resource UnknownClass(String suffix) {
        return resource("Unknown-" + suffix);
    }

    protected static Resource resource(String local) {
        return ResourceFactory.createResource(NS + local);
    }

    protected static Property property(String local) {
        return ResourceFactory.createProperty(NS + local);
    }

}
