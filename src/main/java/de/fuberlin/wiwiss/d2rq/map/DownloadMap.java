package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;

/**
 * Representation of a {@code d2rq:DownloadMap} in the mapping graph.
 *
 * @author RichardCyganiak
 * Created by @ssz on 26.09.2018.
 * @see <a href='http://d2rq.org/d2rq-language#download-map'>8. Enabling HTTP access to CLOBs/BLOBs (d2rq:DownloadMap)</a>
 */
public interface DownloadMap extends MapObject, HasDatabase<DownloadMap>, HasURI<DownloadMap>, HasSQL<DownloadMap> {

    // todo: remove from this interface
    Attribute getContentDownloadColumnAttribute();

    // todo: remove from this interface
    ValueMaker getMediaTypeValueMaker();

    // todo: hide or move to another interface
    NodeMaker nodeMaker();

    // todo: hide or move to another interface
    Relation getRelation();

    /**
     * Sets the given media type for the {@code d2rq:mediaType} predicate,
     * that is served from this download map.
     * It will be sent in the HTTP Content-Type header.
     * Examples include {@code application/pdf}, {@code image/png}, {@code text/plain} and {@code text/html}.
     * If absent, {@code application/octet-stream} will be sent to indicate a generic binary file.
     * The value can be obtained from the database using the {@code d2rq:pattern} syntax.
     * See example:
     * <pre>{@code map:PaperDownloadPDF a d2rq:DownloadMap;
     * d2rq:dataStorage map:database;
     * d2rq:uriPattern "downloads/@@PAPER.ID@@";
     * d2rq:contentDownloadColumn "PAPER.DOWNLOAD";
     * d2rq:mediaType "@@PAPER.MIMETYPE@@" .}</pre>
     *
     * @param mediaType String, not {@code null}
     * @return this instance
     * @see <a href='https://en.wikipedia.org/wiki/Media_type'>Media type</a>
     */
    DownloadMap setMediaType(String mediaType);

    /**
     * Returns a literal value for the {@code d2rq:mediaType} predicate.
     *
     * @return String or {@code null}
     */
    String getMediaType();

    /**
     * Sets the given string pattern for the {@code d2rq:contentDownloadColumn} predicate.
     * That is the column containing downloadable resources, in {@code [SCHEMA.]TABLE.COLUMN} notation.
     *
     * @param column String, not {@code null}
     * @return this instance
     */
    DownloadMap setContentDownloadColumn(String column);

    /**
     * Returns a literal value for the {@code d2rq:contentDownloadColumn} predicate.
     *
     * @return String or {@code null}
     */
    String getContentDownloadColumn();
}
