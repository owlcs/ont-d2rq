package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author J&ouml;rg Hen&szlig;
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class PropertyMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyMap.class);

    /**
     * A regular expression that matches zero or more characters that are allowed inside IRIs
     */
    public static final String IRI_CHAR_REGEX = "([:/?#\\[\\]@!$&'()*+,;=a-zA-Z0-9._~\\x80-\\uFFFF-]|%[0-9A-Fa-f][0-9A-Fa-f])*";

    private String uriPattern;

    public PropertyMap(String pattern) {
        this.uriPattern = Objects.requireNonNull(pattern, "Null uri-pattern");
    }

    public NodeMaker nodeMaker() {
        return new TypedNodeMaker(TypedNodeMaker.URI, buildValueSourceBase(), false);
    }

    protected ValueMaker buildValueSourceBase() {
        Pattern res = toPattern();
        if (!res.literalPartsMatchRegex(IRI_CHAR_REGEX)) {
            throw new D2RQException(String.format("d2rq:uriPattern '%s' contains characters not allowed in URIs", this.uriPattern),
                    D2RQException.RESOURCEMAP_ILLEGAL_URIPATTERN);
        }
        return res;
    }

    public Pattern toPattern() {
        return new Pattern(this.uriPattern);
    }

    public RelationBuilder relationBuilder(ConnectedDB db) {
        RelationBuilder res = new RelationBuilder(db);
        for (ProjectionSpec projection : nodeMaker().projectionSpecs()) {
            res.addProjection(projection);
        }
        return res;
    }

    public static void validate(ResourceMap map) {
        String uriPattern = map.getURIPattern();
        if (uriPattern == null) return;
        if (!new PropertyMap(uriPattern).toPattern().attributes().isEmpty()) {
            return;
        }
        LOGGER.warn(String.format("%s has an uriPattern without any column specifications. " +
                "This usually happens when no primary keys are defined for a table. " +
                "If the configuration is left as is, all table rows will be mapped to a single instance. " +
                "If this is not what you want, please define the keys in the database and re-run the mapping generator, " +
                "or edit the mapping to provide the relevant keys.", map.toString()));
    }

    @Override
    public String toString() {
        return String.format("d2rq:dynamicProperty \"%s\"", this.uriPattern);
    }
}
