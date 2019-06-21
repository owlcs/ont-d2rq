package de.fuberlin.wiwiss.d2rq;

import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.RelationalOperators;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.find.FindQuery;
import de.fuberlin.wiwiss.d2rq.find.TripleQueryIter;
import de.fuberlin.wiwiss.d2rq.map.impl.ClassMapImpl;
import de.fuberlin.wiwiss.d2rq.map.impl.MappingImpl;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import org.apache.jena.graph.GraphUtil;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

/**
 * Provides access to the contents of a d2rq:ClassMap in various
 * ways.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@Deprecated
// TODO: the pure Jena offers much more possibilities to traverse around the graph.
// TODO: Since I'am going to change Mapping to be backed by the graph
// TODO: this class is scheduled to be deleted
public class ClassMapLister {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassMapLister.class);

    private final MappingImpl mapping;

    private Map<String, List<TripleRelation>> classMapInventoryBridges = new HashMap<>();
    private Map<String, NodeMaker> classMapNodeMakers = new HashMap<>();

    public ClassMapLister(MappingImpl mapping) {
        this.mapping = mapping;
        groupTripleRelationsByClassMap();
    }

    private void groupTripleRelationsByClassMap() {
        if (!classMapInventoryBridges.isEmpty() || !classMapNodeMakers.isEmpty()) return;
        mapping.classMaps().forEach(c -> {
            ClassMapImpl classMap = ((ClassMapImpl) c);
            Resource classMapResource = classMap.asResource();
            NodeMaker resourceMaker = classMap.nodeMaker();
            Node classMapNode = classMapResource.asNode();
            ClassMapLister.this.classMapNodeMakers.put(toClassMapName(classMapNode), resourceMaker);
            List<TripleRelation> inventoryBridges = new ArrayList<>();
            for (TripleRelation bridge : classMap.toTripleRelations()) {
                bridge = bridge.orderBy(TripleRelation.SUBJECT, true);
                if (bridge.selectTriple(new Triple(Node.ANY, RDF.Nodes.type, Node.ANY)) != null) {
                    inventoryBridges.add(bridge);
                }
                // TODO The list of label properties is redundantly specified in PageServlet
                if (bridge.selectTriple(new Triple(Node.ANY, RDFS.label.asNode(), Node.ANY)) != null) {
                    inventoryBridges.add(bridge);
                } else if (bridge.selectTriple(new Triple(Node.ANY, SKOS.prefLabel.asNode(), Node.ANY)) != null) {
                    inventoryBridges.add(bridge);
                } else if (bridge.selectTriple(new Triple(Node.ANY, DC.title.asNode(), Node.ANY)) != null) {
                    inventoryBridges.add(bridge);
                } else if (bridge.selectTriple(new Triple(Node.ANY, DCTerms.title.asNode(), Node.ANY)) != null) {
                    inventoryBridges.add(bridge);
                } else if (bridge.selectTriple(new Triple(Node.ANY, FOAF.name.asNode(), Node.ANY)) != null) {
                    inventoryBridges.add(bridge);
                }
            }
            if (inventoryBridges.isEmpty()) {
                Relation relation = classMap.getRelation();
                NodeMaker typeNodeMaker = new FixedNodeMaker(
                        RDF.type.asNode(), false);
                NodeMaker resourceNodeMaker = new FixedNodeMaker(RDFS.Resource.asNode(), false);
                inventoryBridges.add(new TripleRelation(relation,
                        resourceMaker, typeNodeMaker, resourceNodeMaker));
            }
            ClassMapLister.this.classMapInventoryBridges.put(toClassMapName(classMapNode), inventoryBridges);
        });
    }

    private String toClassMapName(Node classMap) {
        return classMap.getLocalName();
    }

    public Collection<String> classMapNames() {
        return this.classMapInventoryBridges.keySet();
    }

    public Model classMapInventory(String classMapName) {
        return classMapInventory(classMapName, Relation.NO_LIMIT);
    }

    public Model classMapInventory(String classMapName, int limitPerClassMap) {
        LOGGER.info("Listing class map: " + classMapName);
        List<TripleRelation> inventoryBridges = classMapInventoryBridges.get(classMapName);
        if (inventoryBridges == null) {
            return null;
        }
        Model result = ModelFactory.createDefaultModel();
        result.setNsPrefixes(mapping.getSchema().getPrefixMapping());
        FindQuery query = new FindQuery(Triple.ANY, inventoryBridges, limitPerClassMap, null);
        // todo: no more com.hp.hpl.jena.graph.BulkUpdateHandler. Use org.apache.jena.graph.GraphUtil:
        //result.getGraph().getBulkUpdateHandler().add(TripleQueryIter.create(query.iterator()));
        GraphUtil.add(result.getGraph(), TripleQueryIter.create(query.iterator()));
        return result;
    }

    public Collection<String> classMapNamesForResource(Node resource) {
        if (!resource.isURI()) {
            return Collections.emptyList();
        }
        List<String> results = new ArrayList<>();
        for (Entry<String, NodeMaker> entry : classMapNodeMakers.entrySet()) {
            String classMapName = entry.getKey();
            NodeMaker nodeMaker = entry.getValue();
            if (!nodeMaker.selectNode(resource, RelationalOperators.DUMMY).equals(NodeMaker.EMPTY)) {
                results.add(classMapName);
            }
        }
        return results;
    }
}
