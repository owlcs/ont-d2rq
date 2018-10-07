package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.map.MapObject;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Abstract base class for classes that represent things in
 * the mapping file.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public abstract class MapObjectImpl implements MapObject {
    protected final Resource resource;
    protected final MappingImpl mapping;

    public MapObjectImpl(Resource resource, MappingImpl mapping) throws NullPointerException {
        this.mapping = Objects.requireNonNull(mapping, "Null mapping");
        this.resource = Objects.requireNonNull(resource, "Null resource")
                .inModel(Objects.requireNonNull(mapping.asModel(), "No model"));
    }

    @Override
    public Resource asResource() {
        return this.resource;
    }

    @Override
    public MappingImpl getMapping() {
        return mapping;
    }

    public Model getModel() {
        return resource.getModel();
    }

    protected <X extends MapObject> X setURI(Property property, String uri) throws NullPointerException {
        Resource res = getModel().createResource(Objects.requireNonNull(uri, "Null uri"));
        return setRDFNode(property, res);
    }

    protected <X extends MapObject> X addLiteral(Property property, String literal) throws NullPointerException {
        Literal res = getModel().createLiteral(Objects.requireNonNull(literal, "Null literal"));
        return addRDFNode(property, res);
    }

    @SuppressWarnings("unchecked")
    protected <X extends MapObject> X setNullable(Property property, String literal) throws NullPointerException {
        resource.removeAll(Objects.requireNonNull(property, "Null property"));
        if (literal == null) return (X) this;
        return addRDFNode(property, getModel().createLiteral(literal));
    }

    protected <X extends MapObject> X setLiteral(Property property, String literal) throws NullPointerException {
        Literal res = getModel().createLiteral(Objects.requireNonNull(literal, "Null literal"));
        return setRDFNode(property, res);
    }

    protected <X extends MapObject> X setLiteral(Property property, int literal) {
        Literal res = getModel().createLiteral(String.valueOf(literal), XSD.integer.getURI());
        return setRDFNode(property, res);
    }

    protected <X extends MapObject> X setBoolean(Property property, boolean value) throws NullPointerException {
        return setRDFNode(property, value ? Models.TRUE : Models.FALSE);
    }

    protected <X extends MapObject> X setRDFNode(Property property, RDFNode node) throws NullPointerException {
        if (resource.hasProperty(Objects.requireNonNull(property, "Null property")))
            resource.removeAll(property);
        return addRDFNode(property, node);
    }

    @SuppressWarnings("unchecked")
    protected <X extends MapObject> X addRDFNode(Property property, RDFNode node) throws NullPointerException {
        resource.addProperty(Objects.requireNonNull(property, "Null property"), Objects.requireNonNull(node, "Null value"));
        return (X) this;
    }

    protected boolean getBoolean(Property property,
                                 boolean defaultValue) throws NullPointerException, LiteralRequiredException {
        return findFirst(property, s -> s.getLiteral().getBoolean()).orElse(defaultValue);
    }

    protected Integer getInteger(Property property, Integer defaultValue) {
        return findFirst(property, s -> s.getLiteral().getInt()).orElse(defaultValue);
    }

    protected ExtendedIterator<Literal> listLiterals(Property property) {
        return listStatements(property).mapWith(Statement::getLiteral);
    }

    protected ExtendedIterator<String> listStrings(Property property) throws NullPointerException, LiteralRequiredException {
        return listStatements(property).mapWith(Statement::getString);
    }

    protected Optional<String> findURI(Property property) throws NullPointerException, ResourceRequiredException {
        return findFirst(property, s -> s.getResource().getURI());
    }

    protected String getString(Property property) {
        return findString(property).orElse(null);
    }

    protected Optional<String> findString(Property property) throws NullPointerException, LiteralRequiredException {
        return findFirst(property, Statement::getString);
    }

    protected <X> Optional<X> findFirst(Property property,
                                        Function<Statement, X> extract) throws NullPointerException {
        ExtendedIterator<Statement> res = listStatements(property);
        try {
            return !res.hasNext() ? Optional.empty() : Optional.of(res.next()).map(extract);
        } finally {
            res.close();
        }
    }

    protected ExtendedIterator<Statement> listStatements(Property predicate) {
        return resource.listProperties(Objects.requireNonNull(predicate, "Null predicate"));
    }

    @Override
    public String toString() {
        return PrettyPrinter.toString(this.resource);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapObjectImpl mapObject = (MapObjectImpl) o;
        return Objects.equals(resource, mapObject.resource);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(resource);
    }

    /**
     * Copies the content of the specified {@link MapObject MapObject} to this instance.
     *
     * @param other {@link MapObject}, not {@code null}
     * @param <X>   subtype of {@link MapObject}
     * @return this instance to allow cascading calls
     * @throws NullPointerException if null argument
     * @throws HasNoModelException  if either this or the given objects has no model
     */
    @SuppressWarnings("unchecked")
    protected <X extends MapObject> X copy(MapObject other) throws NullPointerException {
        Model b = Objects.requireNonNull(other).asResource().getModel();
        Model a = getModel();
        if (Objects.equals(a.getGraph(), b.getGraph())) {
            return (X) this;
        }
        Resource r = other.asResource();
        // copy whole content from one model to another:
        Models.getAssociatedStatements(r).forEach(s -> {
            if (r.equals(s.getSubject())) { // top statement:
                resource.addProperty(s.getPredicate(), s.getObject());
            } else { // nested:
                a.add(s);
            }
        });
        return (X) this;
    }


}
