package de.fuberlin.wiwiss.d2rq.mapgen;

import de.fuberlin.wiwiss.d2rq.algebra.AliasMap;
import de.fuberlin.wiwiss.d2rq.algebra.Attribute;
import de.fuberlin.wiwiss.d2rq.algebra.Join;
import de.fuberlin.wiwiss.d2rq.algebra.RelationName;
import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.map.MappingFactory;
import de.fuberlin.wiwiss.d2rq.sql.ConnectedDB;
import de.fuberlin.wiwiss.d2rq.sql.types.DataType;
import de.fuberlin.wiwiss.d2rq.vocab.D2RQ;
import de.fuberlin.wiwiss.d2rq.vocab.JDBC;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates a D2RQ mapping by introspecting a database schema.
 * Result is available as a high-quality Turtle serialization, or as a parsed model.
 *
 * @author Richard Cyganiak (richard@cyganiak.de)
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue"})
public class MappingGenerator {
    public final static Logger LOGGER = LoggerFactory.getLogger(MappingGenerator.class);

    protected final ConnectedDB database;

    protected URI instanceNamespaceURI;
    protected URI mapNamespaceURI;
    protected URI vocabNamespaceURI;

    protected String driverClass;
    protected Filter filter = Filter.ALL;

    protected boolean generateClasses = true;
    protected boolean generateLabelBridges = true;
    protected boolean generateDefinitionLabels = true;
    protected boolean handleLinkTables = true;
    protected boolean serveVocabulary = true;
    protected boolean skipForeignKeyTargetColumns = true;

    protected URI startupSQLScript;

    public static final String DEFAULT_MAP_NS = "map#";
    public static final String DEFAULT_DB_NS = "";
    public static final String DEFAULT_SCHEMA_NS = "vocab/";

    private Map<String, Object> assignedNames = new HashMap<>();

    public MappingGenerator(ConnectedDB database) {
        this.database = Objects.requireNonNull(database, "Null database.");
        setJDBCDriverClass(ConnectedDB.guessJDBCDriverClass(database.getJdbcURL()));
        setMapNamespaceURI(DEFAULT_MAP_NS);
        setInstanceNamespaceURI(DEFAULT_DB_NS);
        setVocabNamespaceURI(DEFAULT_SCHEMA_NS);
    }

    public void setMapNamespaceURI(String uri) {
        this.mapNamespaceURI = toURI(uri, "Incorrect map ns.");
    }

    public void setInstanceNamespaceURI(String uri) {
        this.instanceNamespaceURI = toURI(uri, "Incorrect db-instance ns.");
    }

