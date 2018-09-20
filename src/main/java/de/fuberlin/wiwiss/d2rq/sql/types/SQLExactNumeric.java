package de.fuberlin.wiwiss.d2rq.sql.types;

import de.fuberlin.wiwiss.d2rq.sql.vendor.Vendor;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @see <a href='https://www.w3.org/2001/sw/rdb2rdf/wiki/Mapping_SQL_datatypes_to_XML_Schema_datatypes'>Mapping SQL datatypes to XML Schema datatypes</a>
 * @see <a href='https://www.w3.org/TR/r2rml/#natural-mapping'>R2RML: Natural Mapping of SQL Values</a>
 */
public class SQLExactNumeric extends DataType {
    private final String rdfType;

    public SQLExactNumeric(Vendor syntax, String name, int jdbcType, boolean unsigned) {
        super(syntax, name);
        switch (jdbcType) {
            case Types.NUMERIC:
                rdfType = "xsd:decimal";
                break;
            case Types.DECIMAL:
                rdfType = "xsd:decimal";
                break;
//		case Types.TINYINT:  rdfType = unsigned ? "xsd:unsignedByte" : "xsd:byte"; break;
//		case Types.SMALLINT: rdfType = unsigned ? "xsd:unsignedShort" : "xsd:short"; break;
//		case Types.INTEGER:  rdfType = unsigned ? "xsd:unsignedInt" : "xsd:int"; break;
//		case Types.BIGINT:   rdfType = unsigned ? "xsd:unsignedLong" : "xsd:long"; break;
            case Types.TINYINT:
                rdfType = "xsd:integer";
                break;
            case Types.SMALLINT:
                rdfType = "xsd:integer";
                break;
            case Types.INTEGER:
                rdfType = "xsd:integer";
                break;
            case Types.BIGINT:
                rdfType = "xsd:integer";
                break;
            default:
                rdfType = "xsd:decimal";
        }
    }

    @Override
    public boolean isIRISafe() {
        return true;
    }

    @Override
    public String rdfType() {
        return rdfType;
    }

    @Override
    public String value(ResultSet resultSet, int column) throws SQLException {
        String num = resultSet.getString(column);
        if (resultSet.wasNull()) return null;
        // Canonical XSD form - no trailing zeros or empty fraction
        while (num.contains(".") && (num.endsWith("0") || num.endsWith("."))) {
            num = num.substring(0, num.length() - 1);
        }
        return num;
    }

    @Override
    public String toSQLLiteral(String value) {
        try {
            return new BigDecimal(value).toString();
        } catch (NumberFormatException ex) {
            try {
                double d = Double.parseDouble(value);
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    // Valid in xsd:double, but not supported by vanilla DBs
                    return "NULL";
                }
                return Double.toString(d);
            } catch (NumberFormatException ex2) {
                // Not a number AFAICT
                LOGGER.warn("Unsupported NUMERIC format: '" + value + "'; treating as NULL");
                return "NULL";
            }
        }
    }
}