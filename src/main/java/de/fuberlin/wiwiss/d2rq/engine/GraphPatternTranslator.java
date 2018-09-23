package de.fuberlin.wiwiss.d2rq.engine;

import de.fuberlin.wiwiss.d2rq.algebra.NodeRelation;
import de.fuberlin.wiwiss.d2rq.algebra.Relation;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import org.apache.jena.graph.Triple;

import java.util.*;

/**
 * Matches a BGP against a collection of {@link TripleRelation}s
 * and returns a collection of {@link NodeRelation}s.
 * <p>
 * The node relations produce the same bindings that one would
 * get from matching the BGP against the materialized triples
 * produced by the triple relations.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class GraphPatternTranslator {
    private final List<Triple> triplePatterns;
    private final Collection<TripleRelation> tripleRelations;
    private boolean useAllOptimizations;

    public GraphPatternTranslator(List<Triple> triplePatterns,
                                  Collection<TripleRelation> tripleRelations,
                                  boolean useAllOptimizations) {
        this.triplePatterns = triplePatterns;
        this.tripleRelations = tripleRelations;
        this.useAllOptimizations = useAllOptimizations;
    }

    /**
     * @return A list of {@link NodeRelation}s
     */
    public List<NodeRelation> translate() {
        if (triplePatterns.isEmpty()) {
            return Collections.singletonList(NodeRelation.TRUE);
        }
        Iterator<Triple> it = triplePatterns.iterator();
        List<CandidateList> candidateLists = new ArrayList<>(triplePatterns.size());
        int index = 1;
        while (it.hasNext()) {
            Triple triplePattern = it.next();
            // use always index
            // index is now unique over one sparq-query-execution
            CandidateList candidates = new CandidateList(
                    triplePattern, triplePatterns.size() > 1, index);
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            candidateLists.add(candidates);
            // inc value
            index++;
        }
        Collections.sort(candidateLists);
        List<TripleRelationJoiner> joiners = new ArrayList<>();
        joiners.add(TripleRelationJoiner.create(this.useAllOptimizations));
        for (CandidateList candidates : candidateLists) {
            List<TripleRelationJoiner> nextJoiners = new ArrayList<>();
            for (TripleRelationJoiner joiner : joiners) {
                nextJoiners.addAll(joiner.joinAll(candidates.triplePattern(), candidates.all()));
            }
            joiners = nextJoiners;
        }
        List<NodeRelation> results = new ArrayList<>(joiners.size());
        for (TripleRelationJoiner joiner : joiners) {
            NodeRelation nodeRelation = joiner.toNodeRelation();
            if (!nodeRelation.baseRelation().equals(Relation.EMPTY) || !useAllOptimizations)
                results.add(nodeRelation);
        }
        return results;
    }

    private class CandidateList implements Comparable<CandidateList> {
        private final Triple triplePattern;
        private final List<NodeRelation> candidates;

        CandidateList(Triple triplePattern, boolean useIndex, int index) {
            this.triplePattern = triplePattern;
            List<NodeRelation> matches = findMatchingTripleRelations(triplePattern);
            if (useIndex) {
                candidates = prefixTripleRelations(matches, index);
            } else {
                candidates = matches;
            }
        }

        boolean isEmpty() {
            return candidates.isEmpty();
        }

        Triple triplePattern() {
            return triplePattern;
        }

        List<NodeRelation> all() {
            return candidates;
        }

        @Override
        public int compareTo(CandidateList other) {
            return Integer.compare(candidates.size(), other.candidates.size());
        }

        private List<NodeRelation> findMatchingTripleRelations(Triple triplePattern) {
            List<NodeRelation> results = new ArrayList<>();
            for (TripleRelation tripleRelation : tripleRelations) {
                TripleRelation selected = tripleRelation.selectTriple(triplePattern);
                if (selected == null) continue;
                results.add(selected);
            }
            return results;
        }

        private List<NodeRelation> prefixTripleRelations(List<NodeRelation> tripleRelations, int index) {
            List<NodeRelation> results = new ArrayList<>(tripleRelations.size());
            for (NodeRelation tripleRelation : tripleRelations) {
                results.add(tripleRelation.withPrefix(index));
            }
            return results;
        }

        @Override
        public String toString() {
            return "CandidateList(" + triplePattern + ")[" + candidates + "]";
        }
    }
}
