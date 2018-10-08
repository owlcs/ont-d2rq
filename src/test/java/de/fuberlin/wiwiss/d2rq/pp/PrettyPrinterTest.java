package de.fuberlin.wiwiss.d2rq.pp;

import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.shared.impl.PrefixMappingImpl;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.junit.Assert;
import org.junit.Test;

public class PrettyPrinterTest {

    @Test
    public void testNodePrettyPrinting() {
        Assert.assertEquals("\"foo\"", PrettyPrinter.toString(NodeFactory.createLiteral("foo")));
        Assert.assertEquals("\"foo\"@en", PrettyPrinter.toString(NodeFactory.createLiteral("foo", "en", null)));
        Assert.assertEquals("\"1\"^^" + PrettyPrinter.LIBRARY.shortForm(XSDDatatype.XSDint.getURI()),
                PrettyPrinter.toString(NodeFactory.createLiteral("1", null, XSDDatatype.XSDint)));
        Assert.assertEquals("\"1\"^^xsd:int",
                PrettyPrinter.toString(NodeFactory.createLiteral("1", null, XSDDatatype.XSDint), PrefixMapping.Standard));
        Assert.assertEquals("_:foo", PrettyPrinter.toString(NodeFactory.createBlankNode("foo")));
        Assert.assertEquals("<http://example.org/>", PrettyPrinter.toString(NodeFactory.createURI("http://example.org/")));
        Assert.assertEquals("<" + RDF.type.getURI() + ">", PrettyPrinter.toString(RDF.type.asNode(), new PrefixMappingImpl()));
        Assert.assertEquals("rdf:type", PrettyPrinter.toString(RDF.type.asNode(), PrefixMapping.Standard));
        Assert.assertEquals("?x", PrettyPrinter.toString(NodeFactory.createVariable("x")));
        Assert.assertEquals("?ANY", PrettyPrinter.toString(Node.ANY));
    }

    @Test
    public void testTriplePrettyPrinting() {
        Assert.assertEquals("<http://example.org/a> " + PrettyPrinter.LIBRARY.shortForm(RDFS.label.getURI()) + " \"Example\" .",
                PrettyPrinter.toString(new Triple(NodeFactory.createURI("http://example.org/a"),
                        RDFS.label.asNode(),
                        NodeFactory.createLiteral("Example", null, null))));
    }

    @Test
    public void testTriplePrettyPrintingWithNodeANY() {
        Assert.assertEquals("?ANY ?ANY ?ANY .", PrettyPrinter.toString(Triple.ANY));
    }

    @Test
    public void testTriplePrettyPrintingWithPrefixMapping() {
        PrefixMappingImpl prefixes = new PrefixMappingImpl();
        prefixes.setNsPrefixes(PrefixMapping.Standard);
        prefixes.setNsPrefix("ex", "http://example.org/");
        Assert.assertEquals("ex:a rdfs:label \"Example\" .",
                PrettyPrinter.toString(new Triple(NodeFactory.createURI("http://example.org/a"),
                        RDFS.label.asNode(),
                        NodeFactory.createLiteral("Example", null, null)), prefixes));
    }

    @Test
    public void testResourcePrettyPrinting() {
        Model m = ModelFactory.createDefaultModel();
        Assert.assertEquals("\"foo\"", PrettyPrinter.toString(m.createLiteral("foo")));
        Assert.assertEquals("<http://test/>", PrettyPrinter.toString(m.createResource("http://test/")));
    }

    @Test
    public void testUsePrefixMappingWhenPrintingURIResources() {
        Model m = ModelFactory.createDefaultModel();
        m.setNsPrefix("ex", "http://example.org/");
        Assert.assertEquals("ex:foo", PrettyPrinter.toString(m.createResource("http://example.org/foo")));
    }

    @Test
    public void testD2RQTermsHaveD2RQPrefix() {
        Assert.assertEquals("d2rq:ClassMap", PrettyPrinter.toString(D2RQ.ClassMap));
    }

    @Test
    public void testSomeRDFDatatypeToString() {
        RDFDatatype someDatatype = TypeMapper.getInstance().getSafeTypeByName("http://example.org/mytype");
        Assert.assertEquals("<http://example.org/mytype>", PrettyPrinter.toString(someDatatype));
    }

    @Test
    public void testXSDTypeToString() {
        Assert.assertEquals("xsd:string", PrettyPrinter.toString(XSDDatatype.XSDstring));
    }
}
