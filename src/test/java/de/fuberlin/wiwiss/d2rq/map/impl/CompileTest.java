package de.fuberlin.wiwiss.d2rq.map.impl;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Join;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
import de.fuberlin.wiwiss.d2rq.map.*;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
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
        Mapping mapping = MappingFactory.create();
        Database database = mapping.createDatabase(null);
        database.useConnectedDB(new DummyDB());

        ClassMap employees = createClassMap(database, "http://test/employee@@e.ID@@")
                .addAlias("employees AS e")
                .addJoin("e.ID = foo.bar")
                .addCondition("e.status = 'active'");
        managerBridge = createPropertyBridge(employees, "http://terms.example.org/manager");
        managerBridge.addAlias("e AS m");
        managerBridge.setRefersToClassMap(employees);
        managerBridge.addJoin("e.manager = m.ID");

        ClassMap cities = createClassMap(database, "http://test/city@@c.ID@@");
        citiesTypeBridge = createPropertyBridge(cities, RDF.type.getURI())
                .setConstantValue("http://terms.example.org/City");
        citiesNameBridge = createPropertyBridge(cities, "http://terms.example.org/name");
        citiesNameBridge.setColumn("c.name");
        ClassMap countries = createClassMap(database, "http://test/countries/@@c.country@@");
        countries.setContainsDuplicates(true);
        countriesTypeBridge = createPropertyBridge(countries, RDF.type.getURI())
                .setConstantValue("http://terms.example.org/Country");
    }

    private static ClassMap createClassMap(Database database, String uriPattern) {
        Mapping mapping = database.getMapping();
        ClassMap result = mapping.createClassMap(null).setDatabase(database).setURIPattern(uriPattern);
        mapping.addClassMap(result);
        return result;
    }

    private static PropertyBridgeImpl createPropertyBridge(ClassMap classMap, String propertyURI) {
        Mapping mapping = classMap.getMapping();
        PropertyBridge res = mapping.createPropertyBridge(null)
                .setBelongsToClassMap(classMap)
                .addProperty(propertyURI);
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
