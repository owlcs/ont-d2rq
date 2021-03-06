package de.fuberlin.wiwiss.d2rq.algebra;

import java.util.*;


/**
 * A {@link ColumnRenamer} based on a fixed map of
 * original and replacement columns.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
public class ColumnRenamerMap extends ColumnRenamer {
    private Map<Attribute, Attribute> originalsToReplacements;

    public ColumnRenamerMap(Map<Attribute, Attribute> originalsToReplacements) {
        this.originalsToReplacements = originalsToReplacements;
    }

    @Override
    public Attribute applyTo(Attribute original) {
        if (this.originalsToReplacements.containsKey(original)) {
            return this.originalsToReplacements.get(original);
        }
        return original;
    }

    @Override
    public AliasMap applyTo(AliasMap aliases) {
        return aliases;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("ColumnRenamerMap(");
        List<Attribute> columns = new ArrayList<>(this.originalsToReplacements.keySet());
        Collections.sort(columns);
        Iterator<Attribute> it = columns.iterator();
        while (it.hasNext()) {
            Attribute column = it.next();
            result.append(column.qualifiedName());
            result.append(" => ");
            result.append(this.originalsToReplacements.get(column).qualifiedName());
            if (it.hasNext()) {
                result.append(", ");
            }
        }
        result.append(")");
        return result.toString();
    }
}
