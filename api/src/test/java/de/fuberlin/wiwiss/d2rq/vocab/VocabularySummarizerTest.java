package de.fuberlin.wiwiss.d2rq.vocab;

import de.fuberlin.wiwiss.d2rq.utils.JenaModelUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;


public class VocabularySummarizerTest {

    @Test
    public void testAllPropertiesEmpty() {
        VocabularySummarizer vocab = new VocabularySummarizer(Object.class);
        Assert.assertTrue(vocab.getAllProperties().isEmpty());
    }

    @Test
    public void testAllClassesEmpty() {
        VocabularySummarizer vocab = new VocabularySummarizer(Object.class);
        Assert.assertTrue(vocab.getAllClasses().isEmpty());
    }

    @Test
    public void testAllPropertiesContainsProperty() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getAllProperties().contains(D2RQ.column));
        Assert.assertTrue(vocab.getAllProperties().contains(D2RQ.belongsToClassMap));
    }

    @Test
    public void testAllPropertiesDoesNotContainClass() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        //noinspection SuspiciousMethodCalls
        Assert.assertFalse(vocab.getAllProperties().contains(D2RQ.Database));
    }

    @Test
    public void testAllPropertiesDoesNotContainTermFromOtherNamespace() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertFalse(vocab.getAllProperties().contains(RDF.type));
    }

    @Test
    public void testAllClassesContainsClass() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getAllClasses().contains(D2RQ.Database));
    }

    @Test
    public void testAllClassesDoesNotContainProperty() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertFalse(vocab.getAllClasses().contains(D2RQ.column));
    }

    @Test
    public void testAllClassesDoesNotContainTermFromOtherNamespace() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertFalse(vocab.getAllClasses().contains(D2RConfig.Server));
    }

    @Test
    public void testGetNamespaceEmpty() {
        Assert.assertNull(new VocabularySummarizer(Object.class).getNamespace());
    }

    @Test
    public void testGetNamespaceD2RQ() {
        Assert.assertEquals(D2RQ.NS, new VocabularySummarizer(D2RQ.class).getNamespace());
    }

    @Test
    public void testGetNamespaceD2RConfig() {
        Assert.assertEquals(D2RConfig.NS, new VocabularySummarizer(D2RConfig.class).getNamespace());
    }

    @Test
    public void testNoUndefinedClassesForEmptyModel() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedClasses(ModelFactory.createDefaultModel()).isEmpty());
    }

    @Test
    public void testNoUndefinedClassesWithoutTypeStatement() {
        Model m = JenaModelUtils.loadTurtle("/vocab/no-type.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedClasses(m).isEmpty());
    }

    @Test
    public void testNoUndefinedClassesIfAllClassesDefined() {
        Model m = JenaModelUtils.loadTurtle("/vocab/defined-types.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedClasses(m).isEmpty());
    }

    @Test
    public void testNoUndefinedClassesIfAllInOtherNamespace() {
        Model m = JenaModelUtils.loadTurtle("/vocab/other-namespace-types.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedClasses(m).isEmpty());
    }

    @Test
    public void testFindOneUndefinedClass() {
        final Model m = JenaModelUtils.loadTurtle("/vocab/one-undefined-type.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Collection<Resource> expected = new HashSet<Resource>() {{
            this.add(m.createResource(D2RQ.NS + "Pint"));
        }};
        Assert.assertEquals(expected, vocab.getUndefinedClasses(m));
    }

    @Test
    public void testFindTwoUndefinedClasses() {
        final Model m = JenaModelUtils.loadTurtle("/vocab/two-undefined-types.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Collection<Resource> expected = new HashSet<Resource>() {{
            this.add(m.createResource(D2RQ.NS + "Pint"));
            this.add(m.createResource(D2RQ.NS + "Shot"));
        }};
        Assert.assertEquals(expected, vocab.getUndefinedClasses(m));
    }

    @Test
    public void testNoUndefinedPropertiesForEmptyModel() {
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedProperties(ModelFactory.createDefaultModel()).isEmpty());
    }

    @Test
    public void testNoUndefinedPropertiesIfAllPropertiesDefined() {
        Model m = JenaModelUtils.loadTurtle("/vocab/defined-properties.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedProperties(m).isEmpty());
    }

    @Test
    public void testNoUndefinedPropertiesIfAllInOtherNamespace() {
        Model m = JenaModelUtils.loadTurtle("/vocab/other-namespace-properties.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Assert.assertTrue(vocab.getUndefinedProperties(m).isEmpty());
    }

    @Test
    public void testFindOneUndefinedProperty() {
        final Model m = JenaModelUtils.loadTurtle("/vocab/one-undefined-property.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Collection<Property> expected = new HashSet<Property>() {{
            this.add(m.createProperty(D2RQ.NS + "price"));
        }};
        Assert.assertEquals(expected, vocab.getUndefinedProperties(m));
    }

    @Test
    public void testFindTwoUndefinedProperties() {
        final Model m = JenaModelUtils.loadTurtle("/vocab/two-undefined-properties.ttl");
        VocabularySummarizer vocab = new VocabularySummarizer(D2RQ.class);
        Collection<Property> expected = new HashSet<Property>() {{
            this.add(m.createProperty(D2RQ.NS + "price"));
            this.add(m.createProperty(D2RQ.NS + "parallelUniverse"));
        }};
        Assert.assertEquals(expected, vocab.getUndefinedProperties(m));
    }
}
