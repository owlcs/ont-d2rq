package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.values.ValueMaker;

/**
 * TODO: all these 4 methods are only to construct DownloadContentQuery => it seems one more stupid thing.
 * Created by @ssz on 26.09.2018.
 */
public interface DownloadMap extends MapObject, HasDatabase<DownloadMap> {

    // todo: remove from interface
    Attribute getContentDownloadColumn();

    // todo: remove from interface
    ValueMaker getMediaTypeValueMaker();

    // todo: remove from interface
    NodeMaker nodeMaker();

    // todo: remove from interface
    Relation getRelation();
}
