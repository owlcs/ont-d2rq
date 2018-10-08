package de.fuberlin.wiwiss.d2rq.sql;

import de.fuberlin.wiwiss.d2rq.dbschema.DatabaseSchemaInspector;
import de.fuberlin.wiwiss.d2rq.helpers.MappingHelper;
import de.fuberlin.wiwiss.d2rq.map.*;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.avicomp.conf.ConnectionData;
import ru.avicomp.ontapi.jena.vocabulary.XSD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Richard Cyganiak (richard@cyganiak.de)
 * Created by @szz on 19.09.2018.
 */
@RunWith(Parameterized.class)
public class MySQLDatatypeTest {

    private final static String EX = "http://example.com/";
    private final static String DB_URI = EX + "db";
    private final static String CLASS_MAP_URI = EX + "classmap";
    private final static String PROPERTY_BRIDGE_URI = EX + "propertybridge";
    private final static String VALUE_PROPERTY = EX + "value";

    private static ConnectionData connection = ConnectionData.MYSQL;
    private static String database = MySQLDatatypeTest.class.getSimpleName().toLowerCase() + "_" + System.currentTimeMillis();

    private final Data data;

    public MySQLDatatypeTest(Data data) {
        this.data = data;
    }

    @Parameterized.Parameters(name = "{0}")
    public static List<Data> getData() {
        return Arrays.stream(Data.values()).filter(Data::enabled).collect(Collectors.toList());
    }

    @BeforeClass
    public static void prepareData() throws Exception {
        connection.createDatabase("/sql/mysql_datatypes.sql", database);
    }

    @AfterClass
    public static void clear() throws Exception {
        connection.dropDatabase(database);
    }

    @Test
    public void testDatatype() {
        Mapping mapping = createMapping(data.name());
        MappingHelper.print(mapping);

        Graph graph = mapping.getData();
        Assert.assertNotNull(graph);


        DatabaseSchemaInspector inspector = mapping.listDatabases().findFirst().orElseThrow(AssertionError::new)
                .connectedDB().schemaInspector();
        Assert.assertNotNull(inspector);

        assertMappedType(inspector, data.name(), data.getDataType());

        assertValues(graph, data.getTestData(), true);
    }

    private static void assertMappedType(DatabaseSchemaInspector inspector, String datatype, String rdfType) {
        Assert.assertEquals(rdfType, inspector.columnType(SQL.parseAttribute("T_" + datatype + ".VALUE")).rdfType());
    }

    @SuppressWarnings("SameParameterValue")
    private static void assertValues(Graph graph, String[] expectedValues, boolean searchValues) {
        ExtendedIterator<Triple> it = graph.find(Node.ANY, Node.ANY, Node.ANY);
        List<String> listedValues = new ArrayList<>();
        while (it.hasNext()) {
            listedValues.add(it.next().getObject().getLiteralLexicalForm());
        }
        Assert.assertEquals(Arrays.asList(expectedValues), listedValues);
        if (!searchValues) return;
        for (String value : expectedValues) {
            Node literal = NodeFactory.createLiteral(value);
            Assert.assertTrue("Expected literal not in graph: " + literal, graph.contains(Node.ANY, Node.ANY, literal));
        }
    }

    private static Mapping createMapping(String datatype) {
        Mapping mapping = generateMapping(datatype);
        mapping.getConfiguration().setServeVocabulary(false);
        mapping.getConfiguration().setUseAllOptimizations(true);
        mapping.connect();
        return mapping;
    }

    private static Mapping generateMapping(String datatype) {
        Mapping mapping = MappingFactory.create();
        Database database = connection.createDatabaseMapObject(mapping, DB_URI, MySQLDatatypeTest.database);
        //do not inject script to prevent database rebuilt:
        //database.setStartupSQLScript(ResourceFactory.createResource(scriptFile.toString()));
        ClassMap classMap = mapping.createClassMap(CLASS_MAP_URI)
                .setDatabase(database)
                .setURIPattern("row/@@T_" + datatype + ".ID@@");
        mapping.addClassMap(classMap);
        PropertyBridge propertyBridge = mapping.createPropertyBridge(PROPERTY_BRIDGE_URI)
                .setBelongsToClassMap(classMap)
                .addProperty(VALUE_PROPERTY)
                .setColumn("T_" + datatype + ".VALUE");
        classMap.addPropertyBridge(propertyBridge);
        return mapping;
    }

