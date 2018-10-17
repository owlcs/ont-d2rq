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
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

/**
 * A {@code d2rq:DownloadMap} instance.
 * This is a d2rq:ResourceMap that must produce URIs,
 * can refer to a {@code d2rq:ClassMap} to provide further relation elements (joins, aliases, conditions),
 * and additionally has a {@code d2rq:mediaType} and {@code d2rq:contentColumn}.
 * Results can be retrieved via {@link #getContentDownloadColumnAttribute()},
 * {@link #getMediaTypeValueMaker()} (for the media type value make),
 * {@link #nodeMaker()} (for the URI spec),
 * and {@link #getRelation()}.
 *
 * @author RichardCyganiak
 */
@SuppressWarnings("WeakerAccess")
public class DownloadMapImpl extends ResourceMap implements DownloadMap {

    public DownloadMapImpl(Resource resource, MappingImpl mapping) {
        super(resource, mapping);
    }

    @Override
    public DownloadMapImpl setBelongsToClassMap(ClassMap classMap) {
        return (DownloadMapImpl) super.setBelongsToClassMap(classMap);
    }

    @Override
    public DownloadMapImpl setDatabase(Database database) {
        return (DownloadMapImpl) super.setDatabase(database);
    }

    @Override
    public DownloadMapImpl setURIPattern(String pattern) {
        return (DownloadMapImpl) super.setURIPattern(pattern);
    }

    @Override
    public DownloadMapImpl setURIColumn(String column) {
        return (DownloadMapImpl) super.setURIColumn(column);
    }

    @Override
    public DownloadMapImpl setConstantValue(String uri) {
        return (DownloadMapImpl) super.setConstantValue(uri);
    }

    @Override
    public DownloadMapImpl setUriSQLExpression(String uriSqlExpression) {
        return (DownloadMapImpl) super.setUriSQLExpression(uriSqlExpression);
    }

    @Override
    public DownloadMapImpl addJoin(String join) {
        return (DownloadMapImpl) super.addJoin(join);
    }

    @Override
    public DownloadMapImpl addCondition(String condition) {
        return (DownloadMapImpl) super.addCondition(condition);
    }

    @Override
    public DownloadMapImpl addAlias(String alias) {
        return (DownloadMapImpl) super.addAlias(alias);
    }

    @Override
    public DownloadMapImpl setMediaType(String mediaType) {
        return setLiteral(D2RQ.mediaType, mediaType);
    }

    @Override
    public String getMediaType() {
        return getString(D2RQ.mediaType);
    }

    @Override
    public DownloadMapImpl setContentDownloadColumn(String column) {
        return setLiteral(D2RQ.contentDownloadColumn, column);
    }

    @Override
    public String getContentDownloadColumn() {
        return getString(D2RQ.contentDownloadColumn);
    }

    @Override
    public void validate() throws D2RQException {
        Validator v = new Validator(this);
        v.requireHasOnlyOneOf(D2RQException.RESOURCEMAP_MISSING_PRIMARYSPEC, D2RQ.uriColumn, D2RQ.uriPattern, D2RQ.constantValue);
        Validator.ForProperty dataStorage = v.forProperty(D2RQ.dataStorage);
        Validator.ForProperty belongsToClassMap = v.forProperty(D2RQ.belongsToClassMap);
        if (dataStorage.exists()) {
            dataStorage.requireHasNoDuplicates(D2RQException.DOWNLOADMAP_DUPLICATE_DATABASE)
                    .requireIsResource(D2RQException.DOWNLOADMAP_INVALID_DATABASE);
        } else if (belongsToClassMap.exists()) {
            belongsToClassMap.requireHasNoDuplicates(D2RQException.DOWNLOADMAP_DUPLICATE_BELONGSTOCLASSMAP)
                    .requireIsResource(D2RQException.DOWNLOADMAP_INVALID_BELONGSTOCLASSMAP);
            new Validator(getBelongsToClassMap())
                    .forProperty(D2RQ.dataStorage)
                    .requireExists(D2RQException.DOWNLOADMAP_NO_DATASTORAGE)
                    .requireHasNoDuplicates(D2RQException.DOWNLOADMAP_DUPLICATE_DATABASE)
                    .requireIsResource(D2RQException.DOWNLOADMAP_INVALID_DATABASE);
        } else {
            throw new D2RQException("Download map " + toString() + " needs a d2rq:dataStorage (or d2rq:belongsToClassMap)",
                    D2RQException.DOWNLOADMAP_NO_DATASTORAGE);
        }
        Validator.ForProperty mediaType = v.forProperty(D2RQ.mediaType);
        if (mediaType.exists()) {
            mediaType.requireHasNoDuplicates(D2RQException.DOWNLOADMAP_DUPLICATE_MEDIATYPE)
                    .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        }
        v.forProperty(D2RQ.contentDownloadColumn)
                .requireExists(D2RQException.DOWNLOADMAP_NO_CONTENTCOLUMN)
                .requireHasNoDuplicates(D2RQException.DOWNLOADMAP_DUPLICATE_CONTENTCOLUMN)
                .requireIsStringLiteral(D2RQException.UNSPECIFIED);
        commonValidateURI();
        commonValidateSQLAdditions();
        commonValidateUnclassifiedAdditions();
        RDFNode constantValue = getConstantValue();
        if (constantValue != null && !constantValue.isURIResource()) {
            throw new D2RQException("d2rq:constantValue for download map " + toString() + " must be a URI",
                    D2RQException.DOWNLOADMAP_INVALID_CONSTANTVALUE);
        }
        PropertyMap.checkURIPattern(this);
    }

    @Override
    protected Relation buildRelation() {
        Attribute contentDownloadColumn = getContentDownloadColumnAttribute();
        ClassMapImpl belongsToClassMap = getBelongsToClassMap();
        ConnectedDB db = mapping.getConnectedDB((belongsToClassMap == null ? getDatabase() : belongsToClassMap.getDatabase()));
        RelationBuilder builder = relationBuilder(db);
        builder.addProjection(contentDownloadColumn);
        for (ProjectionSpec projection : getMediaTypeValueMaker().projectionSpecs()) {
            builder.addProjection(projection);
        }
        if (belongsToClassMap != null) {
            builder.addOther(belongsToClassMap.relationBuilder(db));
        }
        return builder.buildRelation();
    }

    @Override
    public Relation getRelation() {
        validate();
        return buildRelation();
    }

    public ValueMaker getMediaTypeValueMaker() {
        String mediaType = getMediaType();
        if (mediaType == null) return ValueMaker.NULL;
        Pattern pattern = new Pattern(mediaType);
        if (pattern.attributes().isEmpty()) {
            return new ConstantValueMaker(mediaType);
        }
        return pattern;
    }

    public Attribute getContentDownloadColumnAttribute() {
        String column = getContentDownloadColumn();
        return column == null ? null : SQL.parseAttribute(column);
    }
}
