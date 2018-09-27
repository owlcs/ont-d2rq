package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.DownloadMap;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.ConstantValueMaker;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A d2rq:DownloadMap instance. This is a d2rq:ResourceMap
 * that must produce URIs, can refer to a d2rq:ClassMap to
 * provide further relation elements (joins, aliases, conditions),
 * and additionally has a d2rq:mediaType and d2rq:contentColumn.
 * <p>
 * Results can be retrieved via {@link #getContentDownloadColumn()},
 * {@link #getMediaTypeValueMaker()} (for the media type value make),
 * {@link #nodeMaker()} (for the URI spec),
 * and {@link #getRelation()}.
 *
 * @author RichardCyganiak
 */
public class DownloadMapImpl extends ResourceMap implements DownloadMap {
    private final static Logger LOGGER = LoggerFactory.getLogger(DownloadMapImpl.class);

    private ClassMap belongsToClassMap = null;
    private Database database = null;
    private String mediaType = null;
    private Attribute contentDownloadColumn = null;

    public DownloadMapImpl(Resource downloadMapResource) {
        super(downloadMapResource, false);
    }

    public void setBelongsToClassMap(ClassMap classMap) {
        assertNotYetDefined(belongsToClassMap, D2RQ.belongsToClassMap, D2RQException.DOWNLOADMAP_DUPLICATE_BELONGSTOCLASSMAP);
        assertArgumentNotNull(classMap, D2RQ.belongsToClassMap, D2RQException.DOWNLOADMAP_INVALID_BELONGSTOCLASSMAP);
        belongsToClassMap = classMap;
    }

    public void setDatabase(Database database) {
        assertNotYetDefined(this.database, D2RQ.dataStorage, D2RQException.DOWNLOADMAP_DUPLICATE_DATABASE);
        assertArgumentNotNull(database, D2RQ.dataStorage, D2RQException.DOWNLOADMAP_INVALID_DATABASE);
        this.database = database;
    }

    public void setMediaType(String mediaType) {
        assertNotYetDefined(this.mediaType, D2RQ.mediaType, D2RQException.DOWNLOADMAP_DUPLICATE_MEDIATYPE);
        this.mediaType = mediaType;
    }

    public void setContentDownloadColumn(String contentColumn) {
        assertNotYetDefined(this.contentDownloadColumn, D2RQ.contentDownloadColumn, D2RQException.DOWNLOADMAP_DUPLICATE_CONTENTCOLUMN);
        this.contentDownloadColumn = SQL.parseAttribute(contentColumn);
    }

    @Override
    public void validate() throws D2RQException {
        assertHasPrimarySpec(new Property[]{D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.constantValue});
        if (database == null && belongsToClassMap == null) {
            throw new D2RQException("Download map " + toString() + " needs a d2rq:dataStorage (or d2rq:belongsToClassMap)",
                    D2RQException.DOWNLOADMAP_NO_DATASTORAGE);
        }
        assertHasBeenDefined(contentDownloadColumn, D2RQ.contentDownloadColumn, D2RQException.DOWNLOADMAP_NO_CONTENTCOLUMN);
        if (this.constantValue != null && !this.constantValue.isURIResource()) {
            throw new D2RQException("d2rq:constantValue for download map " + toString() + " must be a URI",
                    D2RQException.DOWNLOADMAP_INVALID_CONSTANTVALUE);
        }
        if (this.uriPattern != null && new Pattern(uriPattern).attributes().size() == 0) {
            LOGGER.warn(String.format("%s has an uriPattern without any column specifications. " +
                    "This usually happens when no primary keys are defined for a table. " +
                    "If the configuration is left as is, all table rows will be mapped to a single instance. " +
                    "If this is not what you want, please define the keys in the database and re-run the mapping generator, " +
                    "or edit the mapping to provide the relevant keys.", toString()));
        }
    }

    @Override
    protected Relation buildRelation() {
        Database db = belongsToClassMap == null ? database : belongsToClassMap.database();
        RelationBuilder builder = relationBuilder(db.connectedDB());
        builder.addProjection(contentDownloadColumn);
        for (ProjectionSpec projection : getMediaTypeValueMaker().projectionSpecs()) {
            builder.addProjection(projection);
        }
        if (belongsToClassMap != null) {
            builder.addOther(((ClassMapImpl) belongsToClassMap).relationBuilder(db.connectedDB()));
        }
        return builder.buildRelation();
    }

    @Override
    public Relation getRelation() {
        validate();
        return buildRelation();
    }

    public ValueMaker getMediaTypeValueMaker() {
        if (mediaType == null) return ValueMaker.NULL;
        Pattern pattern = new Pattern(mediaType);
        if (pattern.attributes().isEmpty()) {
            return new ConstantValueMaker(mediaType);
        }
        return pattern;
    }

    @Override
    public Attribute getContentDownloadColumn() {
        return contentDownloadColumn;
    }
}
