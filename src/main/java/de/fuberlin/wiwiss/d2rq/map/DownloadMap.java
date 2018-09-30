package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;

/**
 * Created by @ssz on 26.09.2018.
 */
public interface DownloadMap extends MapObject, HasDatabase<DownloadMap>, HasURI<DownloadMap>, HasSQL<DownloadMap> {

    // todo: remove from the interface
    Attribute getContentDownloadColumn();

    // todo: remove from the interface
    ValueMaker getMediaTypeValueMaker();

    // todo: move to another interface
    NodeMaker nodeMaker();

    // todo: move to another interface
    Relation getRelation();
}
