package com.github.owlcs.d2rq;

import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.*;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLProperty;
import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The filter to work with {@link Model} which encapsulates D2RQ mapping rules.
 * Using this class you can create new D2RQ mapping model from an existing one with the specified constrains.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public class MappingFilter {
    private Set<Resource> properties = new HashSet<>();
    private Set<Resource> classes = new HashSet<>();

    public MappingFilter includeProperty(IRI property) {
        properties.add(ResourceFactory.createResource(OntApiException.notNull(property, "Null property IRI.").getIRIString()));
        return this;
    }

    public MappingFilter includeClass(IRI clazz) {
        classes.add(ResourceFactory.createResource(OntApiException.notNull(clazz, "Null class IRI.").getIRIString()));
        return this;
    }

    public Stream<Resource> properties() {
        return properties.stream();
    }

    public Stream<Resource> classes() {
        return classes.stream();
    }

    public boolean isEmpty() {
        return properties.isEmpty() && classes.isEmpty();
    }

    public Model build(Mapping mapping) {
        Model m = mapping.asModel();
        return isEmpty() ? m : filter(m);
    }

    public static MappingFilter create(OWLProperty... properties) {
        MappingFilter res = new MappingFilter();
        for (OWLProperty p : properties) {
            res.includeProperty(p.getIRI());
        }
        return res;
    }

    /**
     * Creates a filtered D2RQ mapping model based on specified one.
     *
     * @param model {@link Model jena model} with D2RQ mapping rules.
     * @return the new D2RQ Mapping Model which corresponds this filter.
     * @throws OntApiException in case there some properties or classes are missed.
     */
    public Model filter(Model model) {
        validate(model);
        MappingFilter f = compile(model);
        Model res = ModelFactory.createDefaultModel();
        res.setNsPrefixes(model.getNsPrefixMap());
        @SuppressWarnings("SuspiciousMethodCalls")
        Set<Resource> exclude = model.listStatements(null, D2RQ.clazz, (RDFNode) null)
                .filterDrop(s -> f.classes.contains(s.getObject()))
                .andThen(model.listStatements(null, D2RQ.property, (RDFNode) null)
                        .filterDrop(s -> f.properties.contains(s.getObject())))
                .andThen(model.listStatements(null, RDF.type, OWL.Ontology))
                .mapWith(Statement::getSubject).toSet();
        model.listStatements().filterDrop(s -> exclude.contains(s.getSubject())).forEachRemaining(res::add);
        return res;
    }

    /**
     * Creates a new {@link MappingFilter} and compiles it using specified {@link Model model} with D2RQ mapping rules inside.
     * This means it complements missing properties and classes by analysing mapping graph:
     * For each property it adds classes which correspond {@code d2rq:belongsToClassMap} and {@code d2rq:refersToClassMap} predicates;
     * For each class it adds properties which have the class as an object in statement with predicate {@code d2rq:belongsToClassMap}
     * and satisfy some other conditions to be a valid {@code owl:DatatypeProperty} or {@code owl:ObjectProperty}.
     * Unfamiliar properties and classes stay untouched.
     *
     * @param model D2RQ Mapping model
     * @return the new filter with completed list of properties and classes.
     * @see D2RQ
     * @see ru.avicomp.ontapi.jena.vocabulary.OWL
     */
    public MappingFilter compile(Model model) {
        Set<Resource> _classes = new HashSet<>(classes);
        Set<Resource> _properties = new HashSet<>(properties);
        classes(model).forEach(_classes::add);
        properties(model).forEach(_properties::add);
        MappingFilter res = new MappingFilter();
        res.properties = _properties;
        res.classes = _classes;
        return res;
    }

    /**
     * Validates that filter parameters are present in the specified model.
     *
     * @param model {@link Model} with D2RQ rules.
     * @throws OntApiException if some parameters are missed.
     */
    public void validate(Model model) {
        Set<Resource> missedProperties = properties().filter(p -> !model.contains(null, D2RQ.property, p)).collect(Collectors.toSet());
        Set<Resource> missedClasses = classes().filter(c -> !model.contains(null, D2RQ.clazz, c)).collect(Collectors.toSet());
        if (missedClasses.isEmpty() && missedProperties.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        if (!missedProperties.isEmpty()) {
            sb.append("properties:").append(missedProperties).append(";");
        }
        if (!missedClasses.isEmpty()) {
            sb.append("classes:").append(missedClasses).append(";");
        }
        throw new OntApiException("Some filter params are absent in model: " + sb);
    }

    /**
     * Returns all classes from model that relate to the properties in this filter.
     *
     * @param model D2RQ Mapping Model
     * @return Stream of classes
     */
    private Stream<Resource> classes(Model model) {
        return properties().map(p -> model.listResourcesWithProperty(D2RQ.property, p))
                .flatMap(Iter::asStream)
                .map(p -> p.listProperties(D2RQ.belongsToClassMap).andThen(p.listProperties(D2RQ.refersToClassMap)))
                .flatMap(Iter::asStream)
                .map(Statement::getObject)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource)
                .map(c -> model.listObjectsOfProperty(c, D2RQ.clazz))
                .flatMap(Iter::asStream)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource);
    }

    /**
     * Returns all properties (data and object) from model which belong to the classes from this filter.
     *
     * @param model D2RQ Mapping Model
     * @return Stream of properties
     */
    private Stream<Resource> properties(Model model) {
        Set<Resource> classMaps = classes().map(c -> model.listResourcesWithProperty(D2RQ.clazz, c))
                .flatMap(Iter::asStream)
                .filter(RDFNode::isURIResource)
                .collect(Collectors.toSet());
        return classMaps.stream()
                .map(c -> model.listResourcesWithProperty(D2RQ.belongsToClassMap, c))
                .flatMap(Iter::asStream)
                .filter(p -> isValidPropertyBridge(p, classMaps))
                .map(p -> model.listObjectsOfProperty(p, D2RQ.property))
                .flatMap(Iter::asStream)
                .filter(Objects::nonNull)
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource);
    }

    /**
     * @param bridge    {@link Resource} candidate
     * @param classMaps the Set of classMaps
     * @return true if this bridge contains Object or Data property.
     */
    private static boolean isValidPropertyBridge(Resource bridge, Set<Resource> classMaps) {
        if (!bridge.hasProperty(RDF.type, D2RQ.PropertyBridge)) // not a bridge at all
            return false;
        if (bridge.hasProperty(D2RQ.column)) // data property
            return true;
        // object property:
        Set<RDFNode> refClassMaps = bridge.listProperties(D2RQ.refersToClassMap).mapWith(Statement::getObject).toSet();
        return !refClassMaps.isEmpty() && classMaps.containsAll(refClassMaps);
    }

    @Override
    public String toString() {
        return String.format("MappingFilter{properties=%s, classes=%s}", properties, classes);
    }

}
