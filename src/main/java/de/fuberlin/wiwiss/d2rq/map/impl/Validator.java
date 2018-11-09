package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.MapObject;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.*;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * A class-helper to check that mapping object is correct.
 * <p>
 * Created by @ssz on 27.09.2018.
 */
@SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
class Validator {

    private final MapObject resource;

    Validator(MapObject object) {
        this.resource = Objects.requireNonNull(object);
    }

    ForProperty forProperty(Property p) {
        return new ForProperty(p);
    }

    private Resource getMapResource() {
        return resource.asResource();
    }

    private D2RQException newException(String message, int code) {
        return new D2RQException(resource.toString() + ":: " + message, code);
    }

    void requireHasOnlyOneOf(int code, Property... properties) {
        Set<Property> found = Iter.of(properties).filterKeep(p -> getMapResource().hasProperty(p)).toSet();
        if (found.isEmpty()) {
            throw newException("no required spec found, needs one of: " +
                    PrettyPrinter.toString(Arrays.asList(properties)), code);
        }
        if (found.size() > 1) {
            throw newException("must have only one of the following: " + PrettyPrinter.toString(found), code);
        }
    }

    void requireHasNoMoreThanOne(int code, Property... properties) {
        Set<Property> found = Iter.of(properties).filterKeep(p -> getMapResource().hasProperty(p)).toSet();
        if (found.size() <= 1) {
            return;
        }
        throw newException("can't have more than one of the following: " + PrettyPrinter.toString(found), code);
    }


    class ForProperty {
        protected final Property property;

        private ForProperty(Property property) {
            this.property = Objects.requireNonNull(property);
        }

        String asString() {
            return PrettyPrinter.toString(property);
        }

        boolean exists() {
            return getMapResource().hasProperty(property);
        }

        RDFNode get() {
            return getMapResource().getRequiredProperty(property).getObject();
        }

        Literal getLiteral() {
            return getLiteral(D2RQException.MAPPING_RESOURCE_INSTEADOF_LITERAL);
        }

        Literal getLiteral(int code) {
            RDFNode res = get();
            if (!res.isLiteral()) {
                throw newException("no literal for the predicate " + asString() + ": " + res, code);
            }
            return res.asLiteral();
        }

        Resource getResource() {
            return getResource(D2RQException.MAPPING_LITERAL_INSTEADOF_RESOURCE);
        }

        Resource getResource(int code) {
            RDFNode res = get();
            if (!res.isResource()) {
                throw newException("no resource for the predicate " + asString() + ": " + res, code);
            }
            return res.asResource();
        }

        ForProperty requireExists(int code) {
            if (exists()) return this;
            throw newException("can't find predicate " + asString(), code);
        }

        ForProperty requireHasNoDuplicates(int code) {
            Set<RDFNode> res = getMapResource().listProperties(property).mapWith(Statement::getObject).toSet();
            if (res.size() == 1) {
                return this;
            }
            throw newException("to many statements for the predicate " + asString() + ": " + res, code);
        }

        ForProperty requireIsStringLiteral(int code) {
            return requireIsLiteralOfType(XSD.xstring, code);
        }

        ForProperty requireIsIntegerLiteral(int code) {
            return requireIsLiteralOfType(XSD.integer, code);
        }

        ForProperty requireIsBooleanLiteral(int code) {
            return requireIsLiteralOfType(XSD.xboolean, code);
        }

        ForProperty requireIsLiteralOfType(Resource datatypeURI, int code) {
            Literal res = getLiteral();
            if (datatypeURI.getURI().equals(res.getDatatypeURI())) {
                return this;
            }
            throw newException("the found literal for the predicate "
                    + asString() + " is not of the " + PrettyPrinter.toString(datatypeURI) + " type :" + res.getDatatypeURI(), code);
        }

        ForProperty requireContainsOnlyStrings(int code) {
            Set<RDFNode> res = getMapResource().listProperties(property)
                    .mapWith(Statement::getObject)
                    .filterKeep(s -> !s.isLiteral() || !XSD.xstring.getURI().equals(s.asLiteral().getDatatypeURI()))
                    .toSet();
            if (res.isEmpty()) {
                return this;
            }
            throw newException("found non-string literals for the property " + asString() + ": " + res, code);
        }

        ForProperty requireIsURI(int code) {
            if (get().isURIResource()) {
                return this;
            }
            throw newException("can't find an uri resource for the predicate " + asString(), code);
        }

        ForProperty requireIsValidURL(int code) {
            String uri = getResource().getURI();
            try {
                new URL(uri);
                return this;
            } catch (MalformedURLException e) {
                throw new D2RQException("The uri " + uri + " for the predicate " + asString() +
                        " is not valid URL", e, code);
            }

        }

        ForProperty requireIsNotAnonymous(int code) {
            if (!get().isAnon()) {
                return this;
            }
            throw newException("found anonymous resource for the predicate " + asString(), code);
        }

        ForProperty requireIsResource(int code) {
            if (get().isResource()) {
                return this;
            }
            throw newException("can't find a resource for the predicate " + asString(), code);
        }

        ForProperty requireValidClassReference(Class<?> expected, int code) {
            String className = getLiteral().getString();
            Class<?> res;
            try {
                res = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new D2RQException("Class-ref " + className + " from the predicate " + asString()
                        + " in not in class-path", e, code);
            }
            if (expected.isAssignableFrom(res)) return this;
            throw new D2RQException("Class-ref " + className + " from the predicate " + asString() +
                    " in not assignable to the " + expected, code);
        }
    }

}
