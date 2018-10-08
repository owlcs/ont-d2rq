package de.fuberlin.wiwiss.d2rq.pp;

import de.fuberlin.wiwiss.d2rq.D2RQException;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import org.apache.jena.JenaRuntime;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.datatypes.xsd.impl.RDFLangString;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.shared.PrefixMapping;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Pretty printer for various kinds of objects.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class PrettyPrinter {

    public static final PrefixMapping LIBRARY = PrefixMapping.Factory.create()
            .setNsPrefixes(MappingFactory.MAPPING).setNsPrefixes(MappingFactory.SCHEMA).lock();

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
     * Pretty-prints an RDF node and shortens URIs into QNames according to a {@link PrefixMapping}.
     *
     * @param n An RDF node
     * @param prefixes {@link PrefixMapping}
     * @return An N-Triples style textual representation with URIs shortened to QNames
     */
    public static String toString(Node n, PrefixMapping prefixes) {
        if (prefixes == null) prefixes = LIBRARY;
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
        return toString(n.asNode(), n.getModel());
    }

    public static String toString(Collection<? extends RDFNode> res) {
        return res.stream().map(PrettyPrinter::toString).collect(Collectors.joining(", "));
    }
}
