package de.fuberlin.wiwiss.d2rq.vocab;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lists all the classes and properties in a schemagen-generated
 * vocabulary class, such as the {@link D2RQ} class.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class VocabularySummarizer {
    private final Class<?> vocabularyJavaClass;
    private final String namespace;
    private final Set<Property> properties;
    private final Set<Resource> classes;

    public VocabularySummarizer(Class<?> vocabularyJavaClass) {
        this.vocabularyJavaClass = Objects.requireNonNull(vocabularyJavaClass);
        namespace = findNamespace();
        properties = findAllProperties();
        classes = findAllClasses();
    }

    /**
     * Gets all {@link Resource Resources} from the standard vocabularies.
     *
     * @return a {@code Set} of {@link Resource}s
     * @see RDF
     * @see XSD
     * @see RDFS
     * @see OWL
     */
    public static Set<Resource> getStandardResources() {
        return getResources(Resource.class, RDF.class, XSD.class, RDFS.class, OWL.class);
    }

    /**
     * Gets all {@link Property Properties} from the standard vocabularies.
     *
     * @return a {@code Set} of {@link Property}s
     * @see RDF
     * @see XSD
     * @see RDFS
     * @see OWL
     */
    public static Set<Property> getStandardProperties() {
        return getResources(Property.class, RDF.class, XSD.class, RDFS.class, OWL.class);
    }

    /**
     * Gets all resources of the type {@link X} from the specified vocabularies.
     *
     * @param type    a {@code Class}-type
     * @param classes Array of {@link Class}es
     * @param <X>     either {@link Resource} or {@link Property}
     * @return a {@code Set} of {@link X}s
     */
    public static <X extends Resource> Set<X> getResources(Class<X> type, Class<?>... classes) {
        return resources(type, classes).collect(Collectors.toSet());
    }

    /**
     * Lists all {@link Resource}-constants from the given vocabularies
     *
     * @param classes Array of {@link Class}es
     * @return {@code Stream} of {@link Resource}
     */
    public static Stream<? extends Resource> resources(Class<?>... classes) {
        return Stream.of(Resource.class, Property.class).flatMap(x -> resources(x, classes));
    }

    /**
     * Lists all resources of the type {@link X} from the specified vocabularies.
     *
     * @param type    a {@code Class}-type
     * @param classes Array of {@link Class}es
     * @param <X>     either {@link Resource} or {@link Property}
     * @return a {@code Stream} of {@link X}s
     */
    public static <X extends Resource> Stream<X> resources(Class<X> type, Class<?>... classes) {
        return Arrays.stream(classes)
                .map(VocabularySummarizer::new)
                .map(x -> x.get(type))
                .flatMap(Collection::stream)
                // jena 3.11.0: XSD contains null (#ENTITIES)
                .filter(Objects::nonNull);
    }

    @SuppressWarnings("unchecked")
    public <X extends Resource> Set<X> get(Class<X> type) {
        if (Property.class.equals(type)) {
            return (Set<X>) getAllProperties();
        }
        if (Resource.class.equals(type)) {
            return (Set<X>) getAllClasses();
        }
        return Collections.emptySet();
    }

    public Set<Property> getAllProperties() {
        return properties;
    }

    public Set<Resource> getAllClasses() {
        return classes;
    }

    public String getNamespace() {
        return namespace;
    }

    private Set<Property> findAllProperties() {
        Set<Property> results = new HashSet<>();
        for (int i = 0; i < vocabularyJavaClass.getFields().length; i++) {
            Field field = vocabularyJavaClass.getFields()[i];
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Property.class.isAssignableFrom(field.getType())) continue;
            try {
                results.add((Property) field.get(null));
            } catch (IllegalAccessException ex) {
                throw new D2RQException(ex);
            }
        }
        return results;
    }

    private Set<Resource> findAllClasses() {
        Set<Resource> results = new HashSet<>();
        for (int i = 0; i < vocabularyJavaClass.getFields().length; i++) {
            Field field = vocabularyJavaClass.getFields()[i];
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!Resource.class.isAssignableFrom(field.getType())) continue;
            if (Property.class.isAssignableFrom(field.getType())) continue;
            try {
                results.add((Resource) field.get(null));
            } catch (IllegalAccessException ex) {
                throw new D2RQException(ex);
            }
        }
        return results;
    }

    private String findNamespace() {
        try {
            Object o = vocabularyJavaClass.getField("NS").get(vocabularyJavaClass);
            if (o instanceof String) {
                return (String) o;
            }
            return null;
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            return null;
        }
    }

    public Collection<Resource> getUndefinedClasses(Model model) {
        Set<Resource> result = new HashSet<>();
        StmtIterator it = model.listStatements(null, RDF.type, (RDFNode) null);
        while (it.hasNext()) {
            Statement stmt = it.nextStatement();
            if (stmt.getObject().isURIResource()
                    && stmt.getResource().getURI().startsWith(namespace)
                    && !classes.contains(stmt.getResource())) {
                result.add(stmt.getResource());
            }
        }
        return result;
    }

    public Collection<Property> getUndefinedProperties(Model model) {
        Set<Property> result = new HashSet<>();
        StmtIterator it = model.listStatements();
        while (it.hasNext()) {
            Statement stmt = it.nextStatement();
            if (stmt.getPredicate().getURI().startsWith(namespace)
                    && !properties.contains(stmt.getPredicate())) {
                result.add(stmt.getPredicate());
            }
        }
        return result;
    }

    public void assertNoUndefinedTerms(Model model, int undefinedPropertyErrorCode, int undefinedClassErrorCode) {
        Collection<Property> unknownProperties = getUndefinedProperties(model);
        if (!unknownProperties.isEmpty()) {
            throw new D2RQException("Unknown property " + PrettyPrinter.toString(
                    unknownProperties.iterator().next()) + ", maybe a typo?",
                    undefinedPropertyErrorCode);
        }
        Collection<Resource> unknownClasses = getUndefinedClasses(model);
        if (!unknownClasses.isEmpty()) {
            throw new D2RQException("Unknown class " + PrettyPrinter.toString(
                    unknownClasses.iterator().next()) + ", maybe a typo?",
                    undefinedClassErrorCode);
        }
    }

}
