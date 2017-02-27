package ru.avicomp.ontapi;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.rdf.model.*;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLProperty;

import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.map.MappingTransform;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * The "filter" to work with {@link Model} which encapsulates D2RQ mapping rules.
 * Using this class you can create new mapping model from an existing one with the specified constrains.
 * <p>
 * Created by @szuev on 24.02.2017.
 */
public class MappingFilter implements MappingTransform.ModelBuilder {
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

    @Override
    public Model build(Mapping mapping) {
        Model m = mapping.getMappingModel();
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
     * creates filtered model base on specified one.
     *
     * @param model {@link Model} with D2RQ mapping rules.
     * @return the new Model which corresponds this filter.
     */
    public Model filter(Model model) {
        MappingFilter _filter = compile(model);
        Model res = ModelFactory.createDefaultModel();
        res.setNsPrefixes(model.getNsPrefixMap());
        @SuppressWarnings("SuspiciousMethodCalls")
        Set<Resource> exclude = model.listStatements(null, D2RQ.clazz, (RDFNode) null)
                .filterDrop(s -> _filter.classes.contains(s.getObject()))
                .andThen(model.listStatements(null, D2RQ.property, (RDFNode) null)
                        .filterDrop(s -> _filter.properties.contains(s.getObject())))
                .mapWith(Statement::getSubject).toSet();
        model.listStatements().filterDrop(s -> exclude.contains(s.getSubject())).forEachRemaining(res::add);
        return res;
    }

    /**
     * Creates new {@link MappingFilter} and compiles it using specified {@link Model} with D2RQ mapping rules inside.
     * This means it complements missing properties and classes by analysing mapping graph:
     * For each property it adds a class which corresponds d2rq:belongsToClassMap predicate;
     * For each class it adds properties which have the class as an object in statement with predicate d2rq:belongsToClassMap
     * and satisfy some other conditions to be a valid owl:DatatypeProperty or owl:ObjectProperty.
     * Unfamiliar properties and classes stay untouched.
     *
     * @param model D2RQ model
     * @return the new filter with completed list of properties and classes.
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
     * Returns all classes from model which relate to the properties in this filter.
     *
     * @param model D2RQ Model
     * @return Stream of classes
     */
    private Stream<Resource> classes(Model model) {
        return properties().map(p -> findClassByProperty(p, model))
                .filter(Objects::nonNull);
    }

    /**
     * Returns all properties (data&object) from model which belong to the classes from this filter.
     *
     * @param model D2RQ Model
     * @return Stream of properties
     */
    private Stream<Resource> properties(Model model) {
        Set<Resource> classMaps = classes().map(c -> model.listResourcesWithProperty(D2RQ.clazz, c))
                .map(Iter::asStream)
                .flatMap(Function.identity())
                .filter(RDFNode::isURIResource)
                .collect(Collectors.toSet());
        return classMaps.stream()
                .map(c -> model.listResourcesWithProperty(D2RQ.belongsToClassMap, c))
                .map(Iter::asStream)
                .flatMap(Function.identity())
                .filter(p -> isValidPropertyBridge(p, classMaps))
                .map(p -> model.listObjectsOfProperty(p, D2RQ.property))
                .map(Iter::asStream)
                .flatMap(Function.identity())
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

    private static Resource findClassByProperty(Resource property, Model model) {
        return Iter.asStream(model.listResourcesWithProperty(D2RQ.property, property))
                .map(s -> model.listObjectsOfProperty(s, D2RQ.belongsToClassMap))
                .map(Iter::asStream)
                .flatMap(Function.identity())
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource)
                .map(r -> model.listObjectsOfProperty(r, D2RQ.clazz))
                .map(Iter::asStream)
                .flatMap(Function.identity())
                .filter(RDFNode::isURIResource)
                .map(RDFNode::asResource)
                .findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return String.format("MappingFilter{properties=%s, classes=%s}", properties, classes);
    }

}
