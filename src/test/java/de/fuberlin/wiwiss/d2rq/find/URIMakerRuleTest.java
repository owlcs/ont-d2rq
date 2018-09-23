package de.fuberlin.wiwiss.d2rq.find;

import de.fuberlin.wiwiss.d2rq.algebra.*;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.find.URIMakerRule.URIMakerRuleChecker;
import de.fuberlin.wiwiss.d2rq.nodes.FixedNodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.values.Column;
import de.fuberlin.wiwiss.d2rq.values.Pattern;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * :cm1 a d2rq:ClassMap;
 * d2rq:uriPattern "http://test/person@@employees.ID@@";
 * d2rq:class foaf:Person;
 * d2rq:propertyBridge [
 * d2rq:property foaf:knows;
 * d2rq:uriPattern "http://test/person@@employees.manager@@";
 * ];
 * d2rq:propertyBridge [
 * d2rq:property foaf:homepage;
 * d2rq:uriColumn "employees.homepage";
 * ];
 * .
 * :cm2 a d2rq:ClassMap;
 * d2rq:uriColumn "employees.homepage";
 * d2rq:class foaf:Document;
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class URIMakerRuleTest {
    private TripleRelation withURIPatternSubject;
    private TripleRelation withURIPatternSubjectAndObject;
    private TripleRelation withURIColumnSubject;
    private TripleRelation withURIPatternSubjectAndURIColumnObject;
    private URIMakerRuleChecker employeeChecker;
    private URIMakerRuleChecker foobarChecker;

    @Before
    public void setUp() {
        Relation base = new RelationImpl(null, AliasMap.NO_ALIASES,
                Expression.TRUE, Expression.TRUE,
                Collections.emptySet(), Collections.emptySet(),
                false, OrderSpec.NONE, Relation.NO_LIMIT, Relation.NO_LIMIT);
        this.withURIPatternSubject = new TripleRelation(base,
                new TypedNodeMaker(TypedNodeMaker.URI,
                        new Pattern("http://test/person@@employees.ID@@"), true),
                new FixedNodeMaker(RDF.type.asNode(), false),
                new FixedNodeMaker(FOAF.Person.asNode(), false));
        this.withURIPatternSubjectAndObject = new TripleRelation(base,
                new TypedNodeMaker(TypedNodeMaker.URI,
                        new Pattern("http://test/person@@employees.ID@@"), true),
                new FixedNodeMaker(FOAF.knows.asNode(), false),
                new TypedNodeMaker(TypedNodeMaker.URI,
                        new Pattern("http://test/person@@employees.manager@@"), true));
        this.withURIColumnSubject = new TripleRelation(base,
                new TypedNodeMaker(TypedNodeMaker.URI,
                        new Column(new Attribute(null, "employees", "homepage")), false),
                new FixedNodeMaker(RDF.type.asNode(), false),
                new FixedNodeMaker(FOAF.Document.asNode(), false));
        this.withURIPatternSubjectAndURIColumnObject = new TripleRelation(base,
                new TypedNodeMaker(TypedNodeMaker.URI,
                        new Pattern("http://test/person@@employees.ID@@"), true),
                new FixedNodeMaker(FOAF.homepage.asNode(), false),
                new TypedNodeMaker(TypedNodeMaker.URI,
                        new Column(new Attribute(null, "employees", "homepage")), false));
        this.employeeChecker = new URIMakerRule().createRuleChecker(
                NodeFactory.createURI("http://test/person1"));
        this.foobarChecker = new URIMakerRule().createRuleChecker(
                NodeFactory.createURI("http://test/foobar"));
    }

    @Test
    public void testComparator() {
        URIMakerRule u = new URIMakerRule();
        Assert.assertEquals(0, u.compare(this.withURIPatternSubject, this.withURIPatternSubject));
        Assert.assertEquals(1, u.compare(this.withURIPatternSubject, this.withURIPatternSubjectAndObject));
        Assert.assertEquals(-1, u.compare(this.withURIPatternSubject, this.withURIColumnSubject));
        Assert.assertEquals(-1, u.compare(this.withURIPatternSubject, this.withURIPatternSubjectAndURIColumnObject));

        Assert.assertEquals(-1, u.compare(this.withURIPatternSubjectAndObject, this.withURIPatternSubject));
        Assert.assertEquals(0, u.compare(this.withURIPatternSubjectAndObject, this.withURIPatternSubjectAndObject));
        Assert.assertEquals(-1, u.compare(this.withURIPatternSubjectAndObject, this.withURIColumnSubject));
        Assert.assertEquals(-1, u.compare(this.withURIPatternSubjectAndObject, this.withURIPatternSubjectAndURIColumnObject));

        Assert.assertEquals(1, u.compare(this.withURIColumnSubject, this.withURIPatternSubject));
        Assert.assertEquals(1, u.compare(this.withURIColumnSubject, this.withURIPatternSubjectAndObject));
        Assert.assertEquals(0, u.compare(this.withURIColumnSubject, this.withURIColumnSubject));
        Assert.assertEquals(1, u.compare(this.withURIColumnSubject, this.withURIPatternSubjectAndURIColumnObject));

        Assert.assertEquals(1, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIPatternSubject));
        Assert.assertEquals(1, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIPatternSubjectAndObject));
        Assert.assertEquals(-1, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIColumnSubject));
        Assert.assertEquals(0, u.compare(this.withURIPatternSubjectAndURIColumnObject, this.withURIPatternSubjectAndURIColumnObject));
    }

    @Test
    public void testSort() {
        Collection<TripleRelation> unsorted = new ArrayList<>(Arrays.asList(this.withURIColumnSubject,
                this.withURIPatternSubject,
                this.withURIPatternSubjectAndObject,
                this.withURIPatternSubjectAndURIColumnObject));
        Collection<TripleRelation> sorted = new ArrayList<>(Arrays.asList(this.withURIPatternSubjectAndObject,
                this.withURIPatternSubject,
                this.withURIPatternSubjectAndURIColumnObject,
                this.withURIColumnSubject));
        Assert.assertEquals(sorted, new URIMakerRule().sortRDFRelations(unsorted));
    }

    @Test
    public void testRuleCheckerStartsAccepting() {
        Assert.assertTrue(this.employeeChecker.canMatch(
                this.withURIColumnSubject.nodeMaker(TripleRelation.SUBJECT)));
        Assert.assertTrue(this.employeeChecker.canMatch(
                this.withURIPatternSubject.nodeMaker(TripleRelation.SUBJECT)));
    }

    @Test
    public void testRuleCheckerUnaffectedByNonURIPattern() {
        this.employeeChecker.addPotentialMatch(this.withURIColumnSubject.nodeMaker(TripleRelation.SUBJECT));
        Assert.assertTrue(this.employeeChecker.canMatch(this.withURIColumnSubject.nodeMaker(TripleRelation.SUBJECT)));
        Assert.assertTrue(this.employeeChecker.canMatch(
                this.withURIPatternSubject.nodeMaker(TripleRelation.SUBJECT)));
    }

    @Test
    public void testRuleCheckerRejectsAfterMatch() {
        this.employeeChecker.addPotentialMatch(this.withURIPatternSubject.nodeMaker(TripleRelation.SUBJECT));
        Assert.assertFalse(this.employeeChecker.canMatch(this.withURIColumnSubject.nodeMaker(TripleRelation.SUBJECT)));
        Assert.assertTrue(this.employeeChecker.canMatch(this.withURIPatternSubject.nodeMaker(TripleRelation.SUBJECT)));
    }

    @Test
    public void testRuleCheckerDoesNotRejectAfterNonMatch() {
        this.foobarChecker.addPotentialMatch(this.withURIPatternSubject.nodeMaker(TripleRelation.SUBJECT));
        Assert.assertTrue(this.foobarChecker.canMatch(this.withURIColumnSubject.nodeMaker(TripleRelation.SUBJECT)));
        Assert.assertTrue(this.foobarChecker.canMatch(this.withURIPatternSubject.nodeMaker(TripleRelation.SUBJECT)));
    }
}
