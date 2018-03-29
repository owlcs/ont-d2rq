package ru.avicomp.ontapi.jena.impl.conf;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import ru.avicomp.ontapi.jena.impl.Entities;
import ru.avicomp.ontapi.jena.impl.OntIndividualImpl;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntObject;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Personalities holder.
 * <p>
 * Created by @szuev on 29.03.2018.
 */
public class D2RQModelConfig {
    public static final Configurable.Mode D2RQ_NAMED_INDIVIDUAL_FACTORY_KEY = new Configurable.Mode() {
        @Override
        public String toString() {
            return "D2RQNamedIndividualFactory";
        }
    };
    public static final OntPersonality D2RQ_PERSONALITY = buildD2RQPersonality();

    /**
     * Builds a new {@link OntPersonality} which is based on {@link OntModelConfig#ONT_PERSONALITY_LAX}.
     * The difference is that it does not require owl:NamedIndividual declaration for named individuals.
     *
     * @return {@link OntPersonality}
     */
    private static OntPersonality buildD2RQPersonality() {
        Entities.INDIVIDUAL.register(D2RQ_NAMED_INDIVIDUAL_FACTORY_KEY,
                createNamedIndividualFactory(OntModelConfig.ONT_PERSONALITY_LAX.getOntImplementation(OntCE.class)));
        return OntModelConfig.ONT_PERSONALITY_BUILDER.build(OntModelConfig.ONT_PERSONALITY_LAX, D2RQ_NAMED_INDIVIDUAL_FACTORY_KEY);
    }

    public static OntObjectFactory createNamedIndividualFactory(OntObjectFactory ce) {
        OntMaker maker = new OntMaker.Default(IndividualImpl.class) {

            @Override
            public EnhNode instance(Node node, EnhGraph eg) {
                return new IndividualImpl(node, eg);
            }
        };
        OntFinder finder = new OntFinder.ByPredicate(RDF.type);
        OntFilter filter = OntFilter.URI
                .and(new OntFilter.HasPredicate(RDF.type))
                .and((s, g) -> Iter.asStream(g.asGraph().find(s, RDF.type.asNode(), Node.ANY)).map(Triple::getObject)
                        .anyMatch(o -> ce.canWrap(o, g)));
        return new CommonOntObjectFactory(maker, finder, filter);
    }

    /**
     * Named individual which does not required explicit {@code _:x rdf:type owl:NamedIndividual} declaration, just only class.
     */
    public static class IndividualImpl extends OntIndividualImpl.NamedImpl {
        private IndividualImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        public OntStatement getRoot() {
            OntStatement res = getRoot(RDF.type, OWL.NamedIndividual);
            return res == null ? types().map(r -> getRoot(RDF.type, r)).findFirst().orElse(null) : res;
        }

        @Override
        public Class<? extends OntObject> getActualClass() {
            return OntIndividual.Named.class;
        }
    }
}
