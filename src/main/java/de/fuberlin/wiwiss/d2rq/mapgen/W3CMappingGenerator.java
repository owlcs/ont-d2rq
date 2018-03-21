package de.fuberlin.wiwiss.d2rq.mapgen;

import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import org.apache.jena.rdf.model.Resource;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a D2RQ mapping compatible with W3C's Direct Mapping by introspecting a database schema.
 * Result is available as a high-quality Turtle serialization, or
 * as a parsed model.
 *
 * @author Lu&iacute;s Eufrasio (luis.eufrasio@gmail.com)
 */
public class W3CMappingGenerator extends MappingGenerator {

    public W3CMappingGenerator(ConnectedDB database) {
        super(database);
        setGenerateLabelBridges(false);
        setHandleLinkTables(false);
        setGenerateDefinitionLabels(false);
        setServeVocabulary(false);
        setSkipForeignKeyTargetColumns(false);
    }

    @Override
    protected void writeEntityIdentifier(Resource table, RelationName tableName, List<Attribute> identifierColumns) {
        StringBuilder uriPattern = new StringBuilder(instanceNamespaceURI + encodeTableName(tableName));
        Iterator<Attribute> it = identifierColumns.iterator();
        int i = 0;
        while (it.hasNext()) {
            uriPattern.append(i == 0 ? "/" : ";");
            i++;
            Attribute column = it.next();
            uriPattern.append(encodeColumnName(column)).append("=@@").append(column.qualifiedName());
            if (!database.columnType(column).isIRISafe()) {
                uriPattern.append("|encode");
            }
            uriPattern.append("@@");
        }
        table.addLiteral(D2RQ.uriPattern, uriPattern.toString());
    }

    @Override
    protected void writePseudoEntityIdentifier(Resource table, RelationName tableName) {
        List<Attribute> usedColumns = filter(table, database.schemaInspector().listColumns(tableName), true, "pseudo identifier column");
        String msg = String.valueOf(usedColumns.stream().map(Attribute::qualifiedName).collect(Collectors.toList()))
                .replaceAll("^\\[", "").replaceAll("]$", "");
        table.addLiteral(D2RQ.bNodeIdColumns, msg);
    }

    @Override
    protected String vocabularyIRITurtle(RelationName table) {
        return vocabNamespaceURI + encodeTableName(table);
    }

    @Override
    protected String vocabularyIRITurtle(Attribute attribute) {
        return vocabNamespaceURI + encodeTableName(attribute.relationName()) + "#" + encodeColumnName(attribute);
    }

    @Override
    protected String vocabularyIRITurtle(List<Attribute> attributes) {
        StringBuilder result = new StringBuilder();
        result.append(vocabNamespaceURI);
        result.append(encodeTableName(attributes.get(0).relationName()));
        int i = 1;
        for (Attribute column : attributes) {
            String attributeName = encodeColumnName(column);
            if (i == 1) {
                result.append("#ref-");
                result.append(attributeName);
            } else {
                result.append(";").append(attributeName);
            }
            i++;
        }
        return result.toString();
    }

    private String encodeTableName(RelationName tableName) {
        return (tableName.schemaName() == null ? "" : IRIEncoder.encode(tableName.schemaName()) + '/')
                + IRIEncoder.encode(tableName.tableName());
    }

    private String encodeColumnName(Attribute column) {
        return IRIEncoder.encode(column.attributeName());
    }
}