    public void setVocabNamespaceURI(String uri) {
        this.vocabNamespaceURI = toURI(uri, "Incorrect db-schema ns.");
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public void setJDBCDriverClass(String driverClassName) {
        this.driverClass = Objects.requireNonNull(driverClassName, "Null driver class");
    }

    public void setStartupSQLScript(URI uri) {
        startupSQLScript = uri;
    }

    /**
     * @param flag Generate an rdfs:label property bridge based on the PK?
     */
    public void setGenerateLabelBridges(boolean flag) {
        this.generateLabelBridges = flag;
    }

    /**
     * @param flag Generate a d2rq:class for every class map?
     */
    public void setGenerateClasses(boolean flag) {
        this.generateClasses = flag;
    }

    /**
     * @param flag Handle Link Tables as properties (true) or normal tables (false)
     */
    public void setHandleLinkTables(boolean flag) {
        this.handleLinkTables = flag;
    }

    /**
     * @param flag Generate ClassDefinitionLabels and PropertyDefinitionLabels?
     */
    public void setGenerateDefinitionLabels(boolean flag) {
        this.generateDefinitionLabels = flag;
    }

    /**
     * @param flag Value for d2rq:serveVocabulary in map:Configuration
     */
    public void setServeVocabulary(boolean flag) {
        this.serveVocabulary = flag;
    }

    public void setSkipForeignKeyTargetColumns(boolean flag) {
        skipForeignKeyTargetColumns = flag;
    }

    public void copy(MappingGenerator other) {
        setVocabNamespaceURI(other.vocabNamespaceURI.toString());
        setInstanceNamespaceURI(other.instanceNamespaceURI.toString());
        setMapNamespaceURI(other.mapNamespaceURI.toString());
        setFilter(other.filter);
        setStartupSQLScript(other.startupSQLScript);
        setJDBCDriverClass(other.driverClass);
        setGenerateClasses(other.generateClasses);
        setGenerateLabelBridges(other.generateLabelBridges);
        setGenerateDefinitionLabels(other.generateDefinitionLabels);
        setHandleLinkTables(other.handleLinkTables);
        setServeVocabulary(other.serveVocabulary);
        setSkipForeignKeyTargetColumns(other.skipForeignKeyTargetColumns);
    }

    /**
     * Returns an in-memory Jena model containing the D2RQ mapping.
     *
     * @param baseURI Base URI for resolving relative URIs in the mapping, e.g., map namespace
     * @return In-memory Jena model containing the D2RQ mapping
     */
    public Model mappingModel(String baseURI) {
        if (baseURI == null || baseURI.isEmpty()) {
            return createMappingModel();
        }
        URI base = URI.create(baseURI);
        if (!base.isAbsolute()) {
            throw new IllegalArgumentException("Not absolute: <" + base + ">");
        }
        if (mapNamespaceURI.isAbsolute() && vocabNamespaceURI.isAbsolute() && instanceNamespaceURI.isAbsolute()) {
            LOGGER.warn("There are no relative URIs. The base URI <" + base + "> is ignored.");
            return createMappingModel();
        }
        LOGGER.info("The base URI is <" + base + ">.");
        MappingGenerator copy = new MappingGenerator(database);
        copy.copy(this);
        if (!copy.mapNamespaceURI.isAbsolute()) {
            copy.setMapNamespaceURI(composeURI(base, copy.mapNamespaceURI).toString());
        }
        if (!copy.instanceNamespaceURI.isAbsolute()) {
            copy.setInstanceNamespaceURI(composeURI(base, copy.instanceNamespaceURI).toString());
        }
        if (!copy.vocabNamespaceURI.isAbsolute()) {
            copy.setVocabNamespaceURI(composeURI(base, copy.vocabNamespaceURI).toString());
        }
        return copy.createMappingModel();
    }

    private static URI composeURI(URI base, URI part) {
        String res = trimEndSymbol(base.toString()) + "/" + trimEndSymbol(part.toString());
        return URI.create(trimEndSymbol(res) + '#');
    }

    private static String trimEndSymbol(String s) {
        return s.replaceAll("([/#])+$", "");
    }

    protected Model createMappingModel() {
        try {
            Model res = ModelFactory.createDefaultModel();
            res.setNsPrefixes(MappingFactory.MAPPING);
            res.setNsPrefix(MappingFactory.MAP_PREFIX, mapNamespaceURI.toString());
            res.setNsPrefix(MappingFactory.VOCAB_PREFIX, vocabNamespaceURI.toString());
            if (!serveVocabulary) {
                addConfiguration(res);
            }
            Resource db = addDatabase(res);
            List<RelationName> tableNames = new ArrayList<>();
            for (RelationName tableName : database.schemaInspector().listTableNames(filter.getSingleSchema())) {
                if (!filter.matches(tableName)) {
                    LOGGER.info("Skipping table <" + tableName + ">");
                    continue;
                }
                tableNames.add(tableName);
            }
            LOGGER.info("Filter '" + filter + "' matches " + tableNames.size() + " total tables");
            for (RelationName tableName : tableNames) {
                if (handleLinkTables && isLinkTable(tableName)) {
                    addLinkTable(res, tableName);
                } else {
                    addTable(res, db, tableName);
                }
            }
            LOGGER.info("Done!");
            return res;
        } finally {
            assignedNames.clear();
        }
    }

    protected Resource addConfiguration(Model model) {
        LOGGER.info("Generating d2rq:Configuration instance");
        Resource res = model.createResource(mapNamespaceURI + "Configuration", D2RQ.Configuration);
        res.addProperty(D2RQ.serveVocabulary, ResourceFactory.createTypedLiteral(false));
        return res;
    }

    protected Resource addDatabase(Model model) {
        LOGGER.info("Generating d2rq:Database instance");
        Resource res = model.createResource(mapNamespaceURI + "database", D2RQ.Database);
        res.addLiteral(D2RQ.jdbcDriver, driverClass);
        res.addLiteral(D2RQ.jdbcDSN, database.getJdbcURL());
        if (database.getUsername() != null) {
            res.addLiteral(D2RQ.username, database.getUsername());
        }
        if (database.getPassword() != null) {
            res.addLiteral(D2RQ.password, database.getPassword());
        }
        if (startupSQLScript != null) {
            res.addProperty(D2RQ.startupSQLScript, ResourceFactory.createResource(startupSQLScript.toString()));
        }
        Properties props = database.vendor().getDefaultConnectionProperties();
        for (Object property : props.keySet()) {
            String value = props.getProperty((String) property);
            res.addLiteral(ResourceFactory.createProperty(JDBC.getURI() + property), value);
        }
        return res;
    }

    protected Resource addTable(Model model, Resource databaseResource, RelationName tableName) {
        LOGGER.info("Generating d2rq:ClassMap instance for table " + tableName.qualifiedName());
        Resource res = model.createResource(classMapIRITurtle(tableName), D2RQ.ClassMap);
        res.addProperty(D2RQ.dataStorage, databaseResource);

        List<Attribute> identifierColumns = identifierColumns(res, tableName);
        if (identifierColumns.isEmpty()) {
            writePseudoEntityIdentifier(res, tableName);
        } else {
            writeEntityIdentifier(res, tableName, identifierColumns);
        }

        if (generateClasses) {
            res.addProperty(D2RQ.clazz, ResourceFactory.createResource(vocabularyIRITurtle(tableName)));
            if (generateDefinitionLabels) {
                res.addLiteral(D2RQ.classDefinitionLabel, tableName.qualifiedName());
            }
        }
        if (generateLabelBridges && !identifierColumns.isEmpty()) {
            addLabelBridge(res, tableName, identifierColumns);
        }
        List<Join> foreignKeys = database.schemaInspector().foreignKeys(tableName, DatabaseSchemaInspector.KEYS_IMPORTED);
        for (Attribute column : filter(res, database.schemaInspector().listColumns(tableName), false, "property bridge")) {
            if (skipForeignKeyTargetColumns && isInForeignKey(column, foreignKeys)) continue;
            addColumn(res, column);
        }
        for (Join fk : foreignKeys) {
            if (!filter.matches(fk.table1()) || !filter.matches(fk.table2()) || !filter.matchesAll(fk.attributes1()) || !filter.matchesAll(fk.attributes2())) {
                LOGGER.info("Skipping foreign key: " + fk);
                continue;
            }
            addForeignKey(model, fk);
        }
        return res;
    }

    protected Resource addLinkTable(Model model, RelationName linkTableName) {
        List<Join> keys = database.schemaInspector().foreignKeys(linkTableName, DatabaseSchemaInspector.KEYS_IMPORTED);
        Join join1 = keys.get(0);
        Join join2 = keys.get(1);
        if (!filter.matches(join1.table1()) || !filter.matches(join1.table2()) ||
                !filter.matchesAll(join1.attributes1()) || !filter.matchesAll(join1.attributes2()) ||
                !filter.matches(join2.table1()) || !filter.matches(join2.table2()) ||
                !filter.matchesAll(join2.attributes1()) || !filter.matchesAll(join2.attributes2())) {
            LOGGER.info("Skipping link table " + linkTableName);
            return null;
        }
        LOGGER.info("Generating d2rq:PropertyBridge instance for table " + linkTableName.qualifiedName());
        RelationName table1 = database.schemaInspector().getCorrectCapitalization(join1.table2());
        RelationName table2 = database.schemaInspector().getCorrectCapitalization(join2.table2());
        boolean isSelfJoin = table1.equals(table2);
        LOGGER.debug("# Table " + linkTableName + (isSelfJoin ? " (n:m self-join)" : " (n:m)"));
        Resource res = model.createResource(propertyBridgeIRITurtle(linkTableName, "link"), D2RQ.PropertyBridge);
        res.addProperty(D2RQ.belongsToClassMap, model.getResource(classMapIRITurtle(table1)));
        res.addProperty(D2RQ.property, model.getResource(vocabularyIRITurtle(linkTableName)));
        res.addProperty(D2RQ.refersToClassMap, model.getResource(classMapIRITurtle(table2)));
        if (generateDefinitionLabels) { // new:
            res.addLiteral(D2RQ.propertyDefinitionLabel, toLabel(join1.attributes1()));
            res.addLiteral(D2RQ.propertyDefinitionLabel, toLabel(join2.attributes1()));
        }
        for (Attribute column : join1.attributes1()) {
            Attribute otherColumn = join1.equalAttribute(column);
            String join = column.qualifiedName() + " " + Join.joinOperators[join1.joinDirection()] + " " + otherColumn.qualifiedName();
            res.addLiteral(D2RQ.join, join);
        }
        AliasMap alias = AliasMap.NO_ALIASES;
        if (isSelfJoin) {
            RelationName aliasName = new RelationName(null, table2.tableName() + "_" + linkTableName.tableName() + "__alias");
            alias = AliasMap.create1(table2, aliasName);
            String join = table2.qualifiedName() + " AS " + aliasName.qualifiedName();
            res.addLiteral(D2RQ.join, join);
        }
        for (Attribute column : join2.attributes1()) {
            Attribute otherColumn = join2.equalAttribute(column);
            String join = column.qualifiedName() + " " + Join.joinOperators[join2.joinDirection()] + " " + alias.applyTo(otherColumn).qualifiedName();
            res.addLiteral(D2RQ.join, join);
        }
        return res;
    }

    protected void writeEntityIdentifier(Resource table, RelationName tableName, List<Attribute> identifierColumns) {
        StringBuilder uriPattern = new StringBuilder(this.instanceNamespaceURI.toString());
        if (tableName.schemaName() != null) {
            uriPattern.append(IRIEncoder.encode(tableName.schemaName())).append("/");
        }
        uriPattern.append(IRIEncoder.encode(tableName.tableName()));
        for (Attribute column : identifierColumns) {
            uriPattern.append("/@@").append(column.qualifiedName());
            if (!database.schemaInspector().columnType(column).isIRISafe()) {
                uriPattern.append("|urlify");
            }
            uriPattern.append("@@");
        }
        table.addLiteral(D2RQ.uriPattern, uriPattern.toString());
    }

    protected void writePseudoEntityIdentifier(Resource table, RelationName tableName) {
        writeWarning(table, "Sorry, I don't know which columns to put into the uriPattern" +
                "\n\tfor \"" + tableName + "\" because the table doesn't have a primary key." +
                "\n\tPlease specify it manually.");
        writeEntityIdentifier(table, tableName, Collections.emptyList());
    }

    protected Resource addLabelBridge(Resource table, RelationName tableName, List<Attribute> labelColumns) {
        Resource res = table.getModel().createResource(propertyBridgeIRITurtle(tableName, "label"), D2RQ.PropertyBridge);
        res.addProperty(D2RQ.belongsToClassMap, table);
        res.addProperty(D2RQ.property, RDFS.label);
        res.addLiteral(D2RQ.pattern, labelPattern(tableName.tableName(), labelColumns));
        return res;
    }

    protected Resource addColumn(Resource table, Attribute column) {
        Model m = table.getModel();
        Resource res = m.createResource(propertyBridgeIRITurtle(column), D2RQ.PropertyBridge);
        res.addProperty(D2RQ.belongsToClassMap, table);
        res.addProperty(D2RQ.property, ResourceFactory.createProperty(vocabularyIRITurtle(column)));
        if (generateDefinitionLabels) {
            res.addLiteral(D2RQ.propertyDefinitionLabel, toLabel(column));
        }
        res.addLiteral(D2RQ.column, column.qualifiedName());
        DataType colType = database.schemaInspector().columnType(column);
        String xsd = colType.rdfType();
        if (xsd != null && !DataType.XSD_STRING.equals(xsd)) {
            // We use plain literals instead of xsd:strings, so skip
            // this if it's an xsd:string
            RDFDatatype dt = TypeMapper.getInstance().getSafeTypeByName(m.expandPrefix(xsd));
            res.addProperty(D2RQ.datatype, m.getResource(dt.getURI()));
        }
        if (colType.valueRegex() != null) {
            res.addLiteral(D2RQ.valueRegex, colType.valueRegex());
        }
        return res;
    }

    protected Resource addForeignKey(Model model, Join foreignKey) {
        RelationName primaryTable = database.schemaInspector().getCorrectCapitalization(foreignKey.table1());
        List<Attribute> primaryColumns = foreignKey.attributes1();
        RelationName foreignTable = database.schemaInspector().getCorrectCapitalization(foreignKey.table2());
        Resource res = model.createResource(propertyBridgeIRITurtle(primaryColumns), D2RQ.PropertyBridge);
        Resource table1 = model.getResource(classMapIRITurtle(primaryTable));
        Resource table2 = model.getResource(classMapIRITurtle(foreignTable));
        Resource prop = model.getResource(vocabularyIRITurtle(primaryColumns));
        res.addProperty(D2RQ.belongsToClassMap, table1);
        res.addProperty(D2RQ.property, prop);
        res.addProperty(D2RQ.refersToClassMap, table2);
        if (generateDefinitionLabels) { // new:
            res.addLiteral(D2RQ.propertyDefinitionLabel, toLabel(primaryColumns));
        }
        AliasMap alias = AliasMap.NO_ALIASES;
        // Same-table join? Then we need to set up an alias for the table and join to that
        if (foreignKey.isSameTable()) {
            String aliasName = foreignTable.qualifiedName().replace('.', '_') + "__alias";
            res.addLiteral(D2RQ.alias, foreignTable.qualifiedName() + " AS " + aliasName);
            alias = AliasMap.create1(foreignTable, new RelationName(null, aliasName));
        }
        for (Attribute column : primaryColumns) {
            String join = column.qualifiedName() + " " + Join.joinOperators[foreignKey.joinDirection()] + " " +
                    alias.applyTo(foreignKey.equalAttribute(column)).qualifiedName();
            res.addLiteral(D2RQ.join, join);
        }
        return res;
    }

    protected void writeWarning(Resource parent, String msg) {
        LOGGER.warn(msg);
        parent.addLiteral(D2RQ.warning, msg);
    }

    protected List<Attribute> identifierColumns(Resource table, RelationName tableName) {
        List<Attribute> columns = database.schemaInspector().primaryKeyColumns(tableName);
        if (filter.matchesAll(columns)) {
            return filter(table, columns, true, "identifier column");
        }
        return Collections.emptyList();
    }

    protected List<Attribute> filter(Resource table, List<Attribute> columns, boolean requireDistinct, String reason) {
        List<Attribute> result = new ArrayList<>(columns.size());
        for (Attribute column : columns) {
            if (!filter.matches(column)) {
                LOGGER.info("Skipping filtered column " + column + " as " + reason);
                continue;
            }
            DataType type = database.schemaInspector().columnType(column);
            if (type == null) {
                writeWarning(table, "Skipping column " + column + " as " + reason + "." +
                        "\n\tIts datatype is unknown to D2RQ." +
                        "\n\tYou can override the column's datatype using d2rq:xxxColumn and add a property bridge.");
                continue;
            }
            if (type.isUnsupported()) {
                writeWarning(table, "Skipping column " + column + " as " + reason + "." +
                        "\n\tIts datatype " + database.schemaInspector().columnType(column) + " cannot be mapped to RDF.");
                continue;
            }
            if (requireDistinct && !type.supportsDistinct()) {
                writeWarning(table, "Skipping column " + column + " as " + reason + "." +
                        "\n\tIts datatype " + database.schemaInspector().columnType(column) + " does not support DISTINCT.");
            }
            result.add(column);
        }
        return result;
    }

    /**
     * Returns SCHEMA_TABLE. Except if that string is already taken
     * by another table name (or column name); in that case we add
     * more underscores until we have no clash.
     */
    private String toUniqueString(RelationName table) {
        if (table.schemaName() == null) {
            return table.tableName();
        }
        StringBuilder separator = new StringBuilder("_");
        while (true) {
            String candidate = table.schemaName() + separator + table.tableName();
            if (!assignedNames.containsKey(candidate)) {
                assignedNames.put(candidate, table);
                return candidate;
            }
            if (assignedNames.get(candidate).equals(table)) {
                return candidate;
            }
            separator.append("_");
        }
    }

    /**
     * Returns TABLE_COLUMN. Except if that string is already taken by
     * another column name (e.g., AAA.BBB_CCC and AAA_BBB.CCC would
     * result in the same result AAA_BBB_CCC); in that case we add more
     * underscores (AAA__BBB_CCC) until we have no clash.
     */
    private String toUniqueString(Attribute column) {
        StringBuilder separator = new StringBuilder("_");
        while (true) {
            String candidate = toUniqueString(column.relationName()) + separator + column.attributeName();
            if (!assignedNames.containsKey(candidate)) {
                assignedNames.put(candidate, column);
                return candidate;
            }
            if (assignedNames.get(candidate).equals(column)) {
                return candidate;
            }
            separator.append("_");
        }
    }

    private String toUniqueString(List<Attribute> columns) {
        StringBuilder result = new StringBuilder();
        result.append(toUniqueString(columns.get(0).relationName()));
        for (Attribute column : columns) {
            result.append("_").append(column.attributeName());
        }
        return result.toString();
    }

    private String classMapIRITurtle(RelationName tableName) {
        return mapNamespaceURI + IRIEncoder.encode(toUniqueString(tableName));
    }

    private String propertyBridgeIRITurtle(RelationName tableName, String suffix) {
        return mapNamespaceURI + IRIEncoder.encode(toUniqueString(tableName) + "__" + suffix);
    }

    private String propertyBridgeIRITurtle(Attribute attribute) {
        return mapNamespaceURI + IRIEncoder.encode(toUniqueString(attribute));
    }

    private String propertyBridgeIRITurtle(List<Attribute> attributes) {
        return mapNamespaceURI + IRIEncoder.encode(toUniqueString(attributes) + "__ref");
    }

    protected String vocabularyIRITurtle(RelationName tableName) {
        return vocabNamespaceURI + IRIEncoder.encode(toUniqueString(tableName));
    }

    protected String vocabularyIRITurtle(Attribute attribute) {
        return vocabNamespaceURI + IRIEncoder.encode(toUniqueString(attribute));
    }

    protected String vocabularyIRITurtle(List<Attribute> attributes) {
        return vocabNamespaceURI + IRIEncoder.encode(toUniqueString(attributes));
    }

    private String toLabel(Attribute column) {
        return column.tableName() + " " + column.attributeName();
    }

    private String toLabel(List<Attribute> columns) {
        if (columns.size() == 1) return toLabel(columns.get(0));
        return String.valueOf(columns.stream().map(this::toLabel).collect(Collectors.toList()));
    }

    private String labelPattern(String name, List<Attribute> labelColumns) {
        StringBuilder result = new StringBuilder(name + " #");
        Iterator<Attribute> it = labelColumns.iterator();
        while (it.hasNext()) {
            result.append("@@").append(it.next().qualifiedName()).append("@@");
            if (it.hasNext()) {
                result.append("/");
            }
        }
        return result.toString();
    }

    /**
     * A table T is considered to be a link table if it has exactly two
     * foreign key constraints, and the constraints reference other
     * tables (not T), and the constraints cover all columns of T,
     * and there are no foreign keys from other tables pointing to this table
     * @param tableName {@link RelationName}
     * @return boolean
     */
    public boolean isLinkTable(RelationName tableName) {
        List<Join> foreignKeys = database.schemaInspector().foreignKeys(tableName, DatabaseSchemaInspector.KEYS_IMPORTED);
        if (foreignKeys.size() != 2) return false;

        List<Join> exportedKeys = database.schemaInspector().foreignKeys(tableName, DatabaseSchemaInspector.KEYS_EXPORTED);
        if (!exportedKeys.isEmpty()) return false;

        List<Attribute> columns = database.schemaInspector().listColumns(tableName);
        for (Join fk : foreignKeys) {
            if (fk.isSameTable()) return false;
            columns.removeAll(fk.attributes1());
        }
        return columns.isEmpty();
    }

    private static boolean isInForeignKey(Attribute column, List<Join> foreignKeys) {
        for (Join fk : foreignKeys) {
            if (fk.containsColumn(column)) return true;
        }
        return false;
    }

    private static URI toURI(String uri, String msg) {
        try {
            return new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(msg, e);
        }
    }
}
