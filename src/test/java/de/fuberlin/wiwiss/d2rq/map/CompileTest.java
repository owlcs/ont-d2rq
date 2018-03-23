package de.fuberlin.wiwiss.d2rq.map;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Join;
import de.fuberlin.wiwiss.d2rq.algebra.TripleRelation;
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
    private Model model;
    private Mapping mapping;
    private Database database;
    private ClassMap employees;
    private PropertyBridge managerBridge;
    private ClassMap cities;
    private PropertyBridge citiesTypeBridge;
    private PropertyBridge citiesNameBridge;
    private ClassMap countries;
    private PropertyBridge countriesTypeBridge;

    @Before
    public void setUp() {
        this.model = ModelFactory.createDefaultModel();
        this.mapping = MappingFactory.createEmpty();
        this.database = new Database(this.model.createResource());
        database.useConnectedDB(new DummyDB());
        this.mapping.addDatabase(this.database);

        employees = createClassMap("http://test/employee@@e.ID@@");
        employees.addAlias("employees AS e");
        employees.addJoin("e.ID = foo.bar");
        employees.addCondition("e.status = 'active'");
        managerBridge = createPropertyBridge(employees, "http://terms.example.org/manager");
        managerBridge.addAlias("e AS m");
        managerBridge.setRefersToClassMap(this.employees);
        managerBridge.addJoin("e.manager = m.ID");

        cities = createClassMap("http://test/city@@c.ID@@");
        citiesTypeBridge = createPropertyBridge(cities, RDF.type.getURI());
        citiesTypeBridge.setConstantValue(model.createResource("http://terms.example.org/City"));
        citiesNameBridge = createPropertyBridge(cities, "http://terms.example.org/name");
        citiesNameBridge.setColumn("c.name");
        countries = createClassMap("http://test/countries/@@c.country@@");
        countries.setContainsDuplicates(true);
        countriesTypeBridge = createPropertyBridge(countries, RDF.type.getURI());
        countriesTypeBridge.setConstantValue(model.createResource("http://terms.example.org/Country"));
    }

    private ClassMap createClassMap(String uriPattern) {
        ClassMap result = new ClassMap(this.model.createResource());
        result.setDatabase(this.database);
        result.setURIPattern(uriPattern);
        this.mapping.addClassMap(result);
        return result;
    }

    private PropertyBridge createPropertyBridge(ClassMap classMap, String propertyURI) {
        PropertyBridge result = new PropertyBridge(this.model.createResource());
        result.setBelongsToClassMap(classMap);
        result.addProperty(this.model.createProperty(propertyURI));
        classMap.addPropertyBridge(result);
        return result;
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
        Set<String> joinsToString = new HashSet<String>();
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
