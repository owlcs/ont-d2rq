package ru.avicomp.d2rq.conf;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import ru.avicomp.ontapi.jena.impl.OntIndividualImpl;
import ru.avicomp.ontapi.jena.impl.PersonalityModel;
import ru.avicomp.ontapi.jena.impl.conf.*;
import ru.avicomp.ontapi.jena.model.OntCE;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

import java.util.Optional;

/**
 * A Personalities holder.
 * <p>
 * Note: if the {@link de.fuberlin.wiwiss.d2rq.map.Configuration#getControlOWL()} setting is specified
 * in the working {@link de.fuberlin.wiwiss.d2rq.map.Mapping},
 * then there is no need in the {@link OntPersonality} version provided by this class.
 * <p>
 * Created by @szuev on 29.03.2018.
 *
 * @see OntModelConfig
 */
@SuppressWarnings("WeakerAccess")
public class D2RQModelConfig {
    /**
     * A {@link OntPersonality Ontology Personality},
     * that does not require explicit {@link OWL#NamedIndividual owl:NamedIndividual} declarations.
     * This personality is based on {@link OntModelConfig#ONT_PERSONALITY_LAX} and other settings are standard.
     */
    public static final OntPersonality D2RQ_PERSONALITY = PersonalityBuilder.from(OntModelConfig.ONT_PERSONALITY_LAX)
            .add(OntIndividual.Named.class, createNamedIndividualFactory())
            .build();


    public static ObjectFactory createNamedIndividualFactory() {
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
                        .anyMatch(o -> PersonalityModel.canAs(OntCE.class, o, g)));
        return new CommonFactoryImpl(maker, finder, filter) {
            @Override
            public String toString() {
                return "NamedIndividualFactory";
            }
        };
    }

    /**
     * Named individual which does not required explicit {@code _:x rdf:type owl:NamedIndividual} declaration, just only class.
     */
    public static class IndividualImpl extends OntIndividualImpl.NamedImpl {
        private IndividualImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        public Optional<OntStatement> findRootStatement() {
            return getOptionalRootStatement(this, OWL.NamedIndividual);
        }
    }
}