    /**
     * @see <a href='https://www.w3.org/2001/sw/rdb2rdf/wiki/Mapping_SQL_datatypes_to_XML_Schema_datatypes'>Mapping SQL datatypes to XML Schema datatypes</a>
     * @see <a href='https://www.w3.org/TR/r2rml/#natural-mapping'>R2RML: Natural Mapping of SQL Values</a>
     * @see de.fuberlin.wiwiss.d2rq.sql.types.SQLExactNumeric
     */
    enum Data {
        SERIAL(//XSD.unsignedLong,
                XSD.integer, "1", "2", "18446744073709551615"),
        BIT_4(XSD.xstring, "0", "1", "1000", "1111"),
        BIT(XSD.xstring, "0", "1"),
        TINYINT(//XSD.xbyte,
                XSD.integer, "0", "1", "-128", "127"),
        TINYINT_1(XSD.xboolean, "false", "true", "true"),
        TINYINT_UNSIGNED(//XSD.unsignedByte,
                XSD.integer, "0", "1", "255"),
        SMALLINT(//XSD.xshort,
                XSD.integer, "0", "1", "-32768", "32767"),
        SMALLINT_UNSIGNED(//XSD.unsignedShort,
                XSD.integer, "0", "1", "65535"),
        MEDIUMINT(//XSD.xint,
                XSD.integer, "0", "1", "-8388608", "8388607"),
        MEDIUMINT_UNSIGNED(//XSD.unsignedInt,
                XSD.integer, "0", "1", "16777215"),
        INTEGER(//XSD.xint,
                XSD.integer, "0", "1", "-2147483648", "2147483647"),
        INTEGER_UNSIGNED(//XSD.unsignedInt,
                XSD.integer, "0", "1", "4294967295"),
        INT(//XSD.xint,
                XSD.integer, "0", "1", "-2147483648", "2147483647"),
        INT_UNSIGNED(//XSD.unsignedInt,
                XSD.integer, "0", "1", "4294967295"),
        BIGINT(//XSD.xlong,
                XSD.integer, "0", "1", "-9223372036854775808", "9223372036854775807"),
        BIGINT_UNSIGNED(//XSD.unsignedLong,
                XSD.integer, "0", "1", "18446744073709551615"),
        DECIMAL(XSD.decimal, "0", "1", "100000000", "-100000000"),
        DECIMAL_4_2(XSD.decimal, "0", "1", "4.95", "99.99", "-99.99"),
        DEC(XSD.decimal, "0", "1", "100000000", "-100000000"),
        DEC_4_2(XSD.decimal, "0", "1", "4.95", "99.99", "-99.99"),
        // TODO: FLOAT - Fuzzy match to search for floating-point values
        FLOAT(XSD.xdouble, "0.0E0", "1.0E0", "-1.0E0", "-3.0E38", "-1.0E-38", "1.0E-38", "3.0E38"),
        // TODO: DOUBLE - Fuzzy match to search for floating-point values
        DOUBLE(XSD.xdouble, "0.0E0", "1.0E0", "-1.0E0", "-1.0E308", "-2.0E-308", "2.0E-308", "1.0E308"),
        // TODO: REAL - Fuzzy match to search for floating-point values
        REAL(XSD.xdouble, "0.0E0", "1.0E0", "-1.0E0", "-1.0E308", "-2.0E-308", "2.0E-308", "1.0E308"),
        // TODO: DOUBLE_PRECISION - Fuzzy match to search for floating-point values
        DOUBLE_PRECISION(XSD.xdouble, "0.0E0", "1.0E0", "-1.0E0", "-1.0E308", "-2.0E-308", "2.0E-308", "1.0E308"),
        CHAR_3(XSD.xstring, "", "AOU", "\u00C4\u00D6\u00DC"),
        CHAR(XSD.xstring, "", "A", "\u00C4"),
        CHARACTER(XSD.xstring, "", "A", "\u00C4"),
        NATIONAL_CHARACTER(XSD.xstring, "", "A", "\u00C4"),
        NCHAR(XSD.xstring, "", "A", "\u00C4"),
        VARCHAR(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        NATIONAL_VARCHAR(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        NVARCHAR(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        TINYTEXT(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        MEDIUMTEXT(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        TEXT(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        LONGTEXT(XSD.xstring, "", "   ", "AOU", "\u00C4\u00D6\u00DC"),
        BINARY_4(XSD.hexBinary, "00000000", "FFFFFFFF", "F001F001"),
        BINARY(XSD.hexBinary, "00", "01", "FF"),
        VARBINARY(XSD.hexBinary, "", "00", "01", "F001F001F001F001"),
        TINYBLOB(XSD.hexBinary, "", "00", "01", "F001F001F001F001"),
        MEDIUMBLOB(XSD.hexBinary, "", "00", "01", "F001F001F001F001"),
        BLOB(XSD.hexBinary, "", "00", "01", "F001F001F001F001"),
        LONGBLOB(XSD.hexBinary, "", "00", "01", "F001F001F001F001"),
        DATE(XSD.date, "1000-01-01", "2012-03-07", "9999-12-31", "1978-11-30", "1978-11-30"),
        DATETIME(XSD.dateTime, "1000-01-01T00:00:00",
                "2012-03-07T20:39:21",
                "9999-12-31T23:59:59",
                "1978-11-30T00:00:00",
                "1978-11-30T00:00:00"),
        TIMESTAMP(XSD.dateTime, "1970-01-01T00:00:01",
                "2012-03-07T20:39:21",
                "2038-01-19T03:14:07"),
        TIME(XSD.time, "00:00:00", "20:39:21", "23:59:59"),
        YEAR(XSD.date, "1901-01-01", "2012-01-01", "2155-01-01"),
        YEAR_4(XSD.date, "1901-01-01", "2012-01-01", "2155-01-01"),
        YEAR_2(XSD.date, "1970-01-01", "2012-01-01", "2069-01-01") {
            /**
             * This type is disabled since mysql 5.7.5+ does not support YEAR_2:
             * {@code Error Code: 1818. Supports only YEAR or YEAR(4) column}
             * @return {@code false}
             */
            @Override
            public boolean enabled() {
                return false;
            }
        },
        ENUM(XSD.xstring, "foo", "bar"),
        SET(XSD.xstring, "", "foo", "bar", "foo,bar", "foo,bar"),
        ;

        private static final PrefixMapping PM = PrefixMapping.Standard;
        final String[] data;
        final Resource type;

        Data(Resource r, String... d) {
            this.data = d;
            this.type = r;
        }

        public String getDataType() {
            return PM.shortForm(type.getURI());
        }

        public String[] getTestData() {
            return data;
        }

        public boolean enabled() {
            return true;
        }
    }
}
