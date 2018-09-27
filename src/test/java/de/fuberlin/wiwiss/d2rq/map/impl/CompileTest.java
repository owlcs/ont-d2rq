package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Join;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CompileTest {
    private PropertyBridgeImpl managerBridge;
    private PropertyBridgeImpl citiesTypeBridge;
    private PropertyBridgeImpl citiesNameBridge;
    private PropertyBridgeImpl countriesTypeBridge;

    @Before
    public void setUp() {
        Model model = ModelFactory.createDefaultModel();
        Mapping mapping = MappingFactory.createEmpty();
        Database database = mapping.createDatabase(model.createResource());
        database.useConnectedDB(new DummyDB());
        mapping.addDatabase(database);

        ClassMap employees = createClassMap(mapping, database, "http://test/employee@@e.ID@@");
        employees.addAlias("employees AS e");
        employees.addJoin("e.ID = foo.bar");
        employees.addCondition("e.status = 'active'");
        managerBridge = createPropertyBridge(mapping, employees, "http://terms.example.org/manager");
        managerBridge.addAlias("e AS m");
        managerBridge.setRefersToClassMap(employees);
        managerBridge.addJoin("e.manager = m.ID");

        ClassMap cities = createClassMap(mapping, database, "http://test/city@@c.ID@@");
        citiesTypeBridge = createPropertyBridge(mapping, cities, RDF.type.getURI());
        citiesTypeBridge.setConstantValue(model.createResource("http://terms.example.org/City"));
        citiesNameBridge = createPropertyBridge(mapping, cities, "http://terms.example.org/name");
        citiesNameBridge.setColumn("c.name");
        ClassMap countries = createClassMap(mapping, database, "http://test/countries/@@c.country@@");
        countries.setContainsDuplicates(true);
        countriesTypeBridge = createPropertyBridge(mapping, countries, RDF.type.getURI());
        countriesTypeBridge.setConstantValue(model.createResource("http://terms.example.org/Country"));
    }

    private static ClassMap createClassMap(Mapping mapping, Database database, String uriPattern) {
        ClassMap result = mapping.createClassMap(mapping.asModel().createResource());
        result.setDatabase(database);
        result.setURIPattern(uriPattern);
        mapping.addClassMap(result);
        return result;
    }

    private static PropertyBridgeImpl createPropertyBridge(Mapping mapping, ClassMap classMap, String propertyURI) {
        Model model = classMap.asResource().getModel();
        PropertyBridge res = mapping.createPropertyBridge(model.createResource());
        res.setBelongsToClassMap(classMap);
        res.addProperty(model.createProperty(propertyURI));
        classMap.addPropertyBridge(res);
        return (PropertyBridgeImpl) res;
    }

    @Test
    public void testAttributesInRefersToClassMapAreRenamed() {
        TripleRelation relation = this.managerBridge.toTripleRelations().iterator().next();
        Assert.assertEquals("URI(Pattern(http://test/employee@@e.ID@@))",
                relation.nodeMaker(TripleRelation.SUBJECT).toString());
        Assert.assertEquals("URI(Pattern(http://test/employee@@m.ID@@))", relation.nodeMaker(TripleRelation.OBJECT).toString());
    }

    @Test
    public void testJoinConditionsInRefersToClassMapAreRenamed() {
        TripleRelation relation = this.managerBridge.toTripleRelations().iterator().next();
        Set<String> joinsToString = new HashSet<>();
        for (Join join : relation.baseRelation().joinConditions()) {
            joinsToString.add(join.toString());
        }
        Assert.assertEquals(new HashSet<>(Arrays.asList("Join(e.manager <=> m.ID)", "Join(m.ID <=> foo.bar)",
                "Join(e.ID <=> foo.bar)")),
                joinsToString);
    }

    @Test
    public void testConditionInRefersToClassMapIsRenamed() {
        TripleRelation relation = this.managerBridge.toTripleRelations().iterator().next();
        Assert.assertEquals("Conjunction(SQL(e.status = 'active'), SQL(m.status = 'active'))",
                relation.baseRelation().condition().toString());
    }

    @Test
    public void testAliasesInRefersToClassMapAreRenamed() {
        TripleRelation relation = this.managerBridge.toTripleRelations().iterator().next();
        Assert.assertEquals(new AliasMap(Arrays.asList(SQL.parseAlias("employees AS e"),
                SQL.parseAlias("employees AS m"))),
                relation.baseRelation().aliases());
    }

    @Test
    public void testSimpleTypeBridgeContainsNoDuplicates() {
        Assert.assertTrue(this.citiesTypeBridge.buildRelation().isUnique());
    }

    @Test
    public void testSimpleColumnBridgeContainsNoDuplicates() {
        Assert.assertTrue(this.citiesNameBridge.buildRelation().isUnique());
    }

    @Test
    public void testBridgeWithDuplicateClassMapContainsDuplicates() {
        Assert.assertFalse(this.countriesTypeBridge.buildRelation().isUnique());
    }
}
