package de.fuberlin.wiwiss.d2rq.sql.types;

import de.fuberlin.wiwiss.d2rq.sql.vendor.Vendor;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class SQLDate extends DataType {
    private final static Pattern DATE_PATTERN = Pattern.compile("^\\d?\\d?\\d?\\d-\\d\\d-\\d\\d$");

    public SQLDate(Vendor syntax, String name) {
        super(syntax, name);
    }

    @Override
    public boolean isIRISafe() {
        return true;
    }

    @Override
    public String rdfType() {
        return "xsd:date";
    }

    @Override
    public String value(ResultSet resultSet, int column) throws SQLException {
        Date date = resultSet.getDate(column);
        if (date == null || resultSet.wasNull()) return null;
        String s = date.toString();
        // Need at least four digits in year; pad with 0 if necessary
        int yearDigits = s.indexOf('-');
        for (int j = 0; j < 4 - yearDigits; j++) {
            s = '0' + s;
        }
        return s;
    }

    @Override
    public String toSQLLiteral(String value) {
        if (!DATE_PATTERN.matcher(value).matches()) {
            LOGGER.warn("Unsupported DATE format: '" + value + "'; treating as NULL");
            return "NULL";
        }
        return syntax().quoteDateLiteral(value);
    }
}