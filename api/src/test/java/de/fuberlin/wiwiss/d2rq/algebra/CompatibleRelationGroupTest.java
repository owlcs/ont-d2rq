package de.fuberlin.wiwiss.d2rq.algebra;

import de.fuberlin.wiwiss.d2rq.engine.BindingMaker;
import de.fuberlin.wiwiss.d2rq.expr.Expression;
import de.fuberlin.wiwiss.d2rq.map.impl.RelationBuilder;
import de.fuberlin.wiwiss.d2rq.nodes.NodeMaker;
import de.fuberlin.wiwiss.d2rq.nodes.TypedNodeMaker;
import de.fuberlin.wiwiss.d2rq.sql.DummyDB;
import de.fuberlin.wiwiss.d2rq.sql.SQL;
import de.fuberlin.wiwiss.d2rq.values.Column;
import org.apache.jena.sparql.core.Var;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class CompatibleRelationGroupTest {
    private RelationImpl unique;
    private RelationImpl notUnique;
    private DummyDB db;

    @Before
    public void setUp() {
        db = DummyDB.create();
        Set<ProjectionSpec> projections1 = Collections.singleton(new Attribute(null, "table", "unique"));
        Set<ProjectionSpec> projections2 = Collections.singleton(new Attribute(null, "table", "not_unique"));
        unique = new RelationImpl(
                db, AliasMap.NO_ALIASES, Expression.TRUE, Expression.TRUE,
                Collections.emptySet(),
                projections1, true, OrderSpec.NONE, Relation.NO_LIMIT, Relation.NO_LIMIT);
        notUnique = new RelationImpl(
                db, AliasMap.NO_ALIASES, Expression.TRUE, Expression.TRUE,
                Collections.emptySet(),
                projections2, false, OrderSpec.NONE, Relation.NO_LIMIT, Relation.NO_LIMIT);
    }

    @Test
    public void testNotUniqueIsNotCompatible() {
        CompatibleRelationGroup group;
        group = new CompatibleRelationGroup();
        group.addRelation(unique);
        Assert.assertTrue(group.isCompatible(unique));
        Assert.assertFalse(group.isCompatible(notUnique));
        group = new CompatibleRelationGroup();
        group.addRelation(notUnique);
        Assert.assertFalse(group.isCompatible(unique));
    }

    @Test
    public void testNotUniqueIsCompatibleIfSameAttributes() {
        CompatibleRelationGroup group = new CompatibleRelationGroup();
        group.addRelation(notUnique);
        Assert.assertTrue(group.isCompatible(notUnique));
    }

    @Test
    public void testCombineDifferentConditions() {
        Attribute id = SQL.parseAttribute("TABLE.ID");
        db.setNullable(id, false);
        NodeMaker x = new TypedNodeMaker(TypedNodeMaker.PLAIN_LITERAL, new Column(id), true);
        Map<Var, NodeMaker> map = Collections.singletonMap(Var.alloc("x"), x);
        BindingMaker bm = new BindingMaker(map, null);
        RelationBuilder b1 = new RelationBuilder(db);
        RelationBuilder b2 = new RelationBuilder(db);
        b1.addProjection(id);
        b2.addProjection(id);
        b1.addCondition("TABLE.VALUE=1");
        b2.addCondition("TABLE.VALUE=2");

        CompatibleRelationGroup group = new CompatibleRelationGroup();
        Relation r1 = b1.buildRelation();
        Relation r2 = b2.buildRelation();
        group.addBindingMaker(r1, bm);
        Assert.assertTrue(group.isCompatible(r2));
        group.addBindingMaker(r2, bm);

        Assert.assertEquals(2, group.bindingMakers().size());
        Assert.assertTrue(group.baseRelation().projections().contains(new ExpressionProjectionSpec(r1.condition())));
        Assert.assertTrue(group.baseRelation().projections().contains(new ExpressionProjectionSpec(r2.condition())));
        Assert.assertEquals(3, group.baseRelation().projections().size());
        Assert.assertEquals(r1.condition().or(r2.condition()), group.baseRelation().condition());
        Assert.assertEquals(group.bindingMakers().iterator().next().nodeMaker(Var.alloc("x")), x);
        Assert.assertNotNull(group.bindingMakers().iterator().next().condition());
    }

    @Test
    public void testCombineConditionAndNoCondition() {
        Attribute id = SQL.parseAttribute("TABLE.ID");
        db.setNullable(id, false);
        NodeMaker x = new TypedNodeMaker(TypedNodeMaker.PLAIN_LITERAL, new Column(id), true);
        Map<Var, NodeMaker> map = Collections.singletonMap(Var.alloc("x"), x);
        BindingMaker bm = new BindingMaker(map, null);
        RelationBuilder b1 = new RelationBuilder(db);
        RelationBuilder b2 = new RelationBuilder(db);
        b1.addProjection(id);
        b2.addProjection(id);
        b1.addCondition("TABLE.VALUE=1");

        CompatibleRelationGroup group = new CompatibleRelationGroup();
        Relation r1 = b1.buildRelation();
        Relation r2 = b2.buildRelation();
        group.addBindingMaker(r1, bm);
        Assert.assertTrue(group.isCompatible(r2));
        group.addBindingMaker(r2, bm);

        Assert.assertEquals(2, group.bindingMakers().size());
        Assert.assertTrue(group.baseRelation().projections().contains(new ExpressionProjectionSpec(r1.condition())));
        Assert.assertEquals(2, group.baseRelation().projections().size());
        Assert.assertEquals(Expression.TRUE, group.baseRelation().condition());
        Iterator<BindingMaker> it = group.bindingMakers().iterator();
        BindingMaker bm3 = it.next();
        BindingMaker bm4 = it.next();
        Assert.assertTrue((bm3.condition() == null && bm4.condition() != null) || (bm3.condition() != null && bm4.condition() == null));
    }
}
