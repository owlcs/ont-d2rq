package de.fuberlin.wiwiss.d2rq.pp;

import org.apache.jena.JenaRuntime;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.Mapping;
import de.fuberlin.wiwiss.d2rq.vocab.D2RConfig;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;

/**
 * Pretty printer for various kinds of objects.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class PrettyPrinter {

    static {
        // Make sure that the model behind all the
        // D2RQ vocabulary terms has the d2rq prefix
        D2RQ.ClassMap.getModel().setNsPrefix(Mapping.Prefixes.D2RQ_PREFIX, D2RQ.NS);
        // Same for D2RConfig
        D2RConfig.Server.getModel().setNsPrefix("d2r", D2RConfig.NS);
    }

    /**
     * Pretty-prints an RDF node.
     *
     * @param n An RDF node
     * @return An N-Triples style textual representation
     */
    public static String toString(Node n) {
        return toString(n, null);
    }

    /**
     * Pretty-prints an RDF node and shortens URIs into QNames according to a
     * {@link PrefixMapping}.
     *
     * @param n An RDF node
     * @return An N-Triples style textual representation with URIs shortened to QNames
     */
    public static String toString(Node n, PrefixMapping prefixes) {
        if (n.isURI()) {
            return qNameOrURI(n.getURI(), prefixes);
        }
        if (n.isBlank()) {
            return "_:" + n.getBlankNodeLabel();
        }
        if (n.isVariable()) {
            return "?" + n.getName();
        }
        if (Node.ANY.equals(n)) {
            return "?ANY";
        }
        // should be Literal
        if (!n.isLiteral()) {
            throw new D2RQException("Not a literal " + n);
        }
        String s = "\"" + n.getLiteralLexicalForm() + "\"";
        if (!"".equals(n.getLiteralLanguage())) {
            s += "@" + n.getLiteralLanguage();
        }
        RDFDatatype dt = n.getLiteralDatatype();
        if (JenaRuntime.isRDF11) {
            if (XSDDatatype.XSDstring.equals(dt) || RDFLangString.rdfLangString.equals(dt))
                return s;
        } else if (dt == null) {
            return s;
        }
        if (dt == null)
            throw new D2RQException("Literal " + n + " has no datatype");
        s += "^^" + qNameOrURI(dt.getURI(), prefixes);
        return s;
    }

    private static String qNameOrURI(String uri, PrefixMapping prefixes) {
        if (prefixes == null) {
            return "<" + uri + ">";
        }
        String qName = prefixes.qnameFor(uri);
        if (qName != null) {
            return qName;
        }
        return "<" + uri + ">";

    }

    public static String toString(Triple t) {
        return toString(t, null);
    }

    public static String toString(Triple t, PrefixMapping prefixes) {
        return toString(t.getSubject(), prefixes) + " "
                + toString(t.getPredicate(), prefixes) + " "
                + toString(t.getObject(), prefixes) + " .";
    }

    public static String toString(RDFDatatype datatype) {
        return qNameOrURI(datatype.getURI(), PrefixMapping.Standard);
    }

    public static String toString(RDFNode n) {
        if (n.isURIResource()) {
            Resource r = (Resource) n;
            return toString(r.asNode(), r.getModel());
        }
        return toString(n.asNode());
    }
}
