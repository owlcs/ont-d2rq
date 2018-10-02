package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.MapObject;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.iterator.ExtendedIterator;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.List;
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

    public MapObjectImpl(Resource resource, MappingImpl mapping) {
        this.resource = resource; // todo: check not null
        this.mapping = mapping;
    }

    @Override
    public Resource asResource() {
        return this.resource;
    }

    @Override
    public MappingImpl getMapping() {
        return mapping;
    }

    @Override
    public abstract void validate() throws D2RQException;

    @Override
    public String toString() {
        return PrettyPrinter.toString(this.resource);
    }

    protected void assertNotYetDefined(Object object, Property property, int errorCode) {
        if (object == null) {
            return;
        }
        throw new D2RQException("Duplicate " + PrettyPrinter.toString(property) + " for " + this, errorCode);
    }

    protected void assertHasBeenDefined(Object object, Property property, int errorCode) {
        if (object != null) {
            return;
        }
        throw new D2RQException("Missing " + PrettyPrinter.toString(property) + " for " + this, errorCode);
    }

    protected void assertArgumentNotNull(Object object, Property property, int errorCode) {
        if (object != null) {
            return;
        }
        throw new D2RQException("Object for " + PrettyPrinter.toString(property) + " not found at " + this, errorCode);
    }

    protected <X extends MapObject> X addURI(Property property, String uri) throws NullPointerException, HasNoModelException {
        Resource res = mustHaveModel().createResource(Objects.requireNonNull(uri, "Null uri"));
        return addRDFNode(property, res);
    }

    protected <X extends MapObject> X setURI(Property property, String uri) throws NullPointerException, HasNoModelException {
        Resource res = mustHaveModel().createResource(Objects.requireNonNull(uri, "Null uri"));
        return setRDFNode(property, res);
    }

    protected <X extends MapObject> X addLiteral(Property property, String literal) throws NullPointerException, HasNoModelException {
        Literal res = mustHaveModel().createLiteral(Objects.requireNonNull(literal, "Null literal"));
        return addRDFNode(property, res);
    }

    @SuppressWarnings("unchecked")
    protected <X extends MapObject> X setNullable(Property property, String literal) throws NullPointerException, HasNoModelException {
        resource.removeAll(Objects.requireNonNull(property, "Null property"));
        if (literal == null) return (X) this;
        return addRDFNode(property, mustHaveModel().createLiteral(literal));
    }

    protected <X extends MapObject> X setLiteral(Property property, String literal) throws NullPointerException, HasNoModelException {
        Literal res = mustHaveModel().createLiteral(Objects.requireNonNull(literal, "Null literal"));
        return setRDFNode(property, res);
    }

    protected <X extends MapObject> X setLiteral(Property property, int literal) {
        Literal res = mustHaveModel().createLiteral(String.valueOf(literal), XSD.integer.getURI());
        return setRDFNode(property, res);
    }

    protected <X extends MapObject> X setBoolean(Property property, boolean value) throws NullPointerException, HasNoModelException {
        return setRDFNode(property, value ? Models.TRUE : Models.FALSE);
    }

    protected <X extends MapObject> X setRDFNode(Property property, RDFNode node) throws NullPointerException, HasNoModelException {
        resource.removeAll(Objects.requireNonNull(property, "Null property"));
        return addRDFNode(property, node);
    }

    @SuppressWarnings("unchecked")
    protected <X extends MapObject> X addRDFNode(Property property, RDFNode node) throws NullPointerException, HasNoModelException {
        resource.addProperty(Objects.requireNonNull(property, "Null property"), Objects.requireNonNull(node, "Null value"));
        return (X) this;
    }

    protected boolean getBoolean(Property property,
                                 boolean defaultValue) throws NullPointerException, HasNoModelException, LiteralRequiredException {
        return findFirst(property, s -> s.getLiteral().getBoolean()).orElse(defaultValue);
    }

    protected List<String> getStrings(Property property) throws NullPointerException, HasNoModelException, LiteralRequiredException {
        return listStrings(property).toList();
    }

    protected ExtendedIterator<String> listStrings(Property property) throws NullPointerException, HasNoModelException, LiteralRequiredException {
        return resource.listProperties(Objects.requireNonNull(property)).mapWith(Statement::getString);
    }

    protected Optional<String> findURI(Property property) throws NullPointerException, HasNoModelException, ResourceRequiredException {
        return findFirst(property, s -> s.getResource().getURI());
    }

    protected Optional<String> findString(Property property) throws NullPointerException, HasNoModelException, LiteralRequiredException {
        return findFirst(property, Statement::getString);
    }

    protected <X> Optional<X> findFirst(Property property,
                                        Function<Statement, X> extract) throws NullPointerException, HasNoModelException {
        ExtendedIterator<Statement> res = resource.listProperties(Objects.requireNonNull(property, "Null property"));
        try {
            return !res.hasNext() ? Optional.empty() : Optional.of(res.next()).map(extract);
        } finally {
            res.close();
        }
    }

    public Model mustHaveModel() throws HasNoModelException {
        return mustHaveModel(this);
    }

    private static Model mustHaveModel(MapObject object) throws NullPointerException, HasNoModelException {
        Model res = object.asResource().getModel();
        if (res == null) res = object.getMapping().asModel();
        if (res == null) throw new HasNoModelException(object);
        return res;
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
        return Objects.hash(resource);
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
    protected <X extends MapObject> X copy(MapObject other) throws NullPointerException, HasNoModelException {
        Model b = mustHaveModel(Objects.requireNonNull(other));
        Model a = mustHaveModel();
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
