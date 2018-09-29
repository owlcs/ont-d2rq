package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;

import java.util.Objects;

/**
 * @author J&ouml;rg Hen&szlig;
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings("WeakerAccess")
public class PropertyMap {
    private String uriPattern;

    public PropertyMap(String pattern) {
        this.uriPattern = Objects.requireNonNull(pattern, "Null uri-pattern");
    }

    public NodeMaker nodeMaker() {
        return new TypedNodeMaker(TypedNodeMaker.URI, buildValueSourceBase(), false);
    }

    private ValueMaker buildValueSourceBase() {
        Pattern res = new Pattern(this.uriPattern);
        if (!res.literalPartsMatchRegex(MapParser.IRI_CHAR_REGEX)) {
            throw new D2RQException(String.format("d2rq:uriPattern '%s' contains characters not allowed in URIs", this.uriPattern),
                    D2RQException.RESOURCEMAP_ILLEGAL_URIPATTERN);
        }
        return res;
    }

    public RelationBuilder relationBuilder(ConnectedDB db) {
        RelationBuilder res = new RelationBuilder(db);
        for (ProjectionSpec projection : nodeMaker().projectionSpecs()) {
            res.addProjection(projection);
        }
        return res;
    }

    @Override
    public String toString() {
        return String.format("d2rq:dynamicProperty \"%s\"", this.uriPattern);
    }
}
