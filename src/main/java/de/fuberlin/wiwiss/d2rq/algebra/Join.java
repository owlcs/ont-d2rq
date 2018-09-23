package de.fuberlin.wiwiss.d2rq.algebra;

import java.util.*;

/**
 * Represents an SQL join between two tables, spanning one or more columns.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class Join {
    private List<Attribute> attributes1;
    private List<Attribute> attributes2;
    private RelationName table1;
    private RelationName table2;
    private Map<Attribute, Attribute> otherSide = new HashMap<>(4);

    private int joinDirection;

    public static final int DIRECTION_UNDIRECTED = 0;
    public static final int DIRECTION_LEFT = 1;
    public static final int DIRECTION_RIGHT = 2;

    public static final String[] joinOperators = {"=", "<=", "=>"};

    public Join(Attribute oneSide, Attribute otherSide, int joinDirection) {
        this(Collections.singletonList(oneSide), Collections.singletonList(otherSide), joinDirection);
    }

    public Join(List<Attribute> oneSideAttributes, List<Attribute> otherSideAttributes, int joinDirection) {
        RelationName oneRelation = oneSideAttributes.get(0).relationName();
        RelationName otherRelation = otherSideAttributes.get(0).relationName();
        this.attributes1 = oneSideAttributes;
        this.attributes2 = otherSideAttributes;
        this.table1 = oneRelation;
        this.table2 = otherRelation;
        this.joinDirection = joinDirection;
        for (int i = 0; i < this.attributes1.size(); i++) {
            Attribute a1 = this.attributes1.get(i);
            Attribute a2 = this.attributes2.get(i);
            this.otherSide.put(a1, a2);
            this.otherSide.put(a2, a1);
        }
    }

    public boolean isSameTable() {
        return this.table1.equals(this.table2);
    }

    public boolean containsColumn(Attribute column) {
        return this.attributes1.contains(column) ||
                this.attributes2.contains(column);
    }

    public RelationName table1() {
        return this.table1;
    }

    public RelationName table2() {
        return this.table2;
    }

    public List<Attribute> attributes1() {
        return this.attributes1;
    }

    public List<Attribute> attributes2() {
        return this.attributes2;
    }

    public int joinDirection() {
        return this.joinDirection;
    }

    public Attribute equalAttribute(Attribute column) {
        return this.otherSide.get(column);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Join(");
        Iterator<Attribute> it = this.attributes1.iterator();
        while (it.hasNext()) {
            Attribute attribute = it.next();
            result.append(attribute.qualifiedName());
            if (it.hasNext()) {
                result.append(", ");
            }
        }
        result.append(joinDirection == DIRECTION_UNDIRECTED ? " <=> " : (joinDirection == DIRECTION_RIGHT ? " => " : " <= "));
        it = this.attributes2.iterator();
        while (it.hasNext()) {
            Attribute attribute = it.next();
            result.append(attribute.qualifiedName());
            if (it.hasNext()) {
                result.append(", ");
            }
        }
        result.append(")");
        return result.toString();
    }

    @Override
    public int hashCode() {
        switch (this.joinDirection) {
            case DIRECTION_RIGHT:
                return 31 * (this.attributes1.hashCode() ^ this.attributes2.hashCode());
            case DIRECTION_LEFT:
                return 31 * (this.attributes2.hashCode() ^ this.attributes1.hashCode());
            case DIRECTION_UNDIRECTED:
            default:
                return 31 * (this.attributes1.hashCode() ^ this.attributes2.hashCode()) + 1;
        }
    }

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) return true;
        if (!(otherObject instanceof Join)) {
            return false;
        }
        Join otherJoin = (Join) otherObject;
        return
                (this.attributes1.equals(otherJoin.attributes1)
                        && this.attributes2.equals(otherJoin.attributes2) && this.joinDirection == otherJoin.joinDirection())
                        ||
                        (this.attributes1.equals(otherJoin.attributes2)
                                && this.attributes2.equals(otherJoin.attributes1)
                                && this.joinDirection == (otherJoin.joinDirection() == DIRECTION_UNDIRECTED ? DIRECTION_UNDIRECTED :
                                (otherJoin.joinDirection() == DIRECTION_LEFT ? DIRECTION_RIGHT : DIRECTION_LEFT)));
    }

    public Join renameColumns(ColumnRenamer columnRenamer) {
        List<Attribute> oneSide = new ArrayList<>();
        List<Attribute> otherSide = new ArrayList<>();
        for (Attribute column : attributes1) {
            oneSide.add(columnRenamer.applyTo(column));
            otherSide.add(columnRenamer.applyTo(equalAttribute(column)));
        }
        return new Join(oneSide, otherSide, joinDirection);
    }
}
