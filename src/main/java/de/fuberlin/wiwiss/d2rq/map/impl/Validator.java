package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.MapObject;
import de.fuberlin.wiwiss.d2rq.pp.PrettyPrinter;
import org.apache.jena.rdf.model.*;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.Objects;
import java.util.Set;

/**
 * A class-helper to check that mapping RDF is correct.
 * <p>
 * Created by @ssz on 27.09.2018.
 */
class Validator {

    private final MapObject resource;

    Validator(MapObject object) {
        this.resource = Objects.requireNonNull(object);
    }

    ForProperty forProperty(Property p) {
        return new ForProperty(p);
    }

    private Resource asResource() {
        return resource.asResource();
    }

    private D2RQException newException(String message, int code) {
        return new D2RQException(resource.toString() + ":: " + message, code);
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    class ForProperty {
        protected final Property property;

        ForProperty(Property property) {
            this.property = Objects.requireNonNull(property);
        }

        String asString() {
            return PrettyPrinter.toString(property);
        }

        boolean exists() {
            return asResource().hasProperty(property);
        }

        ForProperty requireExists(int code) {
            if (exists()) return this;
            throw newException("can't find predicate " + asString(), code);
        }

        ForProperty requireHasNoDuplicates(int code) {
            Set<RDFNode> res = asResource().listProperties(property).mapWith(Statement::getObject).toSet();
            if (res.size() == 1) {
                return this;
            }
            throw newException("to many statements for the predicate " + asString() + ": " + res, code);
        }

        ForProperty requireIsStringLiteral(int code) {
            Literal res = asResource().getRequiredProperty(property).getLiteral();
            if (XSD.xstring.getURI().endsWith(res.getDatatypeURI())) {
                return this;
            }
            throw newException("the found literal for the predicate "
                    + asString() + " is not a plain string: " + res, code);
        }

        ForProperty requireIsURI(int code) {
            if (asResource().getRequiredProperty(property).getObject().isURIResource()) {
                return this;
            }
            throw newException("can't find an uri resource for the predicate " + asString(), code);
        }

        ForProperty requireIsResource(int code) {
            if (asResource().getRequiredProperty(property).getObject().isResource()) {
                return this;
            }
            throw newException("can't find a resource for the predicate " + asString(), code);
        }
    }

}
