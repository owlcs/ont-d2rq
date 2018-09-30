package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.ProjectionSpec;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.map.ClassMap;
import de.fuberlin.wiwiss.d2rq.map.Database;
import de.fuberlin.wiwiss.d2rq.map.DownloadMap;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.ConstantValueMaker;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
    private String mediaType = null;
    private Attribute contentDownloadColumn = null;

    public DownloadMapImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    public void setBelongsToClassMap(ClassMap classMap) {
        assertNotYetDefined(belongsToClassMap, D2RQ.belongsToClassMap, D2RQException.DOWNLOADMAP_DUPLICATE_BELONGSTOCLASSMAP);
        assertArgumentNotNull(classMap, D2RQ.belongsToClassMap, D2RQException.DOWNLOADMAP_INVALID_BELONGSTOCLASSMAP);
        belongsToClassMap = classMap;
    }

    @Override
    public DownloadMap setDatabase(Database database) {
        DatabaseImpl res = mapping.asDatabase(database.asResource()).copy(database);
        setRDFNode(D2RQ.dataStorage, res.asResource());
        return this;
    }

    @Override
    public DatabaseImpl getDatabase() {
        List<Resource> r = resource.listProperties(D2RQ.dataStorage).mapWith(Statement::getResource).toList();
        return r.size() == 1 ? mapping.asDatabase(r.get(0)) : null;
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
        Validator v = new Validator(this);
        Validator.ForProperty dbChecker = v.forProperty(D2RQ.dataStorage);
        if (dbChecker.exists()) {
            dbChecker.requireHasNoDuplicates(D2RQException.DOWNLOADMAP_DUPLICATE_DATABASE)
                    .requireIsResource(D2RQException.DOWNLOADMAP_INVALID_DATABASE);
        } else {
            if (belongsToClassMap == null) {
                throw new D2RQException("Download map " + toString() + " needs a d2rq:dataStorage (or d2rq:belongsToClassMap)",
                        D2RQException.DOWNLOADMAP_NO_DATASTORAGE);
            }
        }

        assertHasPrimarySpec(new Property[]{D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.constantValue});
        assertHasBeenDefined(contentDownloadColumn, D2RQ.contentDownloadColumn, D2RQException.DOWNLOADMAP_NO_CONTENTCOLUMN);
        RDFNode constantValue = getConstantValue();
        if (constantValue != null && !constantValue.isURIResource()) {
            throw new D2RQException("d2rq:constantValue for download map " + toString() + " must be a URI",
                    D2RQException.DOWNLOADMAP_INVALID_CONSTANTVALUE);
        }
        PropertyMap.validate(this);
    }

    @Override
    protected Relation buildRelation() {
        ConnectedDB db = mapping.getConnectedDB((belongsToClassMap == null ? getDatabase() : (DatabaseImpl) belongsToClassMap.getDatabase()));
        RelationBuilder builder = relationBuilder(db);
        builder.addProjection(contentDownloadColumn);
        for (ProjectionSpec projection : getMediaTypeValueMaker().projectionSpecs()) {
            builder.addProjection(projection);
        }
        if (belongsToClassMap != null) {
            builder.addOther(((ClassMapImpl) belongsToClassMap).relationBuilder(db));
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
